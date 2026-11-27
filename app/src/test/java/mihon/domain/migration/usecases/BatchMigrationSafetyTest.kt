package mihon.domain.migration.usecases

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.items.episode.repository.EpisodeRepository

class BatchMigrationSafetyTest {
    private val preferences = SourcePreferences(InMemoryPreferenceStore()).apply {
        migrationFlags.set(MigrationFlag.REMOVE_DOWNLOAD.flag)
    }

    private val currentAnime = mockk<Anime>(relaxed = true) {
        every { id } returns 1L
        every { source } returns 10L
        every { favorite } returns true
    }
    private val targetAnime = Anime.create().copy(id = 2, source = 11)
    private val currentAnimeSource = mockk<AnimeSource>()
    private val targetAnimeSource = mockk<AnimeSource>()
    private val animeDownloads = mockk<AnimeDownloadManager>()
    private val syncEpisodes = mockk<SyncEpisodesWithSource>()
    private val updateAnime = mockk<UpdateAnime>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val migrateAnime = MigrateAnimeUseCase(
        sourcePreferences = preferences,
        trackerManager = mockk(),
        sourceManager = mockk {
            every { get(10L) } returns currentAnimeSource
            every { get(targetAnime.source) } returns targetAnimeSource
        },
        downloadManager = animeDownloads,
        updateAnime = updateAnime,
        episodeRepository = episodeRepository,
        syncEpisodesWithSource = syncEpisodes,
        getCategories = mockk(),
        animeRepository = mockk(),
        trackRepository = mockk {
            coEvery { getTracksByAnimeId(1L) } returns emptyList()
        },
        migrateEntryTags = mockk(),
        coverCache = mockk(),
    )

    private val currentManga = mockk<Manga>(relaxed = true) {
        every { id } returns 3L
        every { source } returns 12L
        every { favoriteAt } returns 100L
    }
    private val targetManga = Manga.create().copy(id = 4, source = 13)
    private val currentMangaSource = mockk<MangaSource>()
    private val targetMangaSource = mockk<MangaSource> {
        coEvery { getChapterList(any()) } returns listOf(SChapter.create())
    }
    private val mangaDownloads = mockk<MangaDownloadManager>()
    private val updateManga = mockk<UpdateManga>()
    private val mangaRepository = mockk<MangaRepository>()
    private val getMangaCategories = mockk<GetMangaCategories>()
    private val migrateManga = MigrateMangaUseCase(
        sourcePreferences = preferences,
        trackerManager = mockk(),
        sourceManager = mockk {
            coEvery { get(12L) } returns currentMangaSource
            coEvery { get(targetManga.source) } returns targetMangaSource
        },
        downloadManager = mangaDownloads,
        updateManga = updateManga,
        chapterRepository = mockk(),
        syncChaptersWithSource = mockk {
            coEvery { await(any(), targetManga, targetMangaSource) } returns emptyList()
        },
        getCategories = getMangaCategories,
        mangaRepository = mangaRepository,
        trackRepository = mockk {
            coEvery { getTracksByMangaId(3L) } returns emptyList()
        },
        migrateEntryTags = mockk(),
        coverCache = mockk(),
    )

    @Test
    fun `failed episode request preserves the source favorite and downloads`() = runBlocking {
        coEvery { targetAnimeSource.getEpisodeList(any()) } throws IllegalStateException("Unavailable")

        assertFalse(migrateAnime(currentAnime, targetAnime, replace = true))

        coVerify(exactly = 1) { targetAnimeSource.getEpisodeList(any()) }
        coVerify(exactly = 0) { syncEpisodes.await(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { updateAnime.awaitAll(any()) }
        verify(exactly = 0) { animeDownloads.deleteAnime(any(), any(), any()) }
    }

    @Test
    fun `empty episode list rejected by sync preserves the source favorite and downloads`() = runBlocking {
        coEvery { targetAnimeSource.getEpisodeList(any()) } returns emptyList()
        coEvery { syncEpisodes.await(emptyList(), targetAnime, targetAnimeSource) } throws NoEpisodesException()

        assertFalse(migrateAnime(currentAnime, targetAnime, replace = true))

        coVerify(exactly = 1) { syncEpisodes.await(emptyList(), targetAnime, targetAnimeSource) }
        coVerify(exactly = 0) { updateAnime.awaitAll(any()) }
        verify(exactly = 0) { animeDownloads.deleteAnime(any(), any(), any()) }
    }

    @Test
    fun `failed episode progress write preserves the source favorite and downloads`() = runBlocking {
        preferences.migrationFlags.set(MigrationFlag.CHAPTER.flag or MigrationFlag.REMOVE_DOWNLOAD.flag)
        val previous = Episode.create().copy(id = 1, episodeNumber = 1.0, seen = true)
        val destination = Episode.create().copy(id = 2, episodeNumber = 1.0)
        coEvery { targetAnimeSource.getEpisodeList(any()) } returns emptyList()
        coEvery { syncEpisodes.await(emptyList(), targetAnime, targetAnimeSource) } returns emptyList()
        coEvery { episodeRepository.getEpisodeByAnimeId(1L) } returns listOf(previous)
        coEvery { episodeRepository.getEpisodeByAnimeId(targetAnime.id) } returns listOf(destination)
        coEvery { episodeRepository.updateAllEpisodes(any()) } throws IllegalStateException("Write failed")

        assertFalse(migrateAnime(currentAnime, targetAnime, replace = true))

        coVerify(exactly = 1) {
            episodeRepository.updateAllEpisodes(match { it.single().id == destination.id && it.single().seen == true })
        }
        coVerify(exactly = 0) { updateAnime.awaitAll(any()) }
        verify(exactly = 0) { animeDownloads.deleteAnime(any(), any(), any()) }
    }

    @Test
    fun `failed category write preserves the source until a successful retry`() = runBlocking {
        preferences.migrationFlags.set(MigrationFlag.CATEGORY.flag or MigrationFlag.REMOVE_DOWNLOAD.flag)
        val category = Category(id = 5, name = "Reading", order = 0, flags = 0, hidden = false)
        val categoryIds = listOf(category.id)
        coEvery { getMangaCategories.await(3L) } returns listOf(category)
        coEvery { mangaRepository.setMangaCategories(targetManga.id, categoryIds) } throws IllegalStateException("Write failed")

        assertFalse(migrateManga(currentManga, targetManga, replace = true))
        coVerify(exactly = 0) { updateManga.awaitAll(any()) }
        verify(exactly = 0) { mangaDownloads.deleteManga(any(), any(), any()) }

        coEvery { mangaRepository.setMangaCategories(targetManga.id, categoryIds) } returns Unit
        coEvery { updateManga.awaitAll(any()) } returns true
        every { mangaDownloads.deleteManga(currentManga, currentMangaSource) } returns Unit

        assertTrue(migrateManga(currentManga, targetManga, replace = true))
        coVerify(exactly = 2) { mangaRepository.setMangaCategories(targetManga.id, categoryIds) }
        coVerify(exactly = 1) { updateManga.awaitAll(any()) }
        verify(exactly = 1) { mangaDownloads.deleteManga(currentManga, currentMangaSource) }
    }

    @Test
    fun `cancellation during a category write is propagated without a favorite switch`() = runBlocking {
        preferences.migrationFlags.set(MigrationFlag.CATEGORY.flag or MigrationFlag.REMOVE_DOWNLOAD.flag)
        val cancellation = CancellationException()
        coEvery { getMangaCategories.await(3L) } returns emptyList()
        coEvery { mangaRepository.setMangaCategories(targetManga.id, emptyList()) } throws cancellation

        val failure = runCatching { migrateManga(currentManga, targetManga, replace = true) }.exceptionOrNull()

        assertSame(cancellation, failure)
        coVerify(exactly = 0) { updateManga.awaitAll(any()) }
        verify(exactly = 0) { mangaDownloads.deleteManga(any(), any(), any()) }
    }

    @Test
    fun `failed manga favorite transaction leaves source downloads available`() = runBlocking {
        coEvery { updateManga.awaitAll(any()) } returns false

        assertFalse(migrateManga(currentManga, targetManga, replace = true))

        coVerify(exactly = 1) { updateManga.awaitAll(any()) }
        verify(exactly = 0) { mangaDownloads.deleteManga(any(), any(), any()) }
    }

    @Test
    fun `successful manga favorite transaction precedes requested download cleanup`() = runBlocking {
        coEvery { updateManga.awaitAll(any()) } returns true
        every { mangaDownloads.deleteManga(currentManga, currentMangaSource) } returns Unit

        assertTrue(migrateManga(currentManga, targetManga, replace = true))

        coVerifyOrder {
            updateManga.awaitAll(
                match { it.map { update -> update.id to update.favoriteAt } == listOf(3L to null, 4L to 100L) },
            )
            mangaDownloads.deleteManga(currentManga, currentMangaSource)
        }
    }
}
