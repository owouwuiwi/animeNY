package eu.kanade.tachiyomi.ui.entries.merge

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.source.anime.isSourceForTorrents
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class CombinedEntriesScreen(
    private val media: MergeMedia,
    private val sourceId: Long,
    private val url: String,
) : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val seed = remember(sourceId, url) { MergeEntryRef(sourceId, url) }
        val repository = context.appGraph.entryMerges
        val scope = rememberCoroutineScope()
        val updates = remember(repository) {
            repository.observeAll().map { Result.success(it) }.catch { emit(Result.failure(it)) }
        }
        val state by updates.collectAsStateWithLifecycle(initialValue = null)
        var editing by rememberSaveable { mutableStateOf(false) }
        var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
        var confirmUnlink by rememberSaveable { mutableStateOf(false) }
        val groups = state?.getOrNull()
        val group = groups?.firstOrNull { it.media == media && (it.id == selectedGroupId || seed in it.members) }
        val inEditor = group == null || editing

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (inEditor) TLMR.strings.entry_merge_edit else TLMR.strings.entry_merge_title,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (editing) editing = false else navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(MR.strings.action_cancel))
                        }
                    },
                    actions = {
                        if (group != null && !inEditor) {
                            IconButton(onClick = { editing = true }) {
                                Icon(Icons.Outlined.Edit, stringResource(TLMR.strings.entry_merge_edit))
                            }
                            IconButton(onClick = { confirmUnlink = true }) {
                                Icon(Icons.Outlined.Delete, stringResource(TLMR.strings.entry_merge_unlink))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when {
                state == null -> LoadingScreen(modifier)

                groups == null -> EmptyScreen(MR.strings.unknown_error, modifier)

                inEditor -> EntryMergeEditor(
                    media = media,
                    seed = seed,
                    current = group,
                    unavailable = groups
                        .filter { it.media == media && it.id != group?.id }
                        .flatMapTo(hashSetOf()) { it.members },
                    onSave = { draft ->
                        scope.launch {
                            try {
                                if (repository.save(draft)) {
                                    selectedGroupId = draft.id
                                    editing = false
                                } else {
                                    context.toast(TLMR.strings.entry_merge_conflict)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                context.toast(MR.strings.unknown_error)
                            }
                        }
                    },
                    modifier = modifier,
                )

                else -> CombinedEntryParts(
                    group = checkNotNull(group),
                    openPart = { owner, part ->
                        scope.launch { open(context, owner, part) }
                    },
                    modifier = modifier,
                )
            }
        }

        if (confirmUnlink && group != null) {
            AlertDialog(
                onDismissRequest = { confirmUnlink = false },
                title = { Text(stringResource(TLMR.strings.entry_merge_unlink)) },
                text = { Text(stringResource(TLMR.strings.entry_merge_unlink_description)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmUnlink = false
                            scope.launch { unlink(context, group) }
                        },
                    ) { Text(stringResource(TLMR.strings.entry_merge_unlink)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmUnlink = false }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
    }

    private suspend fun open(context: Context, owner: Long, part: Long) {
        try {
            when (media) {
                MergeMedia.Anime -> withIOContext {
                    val graph = context.appGraph
                    val anime = graph.getAnime.await(owner) ?: error("Missing anime for the selected episode")
                    val source = graph.animeSourceManager.get(anime.source)
                    if (graph.torrentPreferences.torrServerEnable().get() && source.isSourceForTorrents()) {
                        TorrentServerService.start()
                    }
                    val external = graph.playerPreferences.alwaysUseExternalPlayer().get()
                    MainActivity.startPlayerActivity(context, owner, part, extPlayer = external)
                }

                MergeMedia.Manga -> context.startActivity(ReaderActivity.newIntent(context, owner, part))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            context.toast(MR.strings.unknown_error)
        }
    }

    private suspend fun unlink(context: Context, group: EntryMerge) {
        try {
            context.appGraph.entryMerges.remove(group.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            context.toast(MR.strings.unknown_error)
        }
    }
}
