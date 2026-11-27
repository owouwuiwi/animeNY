package eu.kanade.domain.track.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.manga.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTrackProgressReader
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

@Inject
class RefreshMangaTracks(
    private val getTracks: GetMangaTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertMangaTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val trackPreferences: TrackPreferences,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     *
     * @return Failed updates.
     */
    suspend fun await(mangaId: Long, standardProgressOnly: Boolean = false): List<Pair<Tracker?, Throwable>> {
        if (standardProgressOnly && !trackPreferences.importTrackerProgress.get()) return emptyList()

        return supervisorScope {
            return@supervisorScope getTracks.await(mangaId)
                .mapNotNull { track ->
                    trackerManager.get(track.trackerId)?.takeIf { it.isLoggedIn }?.let { track to it }
                }
                .map { (track, service) ->
                    async {
                        return@async try {
                            val refreshedTrack = if (standardProgressOnly) {
                                if (service is EnhancedMangaTracker) return@async null
                                (service as? MangaTrackProgressReader)?.fetchTrackProgress(track.toDbTrack())
                                    ?: return@async null
                            } else {
                                service.mangaService.refresh(track.toDbTrack())
                            }
                            currentCoroutineContext().ensureActive()
                            val updatedTrack = refreshedTrack.toDomainTrack()!!
                            insertTrack.await(updatedTrack)
                            syncChapterProgressWithTrack.await(mangaId, updatedTrack, service.mangaService)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }
}
