package eu.kanade.tachiyomi.data.backup.restore

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.Futures
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.cloudSyncLock
import eu.kanade.tachiyomi.data.sync.service.SyncReceipt
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import tachiyomi.core.common.preference.Preference

class CloudRestoreTest {
    @Test
    fun `Only a successful local restore acknowledges the uploaded revision`() = runBlocking {
        val revision = mockk<Preference<String>>(relaxed = true)
        val timestamp = mockk<Preference<Long>>(relaxed = true)
        val preferences = mockk<SyncPreferences> {
            every { appliedDriveRevision() } returns revision
            every { lastSyncTimestamp() } returns timestamp
        }
        val receipt = SyncReceipt(SyncReceipt.Provider.DRIVE, "new-revision")

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
            revision.set(receipt.revision)
            timestamp.set(any())
        }
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
        val receipt = SyncReceipt(SyncReceipt.Provider.SYNCYOMI, "uploaded-revision")

        assertFalse(BackupRestoreJob.startSync(manager, uri, RestoreOptions(), receipt))
        accepted = true
        assertTrue(BackupRestoreJob.startSync(manager, uri, RestoreOptions(), receipt))
        assertTrue(BackupRestoreJob.hasUnfinished(manager))
        assertEquals(receipt.revision, request.captured.workSpec.input.getString("sync_revision"))
    }
}
