package eu.kanade.tachiyomi.ui.player.dlna

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.IOException

@Composable
internal fun DlnaCastDialog(
    media: DlnaMedia?,
    session: DlnaSession?,
    onStarted: (DlnaSession) -> Unit,
    onStopped: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var searchAttempt by remember { mutableIntStateOf(0) }
    var discovery by remember { mutableStateOf<DlnaControlPoint?>(null) }
    var devices by remember { mutableStateOf(emptyList<DlnaRenderer>()) }
    var searching by remember { mutableStateOf(media != null && session == null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val shouldDiscover = media != null && session == null

    LaunchedEffect(shouldDiscover, searchAttempt) {
        if (!shouldDiscover) return@LaunchedEffect
        searching = true
        error = false
        try {
            val controller = app.dlnaControlPoint()
            devices = controller.renderers()
            discovery = controller
        } catch (_: IOException) {
            devices = emptyList()
            discovery = null
            error = true
        } finally {
            searching = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        title = { Text(stringResource(TLMR.strings.action_dlna)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                when {
                    session != null -> Text(stringResource(TLMR.strings.dlna_cast_sent, session.rendererName))

                    media == null -> Text(stringResource(TLMR.strings.dlna_direct_video_required))

                    searching -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(TLMR.strings.dlna_searching))
                        }
                    }

                    devices.isEmpty() && error -> Text(
                        stringResource(TLMR.strings.dlna_no_wifi),
                        color = MaterialTheme.colorScheme.error,
                    )

                    devices.isEmpty() -> Text(stringResource(TLMR.strings.dlna_no_devices))

                    else -> {
                        Column(
                            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            devices.forEach { renderer ->
                                OutlinedButton(
                                    onClick = {
                                        val controller = discovery ?: return@OutlinedButton
                                        busy = true
                                        error = false
                                        scope.launch {
                                            try {
                                                onStarted(controller.start(renderer, media))
                                            } catch (_: IOException) {
                                                error = true
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(renderer.name)
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    Text(stringResource(TLMR.strings.dlna_working))
                } else if (error && (session != null || devices.isNotEmpty())) {
                    Text(stringResource(TLMR.strings.dlna_command_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (session != null) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = false
                        scope.launch {
                            try {
                                session.stop()
                                onStopped()
                            } catch (_: IOException) {
                                error = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(TLMR.strings.dlna_stop))
                }
            } else if (shouldDiscover && !searching) {
                TextButton(enabled = !busy, onClick = { searchAttempt++ }) {
                    Text(stringResource(TLMR.strings.dlna_search_again))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_close))
            }
        },
    )
}
