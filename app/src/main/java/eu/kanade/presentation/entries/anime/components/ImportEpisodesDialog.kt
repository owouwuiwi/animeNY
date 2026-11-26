package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.anime.EpisodeImportCandidate
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun ImportEpisodesDialog(
    files: List<EpisodeImportCandidate>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(TLMR.strings.episode_import_action)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    Column {
                        Text(
                            text = file.fileName.ifBlank {
                                stringResource(TLMR.strings.episode_import_unavailable_file)
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = file.episode?.displayTitle
                                ?: stringResource(TLMR.strings.episode_import_unmatched),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = files.any { it.episode != null }) {
                Text(stringResource(TLMR.strings.episode_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
