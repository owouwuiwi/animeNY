package eu.kanade.domain.track.manga.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.manga.model.toDomainTrack
import eu.kanade.domain.track.manga.service.DelayedMangaTrackingUpdateJob
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

@Inject
class TrackChapter(
    private val getTracks: GetMangaTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertMangaTrack,
    private val delayedTrackingStore: DelayedMangaTrackingStore,
) {

    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {
        if (!chapterNumber.isFinite()) return
        withNonCancellableContext {
            val tracks = getTracks.await(mangaId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        try {
                            val remoteTrack = service.mangaService.refresh(track.toDbTrack())
                            val savedTrack = if (chapterNumber > remoteTrack.last_chapter_read) {
                                remoteTrack.last_chapter_read = chapterNumber
                                service.mangaService.update(remoteTrack, didReadChapter = true)
                            } else {
                                remoteTrack
                            }
                            insertTrack.await(checkNotNull(savedTrack.toDomainTrack()))
                            delayedTrackingStore.removeMangaItem(track.id)
                        } catch (e: Exception) {
                            delayedTrackingStore.addManga(track.id, chapterNumber)
                            if (setupJobOnFailure) {
                                DelayedMangaTrackingUpdateJob.setupTask(context)
                            }
                            throw e
                        }
                    }
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }
}
