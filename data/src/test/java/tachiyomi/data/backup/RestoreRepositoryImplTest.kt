package tachiyomi.data.backup

import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import data.ChapterQueries
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

class RestoreRepositoryImplTest {
    private val manga = Manga.create().copy(id = 2, url = "/manga")
    private val chapter = Chapter.create().copy(id = 3, mangaId = manga.id, url = "/chapter-1", chapterNumber = 1.0)

    @Test
    fun `shared URL restores the date override from another copy together with reading progress`() = runTest {
        val chapters = listOf(
            chapter.copy(read = true, lastPageRead = 3),
            chapter.copy(bookmark = true, lastPageRead = 8, dateUploadOverride = 1_795_000_000_000),
        )

        val queries = restore(chapter, chapters)

        coVerify(exactly = 1) {
            queries.updateFromBackup(true, true, 8, 1_795_000_000_000, chapter.memo, chapter.id)
        }
        confirmVerified(queries)
    }

    @Test
    fun `old backup still restores reading progress when its date override is missing`() = runTest {
        val local = chapter.copy(dateUploadOverride = 1_795_000_000_000)

        val queries = restore(local, listOf(chapter.copy(read = true)))

        coVerify(exactly = 1) {
            queries.updateFromBackup(true, false, 0, local.dateUploadOverride, chapter.memo, chapter.id)
        }
        confirmVerified(queries)
    }

    @Test
    fun `a date before the epoch is still an override`() = runTest {
        val chapters = listOf(chapter, chapter.copy(dateUploadOverride = -86_400_000))

        val queries = restore(chapter, chapters)

        coVerify(exactly = 1) {
            queries.updateFromBackup(false, false, 0, -86_400_000, chapter.memo, chapter.id)
        }
        confirmVerified(queries)
    }

    private suspend fun restore(localChapter: Chapter, chapters: List<Chapter>): ChapterQueries {
        val queries = mockk<ChapterQueries>(relaxed = true)
        val database = mockk<Database> {
            every { chapterQueries } returns queries
            every { mangaQueries } returns mockk(relaxed = true)
            coEvery { transaction(false, any()) } coAnswers {
                secondArg<suspend SuspendingTransactionWithoutReturn.() -> Unit>().invoke(mockk())
            }
        }
        val mangaRepository = mockk<MangaRepository> {
            coEvery { getMangaByUrlAndSourceId(manga.url, manga.source) } returns manga
            coEvery { update(any()) } returns true
        }
        val chapterRepository = mockk<ChapterRepository> {
            coEvery { getChapterByMangaId(manga.id) } returns listOf(localChapter)
        }
        val tracks = mockk<MangaTrackRepository> {
            coEvery { getTracksByMangaId(manga.id) } returns emptyList()
        }
        val entry = RestoredManga(manga, chapters, emptyList(), emptyList(), emptyList(), emptyList())

        RestoreRepositoryImpl(database, mangaRepository, chapterRepository, tracks).restoreManga(listOf(entry)) {
            MangaUpdate(it.id) {}
        }
        return queries
    }
}
