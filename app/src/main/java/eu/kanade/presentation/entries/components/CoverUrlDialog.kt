package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun CoverUrlDialog(onApply: suspend (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(TLMR.strings.cover_from_url)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    failed = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(TLMR.strings.cover_url_hint)) },
                supportingText = if (failed) {
                    { Text(stringResource(MR.strings.notification_cover_update_failed)) }
                } else {
                    null
                },
                isError = failed,
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !loading,
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            onApply(url)
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
