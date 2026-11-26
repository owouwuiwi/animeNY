package eu.kanade.tachiyomi.ui.entries.merge

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.coroutines.CancellationException
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeDuplicates
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.UUID

@Composable
internal fun EntryMergeEditor(
    media: MergeMedia,
    seed: MergeEntryRef,
    current: EntryMerge?,
    unavailable: Set<MergeEntryRef>,
    onSave: (EntryMerge) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val choices by produceState<Result<List<MergeChoice>>?>(null, media, seed, current) {
        value = try {
            Result.success(withIOContext { loadMergeChoices(context, media, seed, current) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val candidates = choices?.getOrNull()
    if (candidates == null) {
        if (choices == null) {
            LoadingScreen(modifier)
        } else {
            EmptyScreen(stringRes = MR.strings.unknown_error, modifier = modifier)
        }
        return
    }

    val id = rememberSaveable(current?.id) { current?.id ?: UUID.randomUUID().toString() }
    var query by rememberSaveable(current?.id) { mutableStateOf("") }
    var members by rememberSaveable(current?.id) { mutableStateOf(current?.members ?: listOf(seed)) }
    var metadata by rememberSaveable(current?.id) { mutableStateOf(current?.metadata ?: seed) }
    var duplicates by rememberSaveable(current?.id) { mutableStateOf(current?.duplicates ?: MergeDuplicates.KeepAll) }
    val candidateByRef = candidates.associateBy(MergeChoice::reference)

    Column(modifier.padding(horizontal = 16.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text(
                    text = stringResource(TLMR.strings.entry_merge_priority),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item {
                Text(
                    text = stringResource(TLMR.strings.entry_merge_metadata),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(members) { reference ->
                val index = members.indexOf(reference)
                val chosen = candidateByRef[reference]
                val choose = { metadata = reference }
                ListItem(
                    headlineContent = { Text(chosen?.title ?: reference.url) },
                    supportingContent = { chosen?.let { Text(it.source) } },
                    leadingContent = { RadioButton(selected = metadata == reference, onClick = choose) },
                    trailingContent = {
                        Row {
                            IconButton(
                                enabled = index > 0,
                                onClick = { members = members.move(index, index - 1) },
                            ) {
                                Icon(Icons.Outlined.ArrowUpward, stringResource(TLMR.strings.entry_merge_move_up))
                            }
                            IconButton(
                                enabled = index < members.lastIndex,
                                onClick = { members = members.move(index, index + 1) },
                            ) {
                                Icon(Icons.Outlined.ArrowDownward, stringResource(TLMR.strings.entry_merge_move_down))
                            }
                        }
                    },
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MergeDuplicates.entries.forEach { option ->
                        FilterChip(
                            selected = duplicates == option,
                            onClick = { duplicates = option },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            MergeDuplicates.KeepAll -> TLMR.strings.entry_merge_keep_all
                                            MergeDuplicates.Number -> TLMR.strings.entry_merge_by_number
                                            MergeDuplicates.Title -> TLMR.strings.entry_merge_by_title
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(MR.strings.action_search_hint)) },
                )
            }
            val visible = candidates
                .filterNot { it.reference in unavailable }
                .filter { it.title.contains(query, ignoreCase = true) || it.source.contains(query, ignoreCase = true) }
            if (visible.none { it.reference != seed }) {
                item {
                    val message = if (query.isBlank()) {
                        TLMR.strings.entry_merge_choose_library
                    } else {
                        MR.strings.no_results_found
                    }
                    Text(
                        text = stringResource(message),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(visible) { entry ->
                val checked = entry.reference in members
                ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(entry.source) },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = checked,
                        enabled = entry.reference != seed,
                        role = Role.Checkbox,
                        onValueChange = { include ->
                            members = if (include) members + entry.reference else members - entry.reference
                            if (metadata !in members) metadata = members.first()
                        },
                    ),
                )
            }
        }
        Button(
            onClick = { onSave(EntryMerge(id, media, metadata, members, duplicates)) },
            enabled = members.size >= 2,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(stringResource(MR.strings.action_save))
        }
    }
}

private suspend fun loadMergeChoices(
    context: Context,
    media: MergeMedia,
    seed: MergeEntryRef,
    current: EntryMerge?,
): List<MergeChoice> {
    val graph = context.appGraph
    val required = (current?.members.orEmpty() + seed).distinct()
    val choices = when (media) {
        MergeMedia.Anime -> {
            val anime = graph.getAnimeFavorites.await() + required.mapNotNull {
                graph.getAnimeByUrlAndSourceId.await(it.url, it.sourceId)
            }
            anime.filter { it.url.isNotBlank() && it.fetchType == FetchType.Episodes }.distinctBy { it.id }.map {
                MergeChoice(
                    MergeEntryRef(it.source, it.url),
                    it.title,
                    graph.animeSourceManager.getOrStub(it.source).name,
                )
            }
        }

        MergeMedia.Manga -> {
            val manga = graph.getMangaFavorites.await() + required.mapNotNull {
                graph.getMangaByUrlAndSourceId.await(it.url, it.sourceId)
            }
            manga.filter { it.url.isNotBlank() }.distinctBy { it.id }.map {
                MergeChoice(
                    MergeEntryRef(it.source, it.url),
                    it.title,
                    graph.mangaSourceManager.getOrStub(it.source).name,
                )
            }
        }
    }
    val present = choices.mapTo(hashSetOf(), MergeChoice::reference)
    val missing = required.filterNot(present::contains).map {
        MergeChoice(it, context.stringResource(TLMR.strings.entry_merge_unavailable), it.sourceId.toString())
    }
    return (choices + missing).sortedBy { it.title.lowercase() }
}

private data class MergeChoice(val reference: MergeEntryRef, val title: String, val source: String)

private fun <T> List<T>.move(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }
