package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dev.zacsweers.metro.Inject
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.cloudSyncLock
import eu.kanade.tachiyomi.data.sync.service.SyncReceipt
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.File

class BackupRestoreJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var backupRestorerFactory: BackupRestorer.Factory

    @Inject private lateinit var notifier: BackupNotifier

    override suspend fun doWork(): Result {
        graph.inject(this)

        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { RestoreOptions.fromBooleanArray(it) }

        if (uri == null || options == null) {
            return Result.failure()
        }

        val isSync = inputData.getBoolean(SYNC_KEY, false)

        return try {
            try {
                setForeground(getForegroundInfo())
            } catch (e: IllegalStateException) {
                logcat(LogPriority.ERROR, e) { "Not allowed to run on foreground service" }
            }
            val complete = backupRestorerFactory.create(notifier = notifier, isSync = isSync).restore(uri, options)
            if (isSync) {
                val receipt = inputData.getString(SYNC_REVISION_KEY)?.let { revision ->
                    inputData.getString(SYNC_PROVIDER_KEY)?.let {
                        SyncReceipt(SyncReceipt.Provider.valueOf(it), revision)
                    }
                }
                if (!finishCloudRestore(complete, graph.syncPreferences, receipt)) return Result.failure()
            }
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                if (isSync) throw e
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError(e.message)
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
            if (isSync && uri.scheme == "file") {
                uri.path?.let(::File)
                    ?.takeIf { it.parentFile == context.cacheDir && it.name.startsWith(SYNC_RESTORE_FILE_PREFIX) }
                    ?.delete()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return isRunning(context.workManager)
        }

        fun isRunning(workManager: WorkManager): Boolean {
            return workManager.isRunning(TAG)
        }

        internal suspend fun hasUnfinished(workManager: WorkManager): Boolean =
            workManager.getWorkInfosForUniqueWorkFlow(TAG).first().any { !it.state.isFinished }

        fun start(
            context: Context,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
        ) {
            start(context.workManager, uri, options, sync)
        }

        fun start(
            workManager: WorkManager,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
        ) {
            workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request(uri, options, sync))
        }

        internal suspend fun startSync(
            manager: WorkManager,
            uri: Uri,
            options: RestoreOptions,
            receipt: SyncReceipt?,
        ): Boolean {
            val request = request(uri, options, sync = true, receipt = receipt)
            // KEEP succeeds even if rejected. Persist the queue decision before releasing the sync lock.
            return withContext(NonCancellable) {
                manager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request).await()
                manager.getWorkInfoByIdFlow(request.id).first() != null
            }
        }

        private fun request(uri: Uri, options: RestoreOptions, sync: Boolean, receipt: SyncReceipt? = null) =
            OneTimeWorkRequestBuilder<BackupRestoreJob>()
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        LOCATION_URI_KEY to uri.toString(),
                        SYNC_KEY to sync,
                        OPTIONS_KEY to options.asBooleanArray(),
                        SYNC_PROVIDER_KEY to receipt?.provider?.name,
                        SYNC_REVISION_KEY to receipt?.revision,
                    ),
                )
                .build()

        fun stop(workManager: WorkManager) {
            workManager.cancelUniqueWork(TAG)
        }
    }
}

internal suspend fun finishCloudRestore(complete: Boolean, preferences: SyncPreferences, receipt: SyncReceipt?): Boolean {
    if (!complete) return false
    cloudSyncLock.withLock {
        currentCoroutineContext().ensureActive()
        receipt?.acknowledge(preferences)
        preferences.lastSyncTimestamp().set(System.currentTimeMillis())
    }
    return true
}

private const val TAG = "BackupRestore"

private const val LOCATION_URI_KEY = "location_uri" // String

private const val SYNC_KEY = "sync" // Boolean
private const val OPTIONS_KEY = "options" // BooleanArray

private const val SYNC_PROVIDER_KEY = "sync_provider"
private const val SYNC_REVISION_KEY = "sync_revision"
internal const val SYNC_RESTORE_FILE_PREFIX = "Animetail_sync_"
