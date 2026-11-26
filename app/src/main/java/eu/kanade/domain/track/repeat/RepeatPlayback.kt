package eu.kanade.domain.track.repeat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap

internal class RepeatPlayback(private val tracker: TrackRepeats, private val key: RepeatKey, val sessionId: String) {
    private val reported = ConcurrentHashMap.newKeySet<Pair<Int, Boolean>>()

    fun report(
        scope: CoroutineScope,
        number: Double,
        completed: Boolean,
        isReady: suspend () -> Boolean = { true },
    ) {
        val part = number.toRepeatPart() ?: return
        if (!completed && (part != 1 || (part to true) in reported)) return
        val change = part to completed
        if (!reported.add(change)) return

        scope.launch {
            var saved = false
            try {
                if (isReady()) {
                    saved = withNonCancellableContext { tracker.record(key, sessionId, part, completed) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not save tracked repeat progress" }
            } finally {
                if (!saved) reported.remove(change)
            }
        }
    }
}
