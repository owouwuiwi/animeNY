package mihon.data.extension.service

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.ModernNetworkExtensionStore
import mihon.domain.extension.model.ExtensionStore
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okio.BufferedSource
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.gzip

internal enum class ExtensionIndexFormat {
    JSON_ARRAY,
    JSON_OBJECT,
    PROTOBUF,
}

private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()).toByteString()

internal inline fun <T> Response.readExtensionIndex(read: (BufferedSource, ExtensionIndexFormat) -> T): T = use {
    val contentType = body.contentType()
    require(contentType?.subtype != "html") { "The extension repository returned a web page. Use a direct index URL." }
    val jsonHint = request.url.encodedPath.endsWith(".json", ignoreCase = true) ||
        contentType?.subtype?.let { it == "json" || it.endsWith("+json") } == true
    val source = body.source().let {
        if (it.request(2) && it.buffer[0] == 0x1F.toByte() && it.buffer[1] == 0x8B.toByte()) {
            it.gzip().buffer()
        } else {
            it
        }
    }
    source.use { read(it, it.extensionIndexFormat(jsonHint)) }
}

internal inline fun <reified T> BufferedSource.decodeExtensionIndexObject(
    format: ExtensionIndexFormat,
    json: Json,
    protoBuf: ProtoBuf,
): T = try {
    when (format) {
        ExtensionIndexFormat.JSON_OBJECT -> json.decodeFromBufferedSource<T>(this)
        ExtensionIndexFormat.PROTOBUF -> protoBuf.decodeFromByteArray<T>(readByteArray())
        ExtensionIndexFormat.JSON_ARRAY -> throw IllegalArgumentException("The extension index must be an object.")
    }
} catch (error: SerializationException) {
    throw IllegalArgumentException(
        "The extension repository returned an unsupported index. Use the repository's direct index URL.",
        error,
    )
}

internal fun HttpUrl.resolveExtensionListUrl(url: String): String =
    requireNotNull(resolve(url)) { "The extension repository contains an invalid extension list URL." }.toString()

internal suspend inline fun <reified Index : ModernNetworkExtensionStore<Entries>, reified Entries>
    ExtensionStore.readModernExtensionList(
        json: Json,
        protoBuf: ProtoBuf,
        fetch: (String) -> Response,
    ): Entries? {
    val listUrl = extensionListUrl?.let(indexUrl.toHttpUrl()::resolveExtensionListUrl) ?: run {
        val response = fetch(indexUrl)
        val responseUrl = response.request.url
        val index = response.readExtensionIndex { source, format ->
            source.decodeExtensionIndexObject<Index>(format, json, protoBuf)
        }
        responseUrl.resolveExtensionListUrl(index.extensionListUrl ?: return index.extensionList)
    }

    return fetch(listUrl).readExtensionIndex { source, format ->
        source.decodeExtensionIndexObject<Entries>(format, json, protoBuf)
    }
}

internal fun BufferedSource.extensionIndexFormat(jsonHint: Boolean): ExtensionIndexFormat {
    if (rangeEquals(0, utf8Bom)) skip(utf8Bom.size.toLong())
    val marker = peek().use { preview ->
        require(preview.request(1)) { "The extension index is empty." }
        var next = preview.readByte().toInt()
        // 0x0A is also the protobuf tag for the first index field. Only skip JSON
        // whitespace when the response advertised a JSON body or file name.
        if (jsonHint) {
            while (next == ' '.code || next == '\t'.code || next == '\r'.code || next == '\n'.code) {
                require(preview.request(1)) { "The extension index is empty." }
                next = preview.readByte().toInt()
            }
        }
        next
    }
    return when (marker) {
        '['.code -> ExtensionIndexFormat.JSON_ARRAY

        '{'.code -> ExtensionIndexFormat.JSON_OBJECT

        '<'.code -> throw IllegalArgumentException(
            "The extension repository returned a web page. Use a direct index URL.",
        )

        else -> ExtensionIndexFormat.PROTOBUF
    }
}
