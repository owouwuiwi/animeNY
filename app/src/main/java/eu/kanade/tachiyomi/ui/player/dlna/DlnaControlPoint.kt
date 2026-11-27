package eu.kanade.tachiyomi.ui.player.dlna

import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.StringReader
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

internal data class DlnaRenderer(
    val id: String,
    val name: String,
    val controlUrl: HttpUrl,
    val serviceType: String,
)

internal class DlnaSession(
    val rendererName: String,
    private val stopPlayback: suspend () -> Unit,
) {
    suspend fun stop() = stopPlayback()
}

internal class DlnaControlPoint(
    private val discovery: DlnaDiscovery,
    client: OkHttpClient,
) {
    private val client = client.newBuilder()
        .cache(null)
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(2, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun renderers(): List<DlnaRenderer> = withContext(Dispatchers.IO) {
        discovery.search()
            .map { item ->
                async {
                    try {
                        val xml = requestXml(Request.Builder().url(item.location).build())
                        parseDescription(item, xml)
                    } catch (_: IOException) {
                        currentCoroutineContext().ensureActive()
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .sortedBy { it.name.lowercase() }
    }

    suspend fun start(renderer: DlnaRenderer, media: DlnaMedia): DlnaSession {
        invoke(
            renderer,
            action = "SetAVTransportURI",
            arguments = listOf("CurrentURI" to media.url.toString(), "CurrentURIMetaData" to media.didL()),
        )
        invoke(renderer, action = "Play", arguments = listOf("Speed" to "1"))
        return DlnaSession(renderer.name) { invoke(renderer, action = "Stop") }
    }

    private suspend fun invoke(
        renderer: DlnaRenderer,
        action: String,
        arguments: List<Pair<String, String>> = emptyList(),
    ): Unit = withContext(Dispatchers.IO) {
        val service = renderer.serviceType
        val xml = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<s:Envelope xmlns:s="$SOAP" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""")
            append("""<s:Body><u:$action xmlns:u="$service"><InstanceID>0</InstanceID>""")
            arguments.forEach { (key, value) -> append("<$key>${value.escapeXml()}</$key>") }
            append("</u:$action></s:Body></s:Envelope>")
        }
        val request = Request.Builder()
            .url(renderer.controlUrl)
            .header("SOAPAction", "\"$service#$action\"")
            .post(xml.toRequestBody(XML_MEDIA_TYPE))
            .build()

        val envelope = parseXml(requestXml(request))
        val body = envelope?.takeIf { it.localName == "Envelope" && it.namespaceURI == SOAP }
            ?.child("Body", SOAP)
        if (body?.child("${action}Response", service) == null || body.child("Fault", SOAP) != null) {
            throw IOException("The renderer rejected the AVTransport action")
        }
    }

    private suspend fun requestXml(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful || response.request.url != request.url || response.priorResponse != null) {
                throw IOException("The renderer did not accept the request")
            }
            val body = response.body
            if (body.contentLength() > MAX_XML_BYTES) throw IOException("The renderer response is too large")
            val source = body.source()
            if (source.request(MAX_XML_BYTES + 1)) throw IOException("The renderer response is too large")
            source.readUtf8()
        }
    }

    companion object {
        private const val MAX_XML_BYTES = 128L * 1024
        private const val DEVICE = "urn:schemas-upnp-org:device-1-0"
        private const val SOAP = "http://schemas.xmlsoap.org/soap/envelope/"
        private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

        internal fun parseDescription(announcement: DlnaAnnouncement, xml: String): DlnaRenderer? {
            val root = parseXml(xml)?.takeIf { it.localName == "root" && it.namespaceURI == DEVICE } ?: return null
            val location = announcement.location
            val base = root.scalar("URLBase", DEVICE)?.let {
                it.toHttpUrlOrNull()?.takeIf { url -> url.sameEndpoint(location) } ?: return null
            } ?: location
            val devices = root.getElementsByTagNameNS(DEVICE, "device")
            for (index in 0 until devices.length) {
                val device = devices.item(index) as? Element ?: continue
                if (device.scalar("deviceType", DEVICE)?.isRendererType() != true) continue
                val id = device.scalar("UDN", DEVICE) ?: continue
                if (!id.equals(announcement.deviceId, ignoreCase = true)) continue
                val name = device.scalar("friendlyName", DEVICE)?.take(100) ?: location.host
                val serviceList = device.child("serviceList", DEVICE) ?: continue
                val services = serviceList.getElementsByTagNameNS(DEVICE, "service")
                for (serviceIndex in 0 until services.length) {
                    val service = services.item(serviceIndex) as? Element ?: continue
                    val type = service.scalar("serviceType", DEVICE)?.takeIf(String::isTransportType) ?: continue
                    val url = service.scalar("controlURL", DEVICE)?.let(base::resolve) ?: continue
                    if (url.sameEndpoint(location)) return DlnaRenderer(id, name, url, type)
                }
            }
            return null
        }

        private fun HttpUrl.sameEndpoint(original: HttpUrl): Boolean =
            isPlainEndpoint() && scheme == original.scheme && host == original.host && port == original.port

        private fun parseXml(xml: String): Element? {
            if ("<!DOCTYPE" in xml) return null
            return try {
                val builder = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isValidating = false
                    isExpandEntityReferences = false
                }.newDocumentBuilder()
                builder.setEntityResolver { _, _ -> throw SAXException("External XML references are unsupported") }
                builder.setErrorHandler(DefaultHandler())
                builder.parse(InputSource(StringReader(xml.removePrefix("\uFEFF")))).documentElement
            } catch (_: SAXException) {
                null
            } catch (_: IOException) {
                null
            } catch (_: ParserConfigurationException) {
                null
            }
        }

        private fun Element.child(name: String, namespace: String): Element? {
            val children = childNodes
            return (0 until children.length)
                .asSequence()
                .mapNotNull { children.item(it) as? Element }
                .firstOrNull { it.localName == name && it.namespaceURI == namespace }
        }

        private fun Element.scalar(name: String, namespace: String): String? {
            val element = child(name, namespace) ?: return null
            val children = element.childNodes
            if ((0 until children.length).any { children.item(it) is Element }) return null
            return element.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }

        private fun DlnaMedia.didL(): String = buildString {
            append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"""")
            append(""" xmlns:dc="http://purl.org/dc/elements/1.1/"""")
            append(""" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
            append("""<item id="0" parentID="-1" restricted="1"><dc:title>${title.escapeXml()}</dc:title>""")
            append("<upnp:class>object.item.videoItem</upnp:class>")
            append("""<res protocolInfo="http-get:*:$mimeType:*">${url.toString().escapeXml()}</res>""")
            append("</item></DIDL-Lite>")
        }

        private fun String.escapeXml(): String = buildString(length) {
            this@escapeXml.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '\'' -> append("&apos;")
                    '"' -> append("&quot;")
                    else -> if (character in ' '..'\uFFFD' || character in "\t\r\n") append(character) else Unit
                }
            }
        }
    }
}
