package mihon.domain.migration.usecases

import dev.zacsweers.metro.Inject
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import kotlin.time.Clock

@Inject
class MigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: MangaSourceManager,
    private val downloadManager: MangaDownloadManager,
    private val updateManga: UpdateManga,
    private val chapterRepository: ChapterRepository,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val getCategories: GetMangaCategories,
    private val mangaRepository: MangaRepository,
    private val trackRepository: MangaTrackRepository,
    private val migrateEntryTags: MigrateEntryTags,
    private val coverCache: MangaCoverCache,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedMangaTracker>() }

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean): Boolean {
        if (current.id == target.id) return false

        return try {
            val targetSource = sourceManager.get(target.source) ?: return false
            val currentSource = sourceManager.get(current.source)
            val flags = MigrationFlag.fromBit(sourcePreferences.migrationFlags.get())
            val chapters = targetSource.getChapterList(target.toSManga())
            syncChaptersWithSource.await(chapters, target, targetSource)

            // Update chapters read, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                val prevMangaChapters = chapterRepository.getChapterByMangaId(current.id)
                val mangaChapters = chapterRepository.getChapterByMangaId(target.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }

                val chapterUpdates = mangaChapters
                    .filter { it.isRecognizedNumber }
                    .map { mangaChapter ->
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == mangaChapter.chapterNumber }

                        ChapterUpdate(mangaChapter.id) {
                            if (prevChapter != null) {
                                dateFetch = prevChapter.dateFetch
                                bookmark = prevChapter.bookmark
                                lastPageRead = prevChapter.lastPageRead
                            }

                            if (prevChapter?.read == true ||
                                (maxChapterRead != null && mangaChapter.chapterNumber <= maxChapterRead)
                            ) {
                                read = true
                            }
                        }
                    }
                chapterRepository.updateAllChapters(chapterUpdates)
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                mangaRepository.setMangaCategories(target.id, categoryIds)
            }

            if (MigrationFlag.TAGS in flags) {
                migrateEntryTags.copy(current, target)
            }

            // Update track
            trackRepository.getTracksByMangaId(current.id).mapNotNull { track ->
                val updatedTrack = track.copy(mangaId = target.id)

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
                ?.let { trackRepository.upsertAllManga(it) }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover(coverCache)) {
                coverCache.getCustomCoverFile(current.id).inputStream().use { input ->
                    coverCache.setCustomCoverToCache(target, input)
                }
            }

            val currentMangaUpdate = MangaUpdate(current.id) {
                favoriteAt = null
            }
                .takeIf { replace }
            val targetMangaUpdate = MangaUpdate(target.id) {
                favoriteAt = current.favoriteAt?.takeIf { replace } ?: Clock.System.now().toEpochMilliseconds()
                chapterFlags = current.chapterFlags
                viewerFlags = current.viewerFlags
                if (MigrationFlag.NOTES in flags) notes = current.notes
            }

            currentCoroutineContext().ensureActive()
            if (!updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))) {
                currentCoroutineContext().ensureActive()
                return false
            }

            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                try {
                    downloadManager.deleteManga(current, currentSource)
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
