package eu.kanade.tachiyomi.data.backup.restore

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.Futures
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.cloudSyncLock
import eu.kanade.tachiyomi.data.sync.service.SyncReceipt
import eu.kanade.tachiyomi.data.sync.service.syncAccountScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import java.nio.file.Path

class CloudRestoreTest {
    @Test
    fun `Only a successful local restore acknowledges the uploaded revision`() = runBlocking {
        val revision = mockk<Preference<String>>(relaxed = true)
        val timestamp = mockk<Preference<Long>>(relaxed = true)
        val preferences = mockk<SyncPreferences> {
            every { appliedDriveRevision() } returns revision
            every { lastSyncTimestamp() } returns timestamp
            every { syncService().get() } returns SyncManager.SyncService.GOOGLE_DRIVE.value
            every { googleDriveRefreshToken().get() } returns "test-refresh-token"
        }
        val receipt = SyncReceipt(SyncReceipt.Provider.DRIVE, syncAccountScope("test-refresh-token"), "new-revision")

        assertFalse(finishCloudRestore(false, preferences, receipt))
        cloudSyncLock.withLock {
            launch(start = CoroutineStart.UNDISPATCHED) {
                finishCloudRestore(true, preferences, receipt)
            }.cancelAndJoin()
        }
        verify(exactly = 0) {
            revision.set(any())
            timestamp.set(any())
        }

        assertTrue(finishCloudRestore(true, preferences, receipt))
        verify(exactly = 1) {
            revision.set("${receipt.accountScope}:${receipt.revision}")
            timestamp.set(any())
        }
    }

    @Test
    fun `Stale queued account skips restore and late account switch prevents acknowledgement`(
        @TempDir directory: Path,
    ) = runBlocking {
        var credential = "old-refresh-token"
        val preferences = mockk<SyncPreferences> {
            every { syncService().get() } returns SyncManager.SyncService.GOOGLE_DRIVE.value
            every { googleDriveRefreshToken().get() } answers { credential }
        }
        val receipt = SyncReceipt(SyncReceipt.Provider.DRIVE, syncAccountScope(credential), "new-revision")
        val stagedFile = directory.resolve("test.proto").toFile()
        repeat(3) { point ->
            stagedFile.writeText("backup")
            credential = if (point == 0) "new-account" else "old-refresh-token"
            val restored = restoreStagedCloudBackup(preferences, receipt, stagedFile, { false }, prepare = {
                if (point == 1) credential = "new-account"
            }) {
                assertEquals(2, point, "stale account must not enter restore")
                credential = "new-account"
                true
            }
            assertFalse(restored)
            assertFalse(stagedFile.exists())
        }
        verify(exactly = 0) {
            preferences.appliedDriveRevision()
            preferences.lastSyncTimestamp()
        }
    }

    @Test
    fun `System stop preserves the payload for retry and terminal results clean it`(
        @TempDir directory: Path,
    ) = runBlocking {
        val revision = mockk<Preference<String>>(relaxed = true)
        val preferences = mockk<SyncPreferences> {
            every { appliedDriveRevision() } returns revision
            every { lastSyncTimestamp() } returns mockk(relaxed = true)
            every { syncService().get() } returns SyncManager.SyncService.GOOGLE_DRIVE.value
            every { googleDriveRefreshToken().get() } returns "test-refresh-token"
        }
        val receipt = SyncReceipt(SyncReceipt.Provider.DRIVE, syncAccountScope("test-refresh-token"), "new-revision")
        val stagedFile = directory.resolve("test.proto").toFile().apply { writeText("backup") }

        val interrupted = runCatching {
            restoreStagedCloudBackup(preferences, receipt, stagedFile, { false }) {
                throw CancellationException("system preemption")
            }
        }
        assertTrue(interrupted.exceptionOrNull() is CancellationException)
        assertEquals("backup", stagedFile.readText())
        verify(exactly = 0) { revision.set(any()) }

        assertTrue(
            restoreStagedCloudBackup(preferences, receipt, stagedFile, { false }) {
                assertEquals("backup", stagedFile.readText())
                true
            },
        )
        assertFalse(stagedFile.exists())
        verify(exactly = 1) { revision.set("${receipt.accountScope}:${receipt.revision}") }

        stagedFile.writeText("backup")
        val cancelled = runCatching {
            restoreStagedCloudBackup(preferences, receipt, stagedFile, { true }) {
                throw CancellationException("explicit app cancel")
            }
        }
        assertTrue(cancelled.exceptionOrNull() is CancellationException)
        assertFalse(stagedFile.exists())
    }

    @Test
    fun `Successful KEEP operation does not report a dropped restore as queued`() = runBlocking {
        val request = slot<OneTimeWorkRequest>()
        val operation = mockk<Operation> {
            every { result } returns Futures.immediateFuture(mockk<Operation.State.SUCCESS>())
        }
        val uri = mockk<Uri>()
        val queuedWork = mockk<WorkInfo> { every { state } returns WorkInfo.State.ENQUEUED }
        var accepted = false
        val manager = mockk<WorkManager> {
            every { enqueueUniqueWork("BackupRestore", ExistingWorkPolicy.KEEP, capture(request)) } returns operation
            every { getWorkInfoByIdFlow(any()) } answers {
                assertEquals(request.captured.id, firstArg())
                flowOf(queuedWork.takeIf { accepted })
            }
            every { getWorkInfosForUniqueWorkFlow("BackupRestore") } answers {
                flowOf(if (accepted) listOf(queuedWork) else emptyList())
            }
        }
        val receipt = SyncReceipt(
            SyncReceipt.Provider.SYNCYOMI,
            syncAccountScope("https://example.test/api/sync/content", "test-api-key"),
            "uploaded-revision",
        )

        assertFalse(BackupRestoreJob.startSync(manager, uri, RestoreOptions(), receipt))
        accepted = true
        assertTrue(BackupRestoreJob.startSync(manager, uri, RestoreOptions(), receipt))
        assertTrue(BackupRestoreJob.hasUnfinished(manager))
        assertEquals(receipt.revision, request.captured.workSpec.input.getString("sync_revision"))
        assertEquals(receipt.accountScope, request.captured.workSpec.input.getString("sync_account_scope"))
    }
}
