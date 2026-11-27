package eu.kanade.tachiyomi.ui.entries.merge

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.source.anime.isLocalOrStub
import eu.kanade.tachiyomi.source.manga.isLocalOrStub
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.entries.merge.model.MergedPart
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
internal fun CombinedEntryParts(
    group: EntryMerge,
    openMember: (ownerId: Long) -> Unit,
    openPart: (ownerId: Long, partId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updates = remember(context, group) {
        observeParts(context, group).map { Result.success(it) }.catch { emit(Result.failure(it)) }
    }
    val state by updates.collectAsStateWithLifecycle(initialValue = null)
    val labels by produceState(emptyMap(), group.media, group.members) {
        value = withIOContext { group.members.associateWith { sourceLabel(context, group.media, it) } }
    }
    val data = state?.getOrNull()
    if (data == null) {
        if (state == null) {
            LoadingScreen(modifier)
        } else {
            EmptyScreen(stringRes = MR.strings.unknown_error, modifier = modifier)
        }
        return
    }

    LazyColumn(modifier = modifier) {
        val metadata = data.metadata
        if (metadata != null) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ItemCover.Book(
                        data = metadata.cover,
                        modifier = Modifier.width(84.dp),
                        contentDescription = metadata.title,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(metadata.title, style = MaterialTheme.typography.headlineSmall)
                        metadata.description?.takeIf(String::isNotBlank)?.let {
                            Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (data.members.isNotEmpty()) {
            item {
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { expanded = true }) {
                        Text(stringResource(TLMR.strings.entry_merge_source_details))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        data.members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(member.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        labels[member.reference]?.let { label ->
                                            Text(label.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    openMember(member.ownerId)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (data.hasUnavailable) {
            item {
                Text(
                    stringResource(TLMR.strings.entry_merge_unavailable),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (data.parts.isEmpty()) {
            item {
                Text(stringResource(TLMR.strings.entry_merge_empty), modifier = Modifier.padding(16.dp))
            }
        }
        items(data.parts, key = { "${it.ownerId}/${it.partId}" }) { part ->
            val label = labels[part.member]
            ListItem(
                headlineContent = { Text(part.title) },
                supportingContent = { label?.let { Text(it.name) } },
                leadingContent = {
                    if (part.completed) {
                        Icon(Icons.Outlined.Done, stringResource(TLMR.strings.entry_merge_completed_part))
                    }
                },
                trailingContent = {
                    if (label?.canDownload == true) {
                        IconButton(onClick = { scope.launch { downloadPart(context, part) } }) {
                            Icon(MaterialSymbols.Rounded.Download, stringResource(MR.strings.manga_download))
                        }
                    }
                },
                modifier = Modifier.clickable { openPart(part.ownerId, part.partId) },
            )
        }
    }
}

private fun observeParts(context: Context, group: EntryMerge): Flow<CombinedParts> {
    val observe = context.appGraph.observeMergedEntry
    return when (group.media) {
        MergeMedia.Anime -> observe.anime(group).map { snapshot ->
            CombinedParts(
                metadata = snapshot.metadata?.let { MergeMetadata(it.title, it.description, it) },
                members = snapshot.members.map { (reference, anime) -> MergeMemberRow(reference, anime.id, anime.title) },
                hasUnavailable = snapshot.unavailableMembers.isNotEmpty(),
                parts = snapshot.parts.map { it.toRow(it.value.seen) },
            )
        }

        MergeMedia.Manga -> observe.manga(group).map { snapshot ->
            CombinedParts(
                metadata = snapshot.metadata?.let { MergeMetadata(it.title, it.description, it) },
                members = snapshot.members.map { (reference, manga) -> MergeMemberRow(reference, manga.id, manga.title) },
                hasUnavailable = snapshot.unavailableMembers.isNotEmpty(),
                parts = snapshot.parts.map { it.toRow(it.value.read) },
            )
        }
    }
}

private suspend fun sourceLabel(context: Context, media: MergeMedia, ref: MergeEntryRef): MergeSourceLabel {
    return when (media) {
        MergeMedia.Anime -> context.appGraph.animeSourceManager.getOrStub(ref.sourceId).let {
            MergeSourceLabel(it.name, !it.isLocalOrStub())
        }

        MergeMedia.Manga -> context.appGraph.mangaSourceManager.getOrStub(ref.sourceId).let {
            MergeSourceLabel(it.name, !it.isLocalOrStub())
        }
    }
}

private suspend fun downloadPart(context: Context, row: MergePartRow) {
    try {
        withIOContext {
            val graph = context.appGraph
            when (val part = row.value) {
                is Episode -> {
                    val anime = graph.getAnime.await(row.ownerId) ?: return@withIOContext
                    if (anime.source != row.member.sourceId || anime.url != row.member.url) return@withIOContext
                    graph.animeDownloadManager.downloadEpisodes(anime, listOf(part))
                }

                is Chapter -> {
                    val manga = graph.getManga.await(row.ownerId) ?: return@withIOContext
                    if (manga.source != row.member.sourceId || manga.url != row.member.url) return@withIOContext
                    graph.mangaDownloadManager.downloadChapters(manga, listOf(part))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        context.toast(MR.strings.unknown_error)
    }
}

private fun MergedPart<*>.toRow(completed: Boolean) = MergePartRow(member, ownerId, partId, title, completed, value)

private data class CombinedParts(
    val metadata: MergeMetadata?,
    val members: List<MergeMemberRow>,
    val hasUnavailable: Boolean,
    val parts: List<MergePartRow>,
)

private data class MergeMetadata(val title: String, val description: String?, val cover: Any)
private data class MergeMemberRow(val reference: MergeEntryRef, val ownerId: Long, val title: String)
private data class MergeSourceLabel(val name: String, val canDownload: Boolean)

private data class MergePartRow(
    val member: MergeEntryRef,
    val ownerId: Long,
    val partId: Long,
    val title: String,
    val completed: Boolean,
    val value: Any?,
)
