package eu.kanade.tachiyomi.data.download.anime

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ParallelHttpDownloadTest {
    @TempDir
    lateinit var destinationDir: File

    @Test
    fun `four conditional ranges are requested concurrently and assemble the original bytes`() = runTest {
        val video = syntheticVideo()
        val concurrentRanges = CountDownLatch(4)
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val client = client { request ->
            requests += request
            if (request.header("If-Range") != null) {
                concurrentRanges.countDown()
                if (!concurrentRanges.await(5, TimeUnit.SECONDS)) throw IOException("The requests were serial")
            }
            partial(request, video)
        }
        val progress = mutableListOf<Pair<Long, Long>>()
        val output = File(destinationDir, "movie.tmp")

        assertTrue(stage(client, output) { written, total -> progress += written to total })
        assertArrayEquals(video, output.readBytes())
        assertEquals(5, requests.size)
        assertEquals("bytes=0-15", requests.first().header("Range"))
        assertEquals(
            listOf("bytes=0-16383", "bytes=16384-32767", "bytes=32768-49151", "bytes=49152-65538"),
            requests.drop(1).map { it.header("Range") }.sortedBy { it?.substringAfter('=')?.substringBefore('-') },
        )
        assertTrue(requests.all { it.header("Accept-Encoding") == "identity" && it.header("Referer") == REFERER })
        assertTrue(requests.drop(1).all { it.header("If-Range") == ETAG })
        assertTrue(progress.zipWithNext().all { (first, second) -> first.first < second.first })
        assertEquals(video.size.toLong() to video.size.toLong(), progress.last())
    }

    @Test
    fun `a server that ignores the range probe leaves the ordinary path eligible`() = runTest {
        val output = File(destinationDir, "not-staged.tmp")
        val video = syntheticVideo()
        val client = client { request ->
            response(request, 200, video.toResponseBody(MEDIA_TYPE)).build()
        }

        assertFalse(stage(client, output))
        assertFalse(output.exists())
    }

    @Test
    fun `unsupported probe headers and a manifest body leave no staged file`() = runTest {
        val video = syntheticVideo()
        val manifest = video.copyOf().apply { "#EXTM3U".encodeToByteArray().copyInto(this) }
        val replies: List<(Request) -> Response> = listOf(
            { request -> partial(request, video).newBuilder().header("ETag", "W/$ETAG").build() },
            { request -> partial(request, video).newBuilder().header("Content-Encoding", "gzip").build() },
            { request -> partial(request, video).newBuilder().header("Content-Range", "bytes 0-15/*").build() },
            { request -> partial(request, manifest) },
        )
        replies.forEachIndexed { index, reply ->
            val output = File(destinationDir, "rejected-$index.tmp")

            assertFalse(stage(client(reply), output))
            assertFalse(output.exists())
        }
    }

    @Test
    fun `a rejected disk reservation performs only the probe`() = runTest {
        val output = File(destinationDir, "reservation.tmp")
        val requests = mutableListOf<Request>()
        val video = syntheticVideo()
        val client = client {
            requests += it
            partial(it, video)
        }

        assertFalse(downloader(client).stage(request(), output, canStore = { false }) { _, _ -> })
        assertEquals(1, requests.size)
        assertFalse(output.exists())
    }

    @Test
    fun `a full body or a different etag during part requests discards all partial data`() = runTest {
        val video = syntheticVideo()
        val changes: List<(Request) -> Response> = listOf(
            { request -> response(request, 200, video.toResponseBody(MEDIA_TYPE)).build() },
            { request -> partial(request, video).newBuilder().header("ETag", "\"changed\"").build() },
        )
        changes.forEachIndexed { index, change ->
            val output = File(destinationDir, "changed-$index.tmp")
            val client = client { request ->
                if (request.header("Range") == "bytes=16384-32767") change(request) else partial(request, video)
            }

            assertFailedTransfer(client, output)
        }
    }

    @Test
    fun `changed range metadata or destination cannot be assembled`() = runTest {
        val video = syntheticVideo()
        val changes: List<(Response.Builder) -> Response.Builder> = listOf(
            { it.header("Content-Range", "bytes 0-16383/${video.size}") },
            { it.header("Content-Range", "bytes 16384-32767/${video.size + 1}") },
            { it.request(Request.Builder().url("https://redirect.invalid/episode").build()) },
        )
        changes.forEachIndexed { index, change ->
            val output = File(destinationDir, "bad-range-$index.tmp")
            val client = client { request ->
                val response = partial(request, video)
                if (request.header("Range") == "bytes=16384-32767") change(response.newBuilder()).build() else response
            }

            assertFailedTransfer(client, output)
        }
    }

    @Test
    fun `either too little or too much body data discards the staged file`() = runTest {
        val video = syntheticVideo()
        listOf(1_000, 17_000).forEach { byteCount ->
            val output = File(destinationDir, "body-$byteCount.tmp")
            val client = client { request ->
                val response = partial(request, video)
                if (request.header("Range") != "bytes=16384-32767") {
                    response
                } else {
                    response.body.close()
                    val body = object : ResponseBody() {
                        private val buffer = Buffer().write(ByteArray(byteCount))
                        override fun contentType(): MediaType = MEDIA_TYPE
                        override fun contentLength(): Long = -1L
                        override fun source(): BufferedSource = buffer
                    }
                    response.newBuilder().body(body).build()
                }
            }

            assertFailedTransfer(client, output)
        }
    }

    private suspend fun assertFailedTransfer(client: OkHttpClient, output: File) {
        try {
            stage(client, output)
            throw AssertionError("Invalid parts were accepted")
        } catch (_: IOException) {
            assertFalse(output.exists())
        }
    }

    private suspend fun stage(
        client: OkHttpClient,
        destination: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Boolean = downloader(client).stage(request(), destination, canStore = { true }, onProgress = onProgress)

    private fun downloader(client: OkHttpClient) = ParallelHttpDownload(client, minimumSize = 32_768)

    private fun request() = Request.Builder().url("https://unit.invalid/episode").header("Referer", REFERER).build()

    private fun client(reply: (Request) -> Response) =
        OkHttpClient.Builder().addInterceptor(Interceptor { reply(it.request()) }).build()

    private fun response(request: Request, status: Int, body: ResponseBody) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("").body(body)

    private fun partial(request: Request, bytes: ByteArray): Response {
        val range = checkNotNull(request.header("Range")).substringAfter("bytes=").split('-')
        val start = range[0].toInt()
        val last = range[1].toInt()
        val body = bytes.copyOfRange(start, last + 1).toResponseBody(MEDIA_TYPE)
        return response(request, 206, body)
            .header("Content-Range", "bytes $start-$last/${bytes.size}")
            .header("ETag", ETAG)
            .build()
    }

    private fun syntheticVideo() = ByteArray(65_539) { (it % 251).toByte() }.apply {
        "ftyp".encodeToByteArray().copyInto(this, destinationOffset = 4)
    }

    private companion object {
        const val REFERER = "https://unit.invalid/"
        const val ETAG = "\"video-v1\""
        val MEDIA_TYPE = "video/mp4".toMediaType()
    }
}
