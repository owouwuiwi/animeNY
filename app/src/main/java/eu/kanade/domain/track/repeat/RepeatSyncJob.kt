package eu.kanade.domain.track.repeat

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class RepeatSyncJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withIOContext {
        if (runAttemptCount >= 6) return@withIOContext Result.failure()
        try {
            if (applicationContext.appGraph.trackRepeats.sync()) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not synchronize tracked repeats" }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TrackedRepeatSync"

        internal fun schedule(context: Context) {
            val work = OneTimeWorkRequestBuilder<RepeatSyncJob>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}
