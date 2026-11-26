package eu.kanade.tachiyomi.data.download.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.episode.model.Episode

class EpisodeDownloadReplacementTest {
    @Test
    fun `An episode that keeps its identity receives the old download`() {
        val old = episode(url = "/old")
        val new = old.copy(url = "/new")
        val other = episode(url = "/other", name = "Episode 2", number = 2.0)

        matchEpisodeDownloadReplacements(listOf(old, other), listOf(new, other)) shouldBe listOf(old to new)
    }

    @Test
    fun `An existing URL or ambiguous metadata never moves the download`() {
        val old = episode(url = "/old")
        val new = old.copy(url = "/new")
        val second = old.copy(url = "/second")

        matchEpisodeDownloadReplacements(listOf(old), listOf(old, new)) shouldBe emptyList()
        matchEpisodeDownloadReplacements(listOf(old, second), listOf(new)) shouldBe emptyList()
        matchEpisodeDownloadReplacements(listOf(old), listOf(new, second)) shouldBe emptyList()
    }

    @Test
    fun `Names and release groups distinguish entries with the same number`() {
        val old = episode(url = "/old", scanlator = "Group A")
        val new = old.copy(url = "/new")
        val differentGroup = new.copy(url = "/group", scanlator = "Group B")
        val differentName = new.copy(url = "/special", name = "Special")
        val unknownNumber = new.copy(url = "/bonus", episodeNumber = -1.0)
        val oldUnknown = unknownNumber.copy(url = "/old-bonus")

        matchEpisodeDownloadReplacements(
            listOf(old, oldUnknown),
            listOf(new, differentGroup, differentName, unknownNumber),
        ) shouldBe listOf(old to new)
    }

    private fun episode(
        url: String,
        name: String = "Episode 1",
        number: Double = 1.0,
        scanlator: String? = null,
    ): Episode = Episode.create().copy(
        animeId = 42,
        url = url,
        name = name,
        episodeNumber = number,
        scanlator = scanlator,
    )
}
