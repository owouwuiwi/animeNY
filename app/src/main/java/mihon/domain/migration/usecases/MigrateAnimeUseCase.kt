package mihon.domain.migration.usecases

import dev.zacsweers.metro.Inject
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.model.toEpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import java.time.Instant

@Inject
class MigrateAnimeUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val updateAnime: UpdateAnime,
    private val episodeRepository: EpisodeRepository,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val getCategories: GetAnimeCategories,
    private val animeRepository: AnimeRepository,
    private val trackRepository: AnimeTrackRepository,
    private val migrateEntryTags: MigrateEntryTags,
    private val coverCache: AnimeCoverCache,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedAnimeTracker>() }

    suspend operator fun invoke(current: Anime, target: Anime, replace: Boolean): Boolean {
        if (current.id == target.id) return false

        return try {
            val targetSource = sourceManager.get(target.source) ?: return false
            val currentSource = sourceManager.get(current.source)
            val flags = MigrationFlag.fromBit(sourcePreferences.migrationFlags.get())
            val episodes = targetSource.getEpisodeList(target.toSAnime())
            syncEpisodesWithSource.await(episodes, target, targetSource)

            // Update episodes seen, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                val prevAnimeEpisodes = episodeRepository.getEpisodeByAnimeId(current.id)
                val animeEpisodes = episodeRepository.getEpisodeByAnimeId(target.id)

                val maxEpisodeSeen = prevAnimeEpisodes
                    .filter { it.seen }
                    .maxOfOrNull { it.episodeNumber }

                val updatedAnimeEpisodes = animeEpisodes.map { animeEpisode ->
                    var updatedEpisode = animeEpisode
                    if (updatedEpisode.isRecognizedNumber) {
                        val prevEpisode = prevAnimeEpisodes
                            .find { it.isRecognizedNumber && it.episodeNumber == updatedEpisode.episodeNumber }

                        if (prevEpisode != null) {
                            updatedEpisode = updatedEpisode.copy(
                                dateFetch = prevEpisode.dateFetch,
                                bookmark = prevEpisode.bookmark,
                                fillermark = prevEpisode.fillermark,
                                lastSecondSeen = prevEpisode.lastSecondSeen,
                                totalSeconds = prevEpisode.totalSeconds,
                            )
                        }

                        if (prevEpisode?.seen == true ||
                            (maxEpisodeSeen != null && updatedEpisode.episodeNumber <= maxEpisodeSeen)
                        ) {
                            updatedEpisode = updatedEpisode.copy(seen = true)
                        }
                    }

                    updatedEpisode
                }

                val episodeUpdates = updatedAnimeEpisodes.map { it.toEpisodeUpdate() }
                episodeRepository.updateAllEpisodes(episodeUpdates)
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                animeRepository.setAnimeCategories(target.id, categoryIds)
            }

            if (MigrationFlag.TAGS in flags) {
                migrateEntryTags.copy(current, target)
            }

            // Update track
            trackRepository.getTracksByAnimeId(current.id).mapNotNull { track ->
                val updatedTrack = track.copy(animeId = target.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                if (service != null) {
                    try {
                        service.migrateTrack(updatedTrack, target, targetSource)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e)
                        updatedTrack
                    }
                } else {
                    updatedTrack
                }
            }
                .takeIf { it.isNotEmpty() }
                ?.let { trackRepository.upsertAllAnime(it) }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover(coverCache)) {
                coverCache.getCustomCoverFile(current.id).inputStream().use { input ->
                    coverCache.setCustomCoverToCache(target, input)
                }
            }

            val currentAnimeUpdate = AnimeUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
            )
                .takeIf { replace }
            val targetAnimeUpdate = AnimeUpdate(
                id = target.id,
                favorite = true,
                episodeFlags = current.episodeFlags,
                viewerFlags = current.viewerFlags,
                dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
            )

            currentCoroutineContext().ensureActive()
            if (!updateAnime.awaitAll(listOfNotNull(currentAnimeUpdate, targetAnimeUpdate))) {
                currentCoroutineContext().ensureActive()
                return false
            }

            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                try {
                    downloadManager.deleteAnime(current, currentSource)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e)
                }
            }
            true
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
            false
        }
    }
}
