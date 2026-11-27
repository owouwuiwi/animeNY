package eu.kanade.tachiyomi.data.sync.service

import com.sun.net.httpserver.HttpServer
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class SyncYomiSyncServiceTest {
    @Test
    fun `Only a committed upload is reported as successful`() = runBlocking {
        for (uploadStatus in listOf(200, 412, 503)) {
            val result = syncWithServer(uploadStatus)
            val committed = uploadStatus == 200
            assertEquals("initial", result.uploadPrecondition)
            assertEquals(if (committed) localBackup else null, result.backup, "HTTP $uploadStatus")
            assertEquals(if (committed) "updated" else "initial", result.etag, "HTTP $uploadStatus")
        }
    }

    @Test
    fun `Unreadable remote data is never replaced`() = runBlocking {
        val result = syncWithServer(200, byteArrayOf(0x0f))
        assertEquals(null, result.backup)
        assertEquals(null, result.uploadPrecondition)
        assertEquals("initial", result.etag)
    }

    private suspend fun syncWithServer(uploadStatus: Int, remoteBody: ByteArray? = null): SyncAttempt {
        val uploadPrecondition = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/sync/content") { exchange ->
            exchange.use {
                if (it.requestMethod == "PUT") {
                    uploadPrecondition.set(it.requestHeaders.getFirst("If-Match") ?: "absent")
                    it.requestBody.readBytes()
                    if (uploadStatus == 200) it.responseHeaders.add("ETag", "updated")
                    it.sendResponseHeaders(uploadStatus, -1)
                } else if (remoteBody != null) {
                    it.responseHeaders.add("ETag", "initial")
                    it.sendResponseHeaders(200, remoteBody.size.toLong())
                    it.responseBody.write(remoteBody)
                } else {
                    it.sendResponseHeaders(304, -1)
                }
            }
        }
        server.start()
        try {
            var etag = "initial"
            val etagPreference = mockk<Preference<String>> {
                every { get() } answers { etag }
                every { set(any()) } answers { etag = firstArg() }
            }
            val store = mockk<PreferenceStore> {
                every { getString("sync_etag", "") } returns etagPreference
                every { getString("sync_client_host", any()) } returns mockk {
                    every { get() } returns "http://127.0.0.1:${server.address.port}"
                }
                every { getString("sync_client_api_key", "") } returns mockk {
                    every { get() } returns "local-test-key"
                }
            }
            val service = SyncYomiSyncService(mockk(), Json, SyncPreferences(store), mockk(relaxUnitFun = true), ProtoBuf)
            val backup = service.doSync(SyncData(backup = localBackup))
            return SyncAttempt(backup, etag, uploadPrecondition.get())
        } finally {
            server.stop(0)
        }
    }

    private data class SyncAttempt(val backup: Backup?, val etag: String, val uploadPrecondition: String?)

    private val localBackup = Backup(isLegacy = false)
}
