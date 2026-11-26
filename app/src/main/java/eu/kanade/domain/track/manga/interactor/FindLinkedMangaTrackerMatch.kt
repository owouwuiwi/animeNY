package eu.kanade.domain.track.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaExternalTrackerIds
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

@Inject
class FindLinkedMangaTrackerMatch(
    private val trackerManager: TrackerManager,
    private val getMangaTracks: GetMangaTracks,
) {
    suspend fun await(mangaId: Long, destination: Tracker): Match? {
        if (!destination.isLoggedIn) return null
        val readId: (MangaBakaExternalTrackerIds) -> Long? = when (destination) {
            trackerManager.aniList -> { ids -> ids.aniList?.id }
            trackerManager.myAnimeList -> { ids -> ids.myAnimeList?.id }
            trackerManager.kitsu -> { ids -> ids.kitsu?.id }
            trackerManager.shikimori -> { ids -> ids.shikimori?.id }
            trackerManager.mangaUpdates -> { ids -> ids.mangaUpdates?.decimalId() }
            else -> return null
        }

        val mangaBaka = trackerManager.mangaBaka
        val existingTrack = getMangaTracks.await(mangaId).firstOrNull {
            it.trackerId == mangaBaka.id && it.remoteId > 0L
        } ?: return null
        val ids = mangaBaka.getExternalTrackerIds(existingTrack.remoteId) ?: return null
        val destinationId = readId(ids)?.takeIf { it > 0L } ?: return null
        val result = destination.mangaService.searchManga("id:$destinationId").singleOrNull {
            it.tracker_id == destination.id && it.remote_id == destinationId
        } ?: return null
        return Match(result, mangaBaka.name)
    }

    data class Match(val result: MangaTrackSearch, val trackerName: String)
}
