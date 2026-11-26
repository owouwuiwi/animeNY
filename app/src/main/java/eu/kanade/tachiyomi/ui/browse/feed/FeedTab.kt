package eu.kanade.tachiyomi.ui.browse.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.FeedAddDialog
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedDisplayOptionsDialog
import eu.kanade.presentation.browse.FeedOrderScreen
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.browse.components.FeedActionsDialog
import eu.kanade.presentation.browse.components.FeedSortAlphabeticallyDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.feedTab(active: Boolean): TabContent {
    var section by rememberSaveable { mutableStateOf(FeedSection.ANIME) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = active && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val selected = when (section) {
        FeedSection.ANIME -> animeFeedTab(rememberScreenModel { FeedScreenModel() }, visible)
        FeedSection.MANGA -> mangaFeedTab(rememberScreenModel { MangaFeedScreenModel() }, visible)
    }

    return selected.copy(
        content = { contentPadding, snackbarHostState ->
            Column(Modifier.fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                ) {
                    FeedSection.entries.forEach { option ->
                        SegmentedButton(
                            selected = section == option,
                            onClick = { section = option },
                            shape = SegmentedButtonDefaults.itemShape(option.ordinal, FeedSection.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (option) {
                                        FeedSection.ANIME -> MR.strings.label_anime
                                        FeedSection.MANGA -> MR.strings.label_manga
                                    },
                                ),
                            )
                        }
                    }
                }
                key(section) {
                    selected.content(contentPadding, snackbarHostState)
                }
            }
        },
    )
}

private enum class FeedSection {
    ANIME,
    MANGA,
}

@Composable
private fun animeFeedTab(
    screenModel: FeedScreenModel,
    active: Boolean,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    val groupBySource by screenModel.sourcePreferences.feedGroupBySource.changes()
        .collectAsState(screenModel.sourcePreferences.feedGroupBySource.get())
    val sortOrder by screenModel.sourcePreferences.feedSortOrder.changes()
        .collectAsState(screenModel.sourcePreferences.feedSortOrder.get())
    var showFeedDisplayOptions by rememberSaveable { mutableStateOf(false) }

    // KMK -->
    val scope = rememberCoroutineScope()
    val showingFeedOrderScreen = rememberSaveable { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = active && showingFeedOrderScreen.value) {
        when {
            showingFeedOrderScreen.value -> showingFeedOrderScreen.value = false
        }
    }
    // KMK <--

    LaunchedEffect(screenModel, active) {
        if (active) screenModel.loadWhileVisible()
    }

    return TabContent(
        titleRes = TLMR.strings.feed,
        actions =
        if (showingFeedOrderScreen.value) {
            persistentListOf(
                AppBar.Action(
                    title = stringResource(TLMR.strings.action_sort_feed),
                    icon = Icons.Outlined.SwapVert,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { showingFeedOrderScreen.value = false },
                ),
                AppBar.Action(
                    title = stringResource(MR.strings.action_sort),
                    icon = Icons.Outlined.SortByAlpha,
                    onClick = { screenModel.showDialog(FeedScreenModel.Dialog.SortAlphabetically) },
                ),
            )
        } else {
            // KMK <--
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_add),
                    icon = Icons.Outlined.Add,
                    onClick = {
                        screenModel.openAddDialog()
                    },
                ),
                // KMK -->
                AppBar.Action(
                    title = stringResource(TLMR.strings.action_sort_feed),
                    icon = Icons.Outlined.SwapVert,
                    onClick = { showingFeedOrderScreen.value = true },
                ),
                AppBar.Action(
                    title = stringResource(TLMR.strings.feed_display_options),
                    icon = Icons.Outlined.Tune,
                    onClick = { showFeedDisplayOptions = true },
                ),
                // KMK <--
            )
        },
        content = { contentPadding, snackbarHostState ->
            // KMK -->
            Crossfade(
                targetState = showingFeedOrderScreen.value,
                label = "feed_order_crossfade",
            ) { showingFeedOrderScreen ->
                if (showingFeedOrderScreen) {
                    FeedOrderScreen(
                        state = state,
                        onClickDelete = screenModel::openDeleteDialog,
                        onClickMoveUp = screenModel::moveUp,
                        onClickMoveDown = screenModel::moveDown,
                    )
                } else {
                    // KMK <--
                    FeedScreen(
                        state = state,
                        contentPadding = contentPadding,
                        groupBySource = groupBySource,
                        sortOrder = sortOrder,
                        onClickSavedSearch = { savedSearch, source ->
                            screenModel.sourcePreferences.lastUsedSource.set(savedSearch.source)
                            navigator.push(
                                BrowseAnimeSourceScreen(
                                    source.id,
                                    listingQuery = savedSearch.query,
                                    savedSearch = savedSearch.id,
                                ),
                            )
                        },
                        onClickSource = { source ->
                            screenModel.sourcePreferences.lastUsedSource.set(source.id)
                            navigator.push(
                                BrowseAnimeSourceScreen(
                                    source.id,
                                    // KMK -->
                                    listingQuery = if (!source.supportsLatest) {
                                        GetRemoteAnime.QUERY_POPULAR
                                    } else {
                                        // KMK <--
                                        GetRemoteAnime.QUERY_LATEST
                                    },
                                ),
                            )
                        },
                        // KMK -->
                        onLongClickFeed = screenModel::openActionsDialog,
                        // KMK <--
                        onClickManga = {
                            // KMK -->
                            scope.launchIO {
                                val manga = screenModel.networkToLocalAnime.getLocal(it)
                                // KMK <--
                                navigator.push(AnimeScreen(manga.id, true))
                            }
                        },
                        // KMK -->
                        onLongClickManga = {
                            scope.launchIO {
                                val manga = screenModel.networkToLocalAnime.getLocal(it)
                                navigator.push(AnimeScreen(manga.id, true))
                            }
                        },
                        // KMK <--
                        onRefresh = screenModel::refresh,
                        getAnimeState = { anime -> screenModel.getManga(initialanime = anime) },
                    )
                }
            }

            if (showFeedDisplayOptions) {
                FeedDisplayOptionsDialog(
                    groupBySource = groupBySource,
                    sortOrder = sortOrder,
                    onGroupBySourceChange = screenModel.sourcePreferences.feedGroupBySource::set,
                    onSortOrderChange = screenModel.sourcePreferences.feedSortOrder::set,
                    onDismiss = { showFeedDisplayOptions = false },
                )
            }

            state.dialog?.let { dialog ->
                val onDismissRequest = screenModel::dismissDialog
                when (dialog) {
                    is FeedScreenModel.Dialog.AddFeed -> {
                        FeedAddDialog(
                            sources = dialog.options,
                            onDismiss = onDismissRequest,
                            onClickAdd = {
                                if (it != null) {
                                    screenModel.openAddSearchDialog(it)
                                }
                                onDismissRequest()
                            },
                        )
                    }

                    is FeedScreenModel.Dialog.AddFeedSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.options,
                            onDismiss = onDismissRequest,
                            onClickAdd = { source, savedSearch ->
                                screenModel.createFeed(source, savedSearch)
                                onDismissRequest()
                            },
                        )
                    }

                    is FeedScreenModel.Dialog.DeleteFeed -> {
                        SourceFeedDeleteDialog(
                            onDismissRequest = onDismissRequest,
                            deleteFeed = {
                                screenModel.deleteFeed(dialog.feed)
                                onDismissRequest()
                            },
                        )
                    }

                    // KMK -->
                    is FeedScreenModel.Dialog.FeedActions -> {
                        FeedActionsDialog(
                            feed = dialog.feedItem.feed,
                            title = dialog.feedItem.title,
                            canMoveUp = dialog.canMoveUp,
                            canMoveDown = dialog.canMoveDown,
                            onDismissRequest = onDismissRequest,
                            onClickDelete = { screenModel.openDeleteDialog(it) },
                            onMoveUp = { screenModel.moveUp(it) },
                            onMoveDown = { screenModel.moveDown(it) },
                        )
                    }

                    is FeedScreenModel.Dialog.SortAlphabetically -> {
                        FeedSortAlphabeticallyDialog(
                            onDismissRequest = onDismissRequest,
                            onSort = { screenModel.sortAlphabetically() },
                        )
                    }
                    // KMK <--
                }
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            val tooManyFeedsString = stringResource(TLMR.strings.too_many_in_feed)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        FeedScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }

                        FeedScreenModel.Event.TooManyFeeds -> {
                            launch { snackbarHostState.showSnackbar(tooManyFeedsString) }
                        }
                    }
                }
            }
        },
    )
}
