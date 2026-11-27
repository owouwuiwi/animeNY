package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import java.io.IOException

class AnimeDownloadStorageTest {

    @Test
    fun `A folder failure does not prevent the next episode in the same source from starting`() = runBlocking {
        val source = mockk<AnimeHttpSource>()
        val blocked = download(source, 1, "Blocked anime")
        val following = download(source, 2, "Following anime")
        val provider = mockk<AnimeDownloadProvider> {
            every { getAnimeDir(blocked.anime.title, source) } throws IOException("Folder unavailable")
            every { getAnimeDir(following.anime.title, source) } throws IOException("Second folder unavailable")
        }
        val store = mockk<AnimeDownloadStore>(relaxUnitFun = true) {
            coEvery { restore() } returns emptyList()
        }
        val notifier = mockk<AnimeDownloadNotifier>(relaxUnitFun = true)
        val downloader = AnimeDownloader(
            context = mockk(),
            provider = provider,
            cache = mockk(),
            sourceManager = mockk(),
            torrentServerApi = mockk(),
            torrentServerUtils = mockk(),
            torrentPreferences = mockk(),
            store = store,
            notifier = notifier,
            preferences = mockk(),
            playerPreferences = mockk(),
        )

        mockkObject(EpisodeLoader.Companion)
        every { EpisodeLoader.invalidateCache(any<Episode>(), source) } returns Unit
        try {
            downloader.awaitQueueRestored()
            downloader.updateQueue(listOf(blocked, following))
            downloader.start() shouldBe true
            withTimeout(5_000) {
                following.statusFlow.first { it == AnimeDownload.State.ERROR }
            }

            blocked.status shouldBe AnimeDownload.State.ERROR
            verify(exactly = 1) { provider.getAnimeDir(following.anime.title, source) }
        } finally {
            downloader.stop()
            unmockkObject(EpisodeLoader.Companion)
        }
    }

    private fun download(source: AnimeHttpSource, id: Long, title: String) = AnimeDownload(
        source = source,
        anime = Anime.create().copy(id = id, ogTitle = title),
        episode = Episode.create().copy(id = id, animeId = id),
    )
}
