package eu.kanade.presentation.entries.anime.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun EditEpisodeTitleDialog(
    episode: Episode,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(episode.id) { mutableStateOf(episode.displayTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(TLMR.strings.edit_episode_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.takeUnless { it.trim() == episode.name }) }) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            if (episode.customTitle != null) {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(MR.strings.action_reset)) }
            }
        },
    )
}
