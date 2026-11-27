package eu.kanade.tachiyomi.ui.browse.feed

import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class MangaFeedScreenModel(
    sourceManager: MangaSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<MangaFeedState>(MangaFeedState()) {

    val sortOrder = sourcePreferences.mangaFeedSortOrder

    private val selectedIds = sourcePreferences.mangaFeedSourceIds
    private val requests = MutableStateFlow(RefreshRequest())
    private val slots = Semaphore(permits = 4)
    private var lastRequests = emptyMap<Long, Pair<Long, Long>>()

    private val visibleSources = combine(
        sourceManager.catalogueSources,
        sourcePreferences.enabledLanguages.changes(),
        sourcePreferences.disabledMangaSources.changes(),
        sourcePreferences.pinnedMangaSources.changes(),
    ) { catalogue, languages, disabled, pinned ->
        catalogue.filter { it.lang in languages && it.id.toString() !in disabled }
            .sortedWith(compareBy({ it.id.toString() !in pinned }, { it.name.lowercase() }, { it.lang }, { it.id }))
    }

    suspend fun loadWhileVisible() {
        combine(visibleSources, selectedIds.changes(), requests, ::FeedRequest)
            .collectLatest(::loadFeed)
    }

    fun addSource(sourceId: Long) {
        val current = selectedIds.get()
        if (sourceId !in current && state.value.sourcesToAdd.any { it.id == sourceId }) {
            selectedIds.set(current + sourceId)
        }
    }

    fun removeSource(sourceId: Long) {
        selectedIds.set(selectedIds.get().filterNot { it == sourceId })
    }

    fun refresh() {
        requests.update { RefreshRequest(all = it.all + 1) }
    }

    fun retry(sourceId: Long) {
        requests.update { request ->
            val revision = request.sourceRevisions.getOrDefault(sourceId, 0L) + 1
            request.copy(sourceRevisions = request.sourceRevisions + (sourceId to revision))
        }
    }

    suspend fun getLocalManga(manga: Manga): Manga = networkToLocalManga.getLocal(manga)

    private suspend fun loadFeed(request: FeedRequest) = coroutineScope {
        val sources = request.visibleSources.associateBy { it.id }
        val ids = request.sourceIds.distinct()
        val previous = state.value.rows.orEmpty().associateBy { it.sourceId }
        val nextRequests = ids.associateWith(request.refresh::revisionOf)
        val rows = ids.map { id ->
            val source = sources[id]
            val prior = previous[id]
            when {
                source == null -> MangaFeedRow(id, prior?.name ?: id.toString(), null, MangaFeedResult.Unavailable)
                prior != null &&
                    prior.source === source &&
                    lastRequests[id] == nextRequests[id] &&
                    prior.result != MangaFeedResult.Loading -> prior

                else -> MangaFeedRow(id, source.name, source, MangaFeedResult.Loading)
            }
        }
        lastRequests = nextRequests
        mutableState.value = MangaFeedState(
            sourcesToAdd = request.visibleSources.filterNot { it.id in nextRequests },
            rows = rows,
        )
        rows.filter { it.result == MangaFeedResult.Loading || it.result is MangaFeedResult.Ready }.forEach { row ->
            launch {
                val fetched = if (row.result == MangaFeedResult.Loading) {
                    slots.withPermit { fetch(requireNotNull(row.source)) }
                } else {
                    row.result
                }
                val result = if (fetched is MangaFeedResult.Ready) {
                    fetched.copy(lastLocalUpdate = latestLocalUpdate(row.sourceId, fetched.titles))
                } else {
                    fetched
                }
                mutableState.update { current ->
                    current.copy(
                        rows = current.rows?.map { if (it.sourceId == row.sourceId) it.copy(result = result) else it },
                    )
                }
            }
        }
    }

    private suspend fun fetch(source: CatalogueSource): MangaFeedResult {
        return try {
            val titles = withIOContext {
                val page = if (source.supportsLatest) source.getLatestUpdates(1) else source.getPopularManga(1)
                page.mangas.distinctBy { it.url }.map { it.toDomainManga(source.id) }
            }
            MangaFeedResult.Ready(titles)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            MangaFeedResult.Failed
        }
    }

    private suspend fun latestLocalUpdate(sourceId: Long, titles: List<Manga>): Long {
        return try {
            withIOContext { getManga.latestUpdateForUrls(sourceId, titles.map { it.url }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to read the latest local manga feed update" }
            0L
        }
    }

    private data class FeedRequest(
        val visibleSources: List<CatalogueSource>,
        val sourceIds: List<Long>,
        val refresh: RefreshRequest,
    )

    private data class RefreshRequest(
        val all: Long = 0L,
        val sourceRevisions: Map<Long, Long> = emptyMap(),
    ) {
        fun revisionOf(sourceId: Long): Pair<Long, Long> = all to sourceRevisions.getOrDefault(sourceId, 0L)
    }
}

internal data class MangaFeedState(
    val sourcesToAdd: List<CatalogueSource> = emptyList(),
    val rows: List<MangaFeedRow>? = null,
)

internal data class MangaFeedRow(
    val sourceId: Long,
    val name: String,
    val source: CatalogueSource?,
    val result: MangaFeedResult,
) {
    val lastLocalUpdate: Long get() = (result as? MangaFeedResult.Ready)?.lastLocalUpdate ?: 0L
}

internal sealed interface MangaFeedResult {
    data object Unavailable : MangaFeedResult
    data object Loading : MangaFeedResult
    data object Failed : MangaFeedResult
    data class Ready(val titles: List<Manga>, val lastLocalUpdate: Long = 0L) : MangaFeedResult
}
