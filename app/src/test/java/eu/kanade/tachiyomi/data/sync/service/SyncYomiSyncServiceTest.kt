package eu.kanade.tachiyomi.data.sync.service

import com.sun.net.httpserver.HttpServer
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.net.InetSocketAddress

class SyncYomiSyncServiceTest {
    @Test
    fun `Only a committed upload returns a receipt`() = runBlocking {
        for (status in listOf(200, 412, 503)) {
            SyncServer(ProtoBuf.encodeToByteArray(Backup.serializer(), localBackup), status).use { server ->
                val preferences = syncPreferences(server.url)
                val uploaded = service(preferences).doSync(SyncData(backup = localBackup))
                assertEquals("revision-0", server.uploadPrecondition)
                assertEquals(status == 200, uploaded?.receipt != null, "HTTP $status")
                assertEquals("", preferences.appliedSyncYomiRevision().get())
            }
        }
    }

    @Test
    fun `Unreadable remote data is never replaced`() = runBlocking {
        SyncServer(byteArrayOf(0x0f)).use { server ->
            val preferences = syncPreferences(server.url)
            assertNull(service(preferences).doSync(SyncData(backup = localBackup)))
            assertNull(server.uploadPrecondition)
        }
    }

    @Test
    fun `Restart before local restore preserves remote titles and an acknowledged revision allows deletion`() =
        runBlocking {
            val remote = Backup(backupManga = listOf(BackupManga(123L, "/remote")), isLegacy = false)
            SyncServer(ProtoBuf.encodeToByteArray(Backup.serializer(), remote)).use { server ->
                val preferences = syncPreferences(server.url)
                val originalLocalSnapshot = SyncData(backup = localBackup)
                val initial = service(preferences).doSync(originalLocalSnapshot)
                assertNotNull(initial?.receipt)
                assertEquals(listOf("/local", "/remote"), server.mangaUrls())

                // The first service uploaded the merge but its local restore never ran.
                val retry = service(preferences).doSync(originalLocalSnapshot)
                assertNull(server.lastReadRevision)
                assertEquals(listOf("/local", "/remote"), server.mangaUrls())
                requireNotNull(retry?.receipt).acknowledge(preferences)

                // Once both titles were applied, the user intentionally removed the remote title.
                service(preferences).doSync(originalLocalSnapshot)
                assertEquals("revision-2", server.lastReadRevision)
                assertEquals(listOf("/local"), server.mangaUrls())
            }
        }

    private fun service(preferences: SyncPreferences) =
        SyncYomiSyncService(mockk(), Json, preferences, mockk(relaxUnitFun = true), ProtoBuf)

    private fun syncPreferences(url: String): SyncPreferences {
        var applied = ""
        val revisionPreference = mockk<Preference<String>> {
            every { get() } answers { applied }
            every { set(any()) } answers { applied = firstArg() }
        }
        return SyncPreferences(
            mockk<PreferenceStore> {
                every { getString(Preference.appStateKey("applied_syncyomi_revision"), "") } returns revisionPreference
                every { getString("sync_client_host", any()) } returns mockk { every { get() } returns url }
                every { getString("sync_client_api_key", "") } returns mockk { every { get() } returns "local-test-key" }
                every { getString(Preference.appStateKey("unique_device_id"), "") } returns mockk {
                    every { get() } returns "test-device"
                }
            },
        )
    }

    private val localBackup = Backup(backupManga = listOf(BackupManga(123L, "/local")), isLegacy = false)

    private class SyncServer(initial: ByteArray, private val uploadStatus: Int = 200) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val url get() = "http://127.0.0.1:${server.address.port}"

        @Volatile private var payload = initial
        @Volatile private var revision = 0
        @Volatile var uploadPrecondition: String? = null
            private set
        @Volatile var lastReadRevision: String? = null
            private set

        init {
            server.createContext("/api/sync/content") { exchange ->
                exchange.use {
                    val etag = "revision-$revision"
                    if (it.requestMethod == "PUT") {
                        uploadPrecondition = it.requestHeaders.getFirst("If-Match")
                        val body = it.requestBody.readBytes()
                        val status = if (uploadPrecondition == etag) uploadStatus else 412
                        if (status == 200) {
                            payload = body
                            revision++
                            it.responseHeaders.add("ETag", "revision-$revision")
                        }
                        it.sendResponseHeaders(status, -1)
                    } else {
                        lastReadRevision = it.requestHeaders.getFirst("If-None-Match")
                        if (lastReadRevision == etag) {
                            it.sendResponseHeaders(304, -1)
                        } else {
                            it.responseHeaders.add("ETag", etag)
                            it.sendResponseHeaders(200, payload.size.toLong())
                            it.responseBody.write(payload)
                        }
                    }
                }
            }
            server.start()
        }

        fun mangaUrls() = ProtoBuf.decodeFromByteArray(Backup.serializer(), payload).backupManga.map { it.url }

        override fun close() = server.stop(0)
    }
}
