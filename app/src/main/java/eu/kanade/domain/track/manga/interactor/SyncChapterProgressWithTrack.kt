package eu.kanade.domain.track.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import kotlin.math.max

@Inject
class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertMangaTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackPreferences: TrackPreferences,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: MangaTrack,
        tracker: MangaTracker,
    ) {
        val supportsEnhancedSync = tracker is EnhancedMangaTracker
        if (!supportsEnhancedSync && !trackPreferences.importTrackerProgress.get()) {
            return
        }
        val remoteProgress = remoteTrack.lastChapterRead
        if (!remoteProgress.isFinite() || (!supportsEnhancedSync && remoteProgress <= 0)) return

        val sortedChapters = getChaptersByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteProgress && !chapter.read }
            .map { ChapterUpdate(it.id) { read = true } }

        if (!supportsEnhancedSync) {
            if (chapterUpdates.isNotEmpty()) updateChapter.awaitAll(chapterUpdates)
            return
        }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val lastRead = max(remoteProgress, localLastRead.toDouble())
        val updatedTrack = remoteTrack.copy(lastChapterRead = lastRead)

        try {
            tracker.update(updatedTrack.toDbTrack())
            updateChapter.awaitAll(chapterUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e)
        }
    }
}
