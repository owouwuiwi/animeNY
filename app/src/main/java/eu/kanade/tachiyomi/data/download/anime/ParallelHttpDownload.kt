package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stages a complete direct video in a caller-owned temporary file. The normal media pipeline
 * can then probe and remux the result locally.
 */
internal class ParallelHttpDownload(
    client: OkHttpClient,
    private val minimumSize: Long = 16L * 1024 * 1024,
) {
    private val client = client.newBuilder()
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun stage(
        request: Request,
        destination: File,
        canStore: (Long) -> Boolean,
        onProgress: (Long, Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val remote = client.newCall(request.forRange(PROBE_RANGE)).readResponse { response ->
            probe(request, response)
        }
        if (remote == null || remote.length < minimumSize || !canStore(remote.length)) {
            return@withContext false
        }

        var complete = false
        try {
            RandomAccessFile(destination, "rw").use { it.setLength(remote.length) }
            val progress = TransferProgress(remote.length, onProgress)
            coroutineScope {
                remote.split().map { range ->
                    async {
                        client.newCall(request.forRange(range, remote.etag)).readResponse { response ->
                            val returnedRange = response.partialRange(request)
                            if (returnedRange != ContentRange(range, remote.length) || response.etag() != remote.etag) {
                                throw IOException("The video changed while downloading its ranges")
                            }
                            writeRange(destination, range, response, progress::advance)
                        }
                    }
                }.awaitAll()
            }
            complete = true
            true
        } finally {
            if (!complete) destination.delete()
        }
    }

    private fun probe(request: Request, response: Response): RemoteFile? {
        val range = response.partialRange(request) ?: return null
        if (range.bytes != PROBE_RANGE) return null
        val etag = response.etag() ?: return null
        val body = response.body.source()
        val prefix = body.readByteArray(PROBE_RANGE.length)
        if (!body.exhausted() || !prefix.hasDirectContainerSignature()) return null
        return RemoteFile(range.totalLength, etag)
    }

    private suspend fun writeRange(
        destination: File,
        range: ByteRange,
        response: Response,
        onProgress: (Int) -> Unit,
    ) {
        val context = currentCoroutineContext()
        val input = response.body.byteStream()
        val buffer = ByteArray(64 * 1024)
        RandomAccessFile(destination, "rw").use { output ->
            output.seek(range.start)
            var remaining = range.length
            while (remaining > 0L) {
                context.ensureActive()
                val chunk = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (chunk <= 0) throw IOException("The video server sent an incomplete range")
                output.write(buffer, 0, chunk)
                remaining -= chunk
                onProgress(chunk)
            }
            if (input.read() != -1) throw IOException("The video server sent an oversized range")
        }
    }

    private data class RemoteFile(val length: Long, val etag: String) {
        fun split(): List<ByteRange> {
            val partLength = length / CONNECTIONS
            return List(CONNECTIONS) { index ->
                val end = if (index == CONNECTIONS - 1) length else partLength * (index + 1)
                ByteRange(partLength * index, end - 1)
            }
        }
    }

    private class TransferProgress(
        private val total: Long,
        private val onProgress: (Long, Long) -> Unit,
    ) {
        private var written = 0L

        @Synchronized
        fun advance(bytes: Int) {
            written += bytes
            onProgress(written, total)
        }
    }

    private data class ByteRange(val start: Long, val last: Long) {
        val length: Long get() = last - start + 1
    }

    private data class ContentRange(val bytes: ByteRange, val totalLength: Long)

    private companion object {
        const val CONNECTIONS = 4
        val PROBE_RANGE = ByteRange(0, 15)
        val RANGE_PATTERN = Regex("""bytes (\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)

        fun Request.forRange(range: ByteRange, etag: String? = null): Request =
            newBuilder()
                .header("Range", "bytes=${range.start}-${range.last}")
                .header("Accept-Encoding", "identity")
                .apply { if (etag != null) header("If-Range", etag) }
                .build()

        fun Response.partialRange(original: Request): ContentRange? {
            if (code != 206 || request.url != original.url || priorResponse != null) return null
            if (networkResponse?.request?.url?.let { it != original.url } == true) return null
            val encodings = headers.values("Content-Encoding")
            if (encodings.size > 1 || encodings.any { !it.equals("identity", ignoreCase = true) }) return null
            if (body.contentType()?.type.equals("multipart", ignoreCase = true)) return null

            val header = headers.values("Content-Range").singleOrNull() ?: return null
            val match = RANGE_PATTERN.matchEntire(header) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val last = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].toLongOrNull() ?: return null
            if (last < start || total <= last) return null
            val range = ByteRange(start, last)
            val bodyLength = body.contentLength()
            if (bodyLength >= 0L && bodyLength != range.length) return null
            return ContentRange(range, total)
        }

        fun Response.etag(): String? {
            val value = headers.values("ETag").singleOrNull() ?: return null
            if (value.length < 2 || value.first() != '"' || value.last() != '"') return null
            return value.takeIf {
                (1 until it.lastIndex).all { i -> it[i] == '!' || it[i] in '#'..'~' || it[i].code in 128..255 }
            }
        }

        fun ByteArray.hasDirectContainerSignature(): Boolean {
            val mp4 = decodeToString(startIndex = 4, endIndex = 8) == "ftyp"
            val ebml = this[0] == 0x1A.toByte() && this[1] == 0x45.toByte() &&
                this[2] == 0xDF.toByte() && this[3] == 0xA3.toByte()
            return mp4 || ebml
        }
    }
}

/**
 * Cancels the OkHttp exchange during body reads as well as during await. The response and writer
 * close before the transfer's coroutine completes, so its caller can dispose of the file safely.
 */
private suspend fun <T> Call.readResponse(read: suspend (Response) -> T): T = coroutineScope {
    val completed = AtomicBoolean(false)
    val watch = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!completed.get()) this@readResponse.cancel()
        }
    }
    try {
        await().use { read(it) }
    } catch (e: IOException) {
        currentCoroutineContext().ensureActive()
        throw e
    } finally {
        completed.set(true)
        watch.cancel()
    }
}
