package eu.kanade.domain.track.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.service.episodeTrackingNumbers
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack
import kotlin.math.max

@Inject
class SyncEpisodeProgressWithTrack(
    private val updateEpisode: UpdateEpisode,
    private val insertTrack: InsertAnimeTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val trackPreferences: TrackPreferences,
    private val getAnime: GetAnime,
) {

    suspend fun await(
        animeId: Long,
        remoteTrack: AnimeTrack,
        service: AnimeTracker,
    ) {
        val supportsEnhancedSync = service is EnhancedAnimeTracker
        if (!supportsEnhancedSync && !trackPreferences.importTrackerProgress.get()) {
            return
        }
        val remoteProgress = remoteTrack.lastEpisodeSeen
        if (!remoteProgress.isFinite() || (!supportsEnhancedSync && remoteProgress <= 0)) return

        val anime = getAnime.await(animeId) ?: return
        val episodes = getEpisodesByAnimeId.await(animeId)
        val trackingNumbers = episodeTrackingNumbers(anime, episodes)
        val sortedEpisodes = episodes.mapNotNull { episode ->
            trackingNumbers[episode.id]
                ?.takeIf { it.isFinite() && it >= 0 }
                ?.let { episode to it }
        }.sortedBy { (_, trackingNumber) -> trackingNumber }

        val episodeUpdates = sortedEpisodes
            .filter { (episode, trackingNumber) -> trackingNumber <= remoteProgress && !episode.seen }
            .map { (episode, _) -> EpisodeUpdate(id = episode.id, seen = true) }

        if (!supportsEnhancedSync) {
            if (episodeUpdates.isNotEmpty()) updateEpisode.awaitAll(episodeUpdates)
            return
        }

        // only take into account continuous watching
        val localLastSeen = sortedEpisodes.takeWhile { (episode, _) -> episode.seen }.lastOrNull()?.second ?: 0.0
        val lastSeen = max(remoteProgress, localLastSeen)
        val updatedTrack = remoteTrack.copy(lastEpisodeSeen = lastSeen)

        try {
            service.update(updatedTrack.toDbTrack())
            updateEpisode.awaitAll(episodeUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e)
        }
    }
}
