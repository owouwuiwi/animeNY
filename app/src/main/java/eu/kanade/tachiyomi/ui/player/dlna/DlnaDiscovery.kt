package eu.kanade.tachiyomi.ui.player.dlna

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal data class DlnaAnnouncement(val deviceId: String, val location: HttpUrl)

internal class DlnaDiscovery(
    private val openSocket: () -> DatagramSocket,
    private val isLocalDevice: (Inet4Address) -> Boolean,
    private val destination: InetSocketAddress = InetSocketAddress("239.255.255.250", 1900),
    private val searchDuration: Duration = 3.seconds,
) {
    suspend fun search(): List<DlnaAnnouncement> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DlnaAnnouncement>()
        val deadline = TimeSource.Monotonic.markNow() + searchDuration
        openSocket().use { socket ->
            val search = SEARCH.encodeToByteArray()
            socket.send(DatagramPacket(search, search.size, destination))
            val responseBytes = ByteArray(4097)
            val packet = DatagramPacket(responseBytes, responseBytes.size)
            socket.soTimeout = 200
            while (!deadline.hasPassedNow() && found.size < 16) {
                ensureActive()
                packet.length = responseBytes.size
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val address = packet.address as? Inet4Address ?: continue
                if (packet.length >= responseBytes.size || !isLocalDevice(address)) continue
                val reply = responseBytes.decodeToString(0, packet.length)
                parseReply(reply, address)?.let { found.putIfAbsent(it.deviceId.lowercase(), it) }
            }
        }
        found.values.toList()
    }

    companion object {
        private val STATUS = Regex("""HTTP/1\.[01] 200(?: .*)?""", RegexOption.IGNORE_CASE)
        private val SEARCH = listOf(
            "M-SEARCH * HTTP/1.1",
            "HOST: 239.255.255.250:1900",
            "MAN: \"ssdp:discover\"",
            "MX: 2",
            "ST: ${DLNA_RENDERER_PREFIX}1",
            "USER-AGENT: Android UPnP/1.1 animeNY/1.0",
            "",
            "",
        ).joinToString("\r\n")

        internal fun parseReply(reply: String, address: Inet4Address): DlnaAnnouncement? {
            val lines = reply.lineSequence().iterator()
            if (!lines.hasNext() || !STATUS.matches(lines.next().trimEnd())) return null
            val fields = lines.asSequence()
                .takeWhile { it.isNotBlank() }
                .filter { ':' in it }
                .groupBy({ it.substringBefore(':').trim().lowercase() }, { it.substringAfter(':').trim() })

            val target = fields["st"]?.singleOrNull() ?: return null
            if (!target.isRendererType()) return null
            val id = fields["usn"]?.singleOrNull()?.substringBefore("::") ?: return null
            if (id.length !in 6..128 || !id.startsWith("uuid:", ignoreCase = true) || id.any { it.code !in 33..126 }) {
                return null
            }
            val location = fields["location"]?.singleOrNull()?.toHttpUrlOrNull() ?: return null
            if (location.host != address.hostAddress || !location.isPlainEndpoint()) return null
            return DlnaAnnouncement(id, location)
        }
    }
}

internal const val DLNA_RENDERER_PREFIX = "urn:schemas-upnp-org:device:MediaRenderer:"
internal const val DLNA_TRANSPORT_PREFIX = "urn:schemas-upnp-org:service:AVTransport:"

internal fun String.isRendererType(): Boolean = isVersionOf(DLNA_RENDERER_PREFIX)

internal fun String.isTransportType(): Boolean = isVersionOf(DLNA_TRANSPORT_PREFIX)

private fun String.isVersionOf(prefix: String): Boolean =
    startsWith(prefix) && removePrefix(prefix).let { version ->
        version.all { it in '0'..'9' } && version.toIntOrNull()?.let { it > 0 } == true
    }

internal fun HttpUrl.isPlainEndpoint(): Boolean = username.isEmpty() && password.isEmpty() && fragment == null
