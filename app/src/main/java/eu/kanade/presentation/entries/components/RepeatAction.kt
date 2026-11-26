package eu.kanade.presentation.entries.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.domain.track.repeat.RepeatKey
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.tail.TLMR

@Composable
internal fun repeatAction(
    key: RepeatKey,
    parts: @Composable () -> Map<Int, Long>,
    open: suspend (partId: Long) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val tracker = context.appGraph.trackRepeats
    val enabled by remember(tracker) {
        tracker.enabledFlow()
    }.collectAsStateWithLifecycle(initialValue = tracker.enabled)
    if (!enabled) return null

    val scope = rememberCoroutineScope()
    val availableParts = parts()
    val availability = remember(tracker, key, availableParts) {
        tracker.available(key, availableParts.keys).catch { error ->
            context.logcat(LogPriority.WARN, error) { "Could not read tracked repeat availability" }
            emit(false)
        }
    }
    val available by availability.collectAsStateWithLifecycle(initialValue = false)
    if (!available) return null

    return {
        scope.launch {
            try {
                val partId = tracker.prepare(key, availableParts)
                if (partId != null) {
                    open(partId)
                } else {
                    context.toast(TLMR.strings.track_repeat_no_next_part)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.logcat(LogPriority.WARN, e) { "Could not prepare a tracked repeat" }
                context.toast(TLMR.strings.track_repeat_open_error)
            }
        }
    }
}
