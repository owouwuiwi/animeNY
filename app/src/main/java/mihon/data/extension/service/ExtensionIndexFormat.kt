package mihon.data.extension.service

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
        '<'.code -> throw IllegalArgumentException("The extension repository returned a web page. Use a direct index URL.")
        else -> ExtensionIndexFormat.PROTOBUF
    }
}
