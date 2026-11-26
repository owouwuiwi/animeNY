package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.R
import kotlin.time.Duration.Companion.seconds

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 */
class AnimeDownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var downloadManager: AnimeDownloadManager

    @Inject private lateinit var downloadPreferences: DownloadPreferences

    init {
        graph.inject(this)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_EPISODE_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result = workerMutex.withLock {
        // After the app is updated or killed, WorkManager runs this job again before the queue is
        // restored. Starting with the queue still empty would end the job and leave it paused.
        downloadManager.awaitQueueRestored()

        if (downloadManager.queueState.value.isEmpty() ||
            !checkNetworkState(applicationContext.activeNetworkState(), downloadPreferences.downloadOnlyOverWifi.get())
        ) {
            return@withLock Result.success()
        }

        // A download can fail immediately and stop the worker. Wait for foreground
        // promotion before its pipeline can make a network request.
        setForegroundSafely()
        if (!downloadManager.downloaderStart()) return@withLock Result.success()

        try {
            coroutineScope {
                val monitor = combine(
                    applicationContext.networkStateFlow(),
                    downloadPreferences.downloadOnlyOverWifi.changes(),
                    ::checkNetworkState,
                ).launchIn(this)
                try {
                    while (downloadManager.isRunning) delay(1.seconds)
                } finally {
                    monitor.cancel()
                }
            }
        } finally {
            downloadManager.pauseActiveDownloads()
        }

        Result.success()
    }

    private fun checkNetworkState(state: NetworkState, requireWifi: Boolean): Boolean {
        return if (state.isOnline) {
            val noWifi = requireWifi && !state.isWifi
            if (noWifi) {
                downloadManager.downloaderStop(
                    applicationContext.getString(R.string.download_notifier_text_only_wifi),
                )
            }
            !noWifi
        } else {
            downloadManager.downloaderStop(applicationContext.getString(R.string.download_notifier_no_network))
            false
        }
    }

    companion object {
        private const val TAG = "AnimeDownloader"
        // A newly enqueued worker must wait for a cancelled one to release the downloader.
        private val workerMutex = Mutex()

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
