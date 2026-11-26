package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PreferredSeriesStreamTest {
    @Test
    fun `Changing the episode does not change the chosen release group or resolution`() {
        val selected = video("[Group A] Series - 01 (1080p)", "magnet:?one")
        val preference = PreferredSeriesStream.from("Torrents", selected)!!
        val candidates = listOf(
            video("[Group B] Series - 02 (1080p)", "magnet:?two"),
            video("[Group A] Series - 02 (720p)", "magnet:?three"),
            video("[Group A] Series - 02 (1080p)", "magnet:?four"),
        ).withIndex().toList()

        preference.matchesHoster(Hoster(hosterName = "torrents")) shouldBe true
        preference.selectVideo(candidates) shouldBe 2
        PreferredSeriesStream.decode(preference.encode()) shouldBe preference
    }

    @Test
    fun `Ambiguous alternatives stay in the source's normal order`() {
        val preference = PreferredSeriesStream.from("Torrents", video("[Group] Series - 01 (1080p)", "first"))!!
        val candidates = listOf(
            video("[Group] Series - 02 (1080p) German", "second"),
            video("[Group] Series - 02 (1080p) English", "third"),
        ).withIndex().toList()

        preference.selectVideo(candidates) shouldBe null
    }

    private fun video(label: String, url: String) = Video(videoUrl = url, videoTitle = label)
}
