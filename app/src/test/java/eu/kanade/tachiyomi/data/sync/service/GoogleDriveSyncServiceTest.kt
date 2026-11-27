package eu.kanade.tachiyomi.data.sync.service

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

class GoogleDriveSyncServiceTest {
    @Test
    fun `Lookup keeps both backup formats and paginates in newest first order`() {
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/drive/v3/files") { exchange ->
            exchange.use {
                val query = URLDecoder.decode(it.requestURI.rawQuery, StandardCharsets.UTF_8)
                requests += query
                val json = if ("pageToken=next" in query) {
                    """{"files":[{"id":"older"}]}"""
                } else {
                    """{"nextPageToken":"next","files":[{"id":"latest"}]}"""
                }.encodeToByteArray()
                it.responseHeaders.add("Content-Type", "application/json")
                it.sendResponseHeaders(200, json.size.toLong())
                it.responseBody.write(json)
            }
        }
        server.start()
        try {
            val files = drive(server).listSyncBackups("QA's backup")
            assertEquals(listOf("latest", "older"), files.map { it.id })
            assertEquals(2, requests.size)
            assertTrue(requests.all { "spaces=appDataFolder" in it && "orderBy=modifiedTime desc" in it })
            assertTrue(requests.all { "application/octet-stream" in it && "application/x-gzip" in it })
            assertTrue(requests.all { "QA\\'s backup" in it })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `Failed lookup propagates instead of implying no backup exists`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/drive/v3/files") { exchange ->
            exchange.use { it.sendResponseHeaders(503, -1) }
        }
        server.start()
        try {
            assertThrows<IOException> { drive(server).listSyncBackups("QA backup") }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `Upload content is readable repeatedly and cleaned up after an early failure`(@TempDir directory: Path) {
        val bytes = ByteArray(32_768) { it.toByte() }
        val cache = directory.toFile()
        for (rejectUpload in listOf(false, true)) {
            try {
                withDriveBackupContent(cache, bytes) { content ->
                    assertEquals("application/x-gzip", content.type)
                    assertTrue(content.retrySupported())
                    if (rejectUpload) throw IOException("rejected before reading")
                    repeat(2) {
                        val data = content.inputStream.use { stream ->
                            GZIPInputStream(stream).use { gzip -> gzip.readBytes() }
                        }
                        assertArrayEquals(bytes, data)
                    }
                }
                assertEquals(false, rejectUpload)
            } catch (e: IOException) {
                assertEquals(true, rejectUpload)
                assertEquals("rejected before reading", e.message)
            }
            assertTrue(cache.list().orEmpty().isEmpty())
        }
    }

    private fun drive(server: HttpServer): Drive =
        Drive.Builder(NetHttpTransport(), JacksonFactory.getDefaultInstance(), null)
            .setApplicationName("Backup test")
            .setRootUrl("http://127.0.0.1:${server.address.port}/")
            .build()
}
