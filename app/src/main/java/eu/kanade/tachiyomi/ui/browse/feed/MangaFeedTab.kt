package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.EmptyResultItem
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.browse.manga.components.MangaSourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
internal fun mangaFeedTab(screenModel: MangaFeedScreenModel, active: Boolean): TabContent {
    val state by screenModel.state.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var removingSource by rememberSaveable { mutableStateOf<Long?>(null) }
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val openError = stringResource(MR.strings.unknown_error)

    LaunchedEffect(screenModel, active) {
        if (active) screenModel.loadWhileVisible()
    }

    return TabContent(
        titleRes = TLMR.strings.feed,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_add),
                icon = Icons.Outlined.Add,
                onClick = { showAdd = true },
            ),
        ),
        content = { contentPadding, snackbar ->
            MangaFeedContent(
                state = state,
                contentPadding = contentPadding,
                onOpenSource = { source ->
                    navigator.push(
                        BrowseMangaSourceScreen(
                            source.id,
                            if (source.supportsLatest) GetRemoteManga.QUERY_LATEST else GetRemoteManga.QUERY_POPULAR,
                        ),
                    )
                },
                onOpenTitle = { title ->
                    scope.launch {
                        try {
                            val local = screenModel.getLocalManga(title)
                            navigator.push(MangaScreen(local.id, fromSource = true))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            snackbar.showSnackbar(openError)
                        }
                    }
                },
                onRemove = { removingSource = it },
                onRetry = screenModel::retry,
                onRefresh = screenModel::refresh,
            )

            if (active && showAdd) {
                AddMangaFeedSourceDialog(
                    sources = state.sourcesToAdd,
                    loading = state.rows == null,
                    onSelect = {
                        screenModel.addSource(it)
                        showAdd = false
                    },
                    onDismiss = { showAdd = false },
                )
            }

            if (active) {
                removingSource?.let { sourceId ->
                    SourceFeedDeleteDialog(
                        onDismissRequest = { removingSource = null },
                        deleteFeed = {
                            screenModel.removeSource(sourceId)
                            removingSource = null
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun MangaFeedContent(
    state: MangaFeedState,
    contentPadding: PaddingValues,
    onOpenSource: (CatalogueSource) -> Unit,
    onOpenTitle: (Manga) -> Unit,
    onRemove: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onRefresh: () -> Unit,
) {
    val rows = state.rows
    when {
        rows == null -> LoadingScreen()
        rows.isEmpty() -> EmptyScreen(
            stringRes = TLMR.strings.feed_tab_empty,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            val loading = rows.any { it.result == MangaFeedResult.Loading }
            PullRefresh(
                refreshing = loading,
                enabled = !loading,
                onRefresh = onRefresh,
            ) {
                ScrollbarLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    items(rows, key = { it.sourceId }) { row ->
                        MangaFeedSourceSection(
                            row = row,
                            onOpenSource = onOpenSource,
                            onOpenTitle = onOpenTitle,
                            onRemove = { onRemove(row.sourceId) },
                            onRetry = { onRetry(row.sourceId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaFeedSourceSection(
    row: MangaFeedRow,
    onOpenSource: (CatalogueSource) -> Unit,
    onOpenTitle: (Manga) -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val source = row.source
    Column {
        ListItem(
            supportingContent = {
                Text(
                    stringResource(
                        when {
                            source == null -> TLMR.strings.feed_manga_source_unavailable
                            source.supportsLatest -> MR.strings.latest
                            else -> MR.strings.popular
                        },
                    ),
                )
            },
            leadingContent = if (source == null) null else { { FeedSourceIcon(source) } },
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_remove))
                }
            },
            modifier = if (source == null) Modifier else Modifier.clickable { onOpenSource(source) },
        ) {
            Text(row.name)
        }
        when (val result = row.result) {
            MangaFeedResult.Unavailable -> Unit
            MangaFeedResult.Loading -> GlobalSearchLoadingResultItem()
            MangaFeedResult.Failed -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlobalSearchErrorResultItem(message = null)
                TextButton(onClick = onRetry) { Text(stringResource(MR.strings.action_retry)) }
            }
            is MangaFeedResult.Ready -> if (result.titles.isEmpty()) {
                EmptyResultItem()
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(result.titles, key = { it.url }) { title ->
                        Box(Modifier.width(96.dp)) {
                            EntryComfortableGridItem(
                                title = title.title,
                                coverData = title.asMangaCover(),
                                onClick = { onOpenTitle(title) },
                                onLongClick = { onOpenTitle(title) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMangaFeedSourceDialog(
    sources: List<CatalogueSource>,
    loading: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.action_add)) },
        text = {
            when {
                loading -> GlobalSearchLoadingResultItem()
                sources.isEmpty() -> Text(stringResource(TLMR.strings.feed_manga_no_sources))
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sources, key = { it.id }) { source ->
                        ListItem(
                            supportingContent = { Text(LocaleHelper.getLocalizedDisplayName(source.lang)) },
                            leadingContent = { FeedSourceIcon(source) },
                            modifier = Modifier.clickable { onSelect(source.id) },
                        ) {
                            Text(source.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun FeedSourceIcon(source: CatalogueSource) {
    MangaSourceIcon(
        source = Source(
            id = source.id,
            lang = source.lang,
            name = source.name,
            supportsLatest = source.supportsLatest,
            isStub = false,
        ),
    )
}
