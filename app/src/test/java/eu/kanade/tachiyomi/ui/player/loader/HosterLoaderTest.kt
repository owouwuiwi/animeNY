package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.anime.model.StubAnimeSource

class HosterLoaderTest {

    @Test
    fun `waits while an earlier hoster is still loading`() {
        val states = listOf(
            HosterState.Loading("UPNShare"),
            ready("Voe", video("voe-720")),
        )

        assertNull(HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `starts with the first ready hoster once the earlier ones settled`() {
        val states = listOf(
            HosterState.Error("HLS"),
            ready("UPNShare"),
            ready("Voe", video("voe-720")),
            HosterState.Loading("Byse"),
        )

        assertEquals(2 to 0, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `a preferred video wins even if earlier hosters are still loading`() {
        val states = listOf(
            HosterState.Loading("UPNShare"),
            ready("Voe", video("voe-720"), video("voe-1080", preferred = true)),
        )

        assertEquals(1 to 1, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `skips videos that already failed`() {
        val states = listOf(
            ready("Voe", video("voe-1080"), video("voe-720"), states = listOf(Video.State.ERROR, Video.State.QUEUE)),
        )

        assertEquals(0 to 1, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `reports no video once every hoster settled without a playable one`() {
        val states = listOf(
            HosterState.Error("HLS"),
            HosterState.Idle("Mega"),
            ready("Voe", video("voe-720"), states = listOf(Video.State.ERROR)),
        )

        assertEquals(-1 to -1, HosterLoader.selectNextInOrder(states))
    }

    @Test
    fun `automatic downloads try the remembered release before the source default`() = runTest {
        val source = StubAnimeSource(1, "en", "Source")
        val default = video("default", preferred = true)
        val preferred = Video(videoUrl = "magnet:?wanted", videoTitle = "[Group] Series 02 (1080p)")
        val stream = PreferredSeriesStream.from(
            "Torrents",
            Video(videoUrl = "magnet:?previous", videoTitle = "[Group] Series 01 (1080p)"),
        )!!
        val hosters = listOf(
            Hoster(hosterName = "Default", videoList = listOf(default)),
            Hoster(hosterName = "Torrents", videoList = listOf(preferred)),
        )

        val match = HosterLoader.getBestHosterVideo(source, hosters, preferredStream = stream)
        assertEquals(preferred.videoUrl, match?.second?.videoUrl)

        val fallback = HosterLoader.getBestHosterVideo(source, hosters, preferredStream = stream) { _, video ->
            video.videoUrl == preferred.videoUrl
        }
        assertEquals(default.videoUrl, fallback?.second?.videoUrl)
    }

    @Test
    fun `ambiguous and disabled remembered providers keep the original download order`() = runTest {
        val source = StubAnimeSource(1, "en", "Source")
        val default = video("default")
        val preferred = video("wanted")
        val stream = PreferredSeriesStream.from("Torrents", preferred)!!
        val first = Hoster(hosterName = "Default", videoList = listOf(default))
        val wanted = Hoster(hosterName = "Torrents", videoList = listOf(preferred))
        val ambiguous = wanted.copy(videoList = listOf(preferred, preferred.copy(videoUrl = "magnet:?other")))

        for (second in listOf(ambiguous, wanted.copy(lazy = true))) {
            val match = HosterLoader.getBestHosterVideo(source, listOf(first, second), preferredStream = stream)
            assertEquals(default.videoUrl, match?.second?.videoUrl)
        }
    }

    private fun video(url: String, preferred: Boolean = false) =
        Video(videoUrl = "https://cdn.example/$url", videoTitle = url, preferred = preferred)

    private fun ready(
        name: String,
        vararg videos: Video,
        states: List<Video.State> = List(videos.size) { Video.State.QUEUE },
    ) = HosterState.Ready(name, videos.toList(), states)
}
