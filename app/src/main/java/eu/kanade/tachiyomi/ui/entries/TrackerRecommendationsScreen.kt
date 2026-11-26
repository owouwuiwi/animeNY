package eu.kanade.tachiyomi.ui.entries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.anilist.AniListRecommendation
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.domain.extension.model.ContentWarning
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

internal data class TrackerRecommendationsScreen(
    private val entryId: Long,
    private val anime: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val graph = LocalContext.current.appGraph
        val enabledWarnings by graph.sourcePreferences.enabledContentWarnings.collectAsState()
        var response by remember { mutableStateOf<RecommendationResult>(RecommendationResult.Loading) }
        var retry by remember { mutableIntStateOf(0) }

        LaunchedEffect(graph, retry) {
            response = RecommendationResult.Loading
            response = try {
                withContext(Dispatchers.IO) { loadRecommendations(graph) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecommendationResult.Failed
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(TLMR.strings.anilist_recommendations),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            when (val result = response) {
                RecommendationResult.Loading -> LoadingScreen(Modifier.padding(paddingValues))

                RecommendationResult.NoLinkedTitle -> EmptyScreen(
                    message = stringResource(TLMR.strings.anilist_recommendations_needs_link),
                    modifier = Modifier.padding(paddingValues),
                )

                RecommendationResult.Failed -> Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(TLMR.strings.anilist_recommendations_failed))
                    TextButton(onClick = { retry++ }) { Text(stringResource(MR.strings.action_retry)) }
                }

                is RecommendationResult.Available -> {
                    val items = result.items.filter { it.isAdult == false || ContentWarning.NSFW in enabledWarnings }
                    if (items.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(MR.strings.no_results_found),
                            modifier = Modifier.padding(paddingValues),
                        )
                    } else {
                        FastScrollLazyColumn(contentPadding = paddingValues) {
                            items(items = items, key = { it.id }) { entry ->
                                ListItem(
                                    modifier = Modifier.clickable {
                                        if (anime) {
                                            navigator.push(GlobalAnimeSearchScreen(entry.title))
                                        } else {
                                            navigator.push(GlobalMangaSearchScreen(entry.title))
                                        }
                                    },
                                    headlineContent = { Text(entry.title) },
                                    trailingContent = {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = stringResource(
                                                TLMR.strings.anilist_recommendations_find_in_sources,
                                            ),
                                        )
                                    },
                                    leadingContent = {
                                        AsyncImage(
                                            model = entry.coverUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(width = 42.dp, height = 60.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadRecommendations(graph: AppGraph): RecommendationResult {
        val linked = if (anime) {
            graph.getAnimeTracks.await(entryId).associate { it.trackerId to it.remoteId }
        } else {
            graph.getMangaTracks.await(entryId).associate { it.trackerId to it.remoteId }
        }
        val tracker = graph.trackerManager
        val aniListId = linked[tracker.aniList.id].asCatalogueId()
        val myAnimeListId = linked[tracker.myAnimeList.id].asCatalogueId().takeIf { aniListId == null }
        if (aniListId == null && myAnimeListId == null) return RecommendationResult.NoLinkedTitle
        return RecommendationResult.Available(tracker.aniList.getRecommendations(aniListId, myAnimeListId, anime))
    }

    private fun Long?.asCatalogueId(): Int? = this?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()

    private sealed interface RecommendationResult {
        data object Loading : RecommendationResult
        data object NoLinkedTitle : RecommendationResult
        data object Failed : RecommendationResult
        data class Available(val items: List<AniListRecommendation>) : RecommendationResult
    }
}
