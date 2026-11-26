package tachiyomi.domain.items.episode.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class SeasonalEpisodeNumberTest {
    private val anime = Anime.create().copy(
        episodeFlags = Anime.EPISODE_SEASONAL_NUMBERING_MASK or Anime.EPISODE_SORTING_NUMBER,
    )

    @Test
    fun `Season and episode coordinates retain trailing zeros`() {
        val episodes = listOf(episode(1, "Episode 1.10"), episode(2, "Episode 1.1"), episode(3, "Episode 1.2"))

        episodes.sortedWith(getEpisodeSort(anime, sortDescending = false)).map(Episode::name) shouldBe
            listOf("Episode 1.1", "Episode 1.2", "Episode 1.10")
        SeasonalEpisodeNumber.parse("S02E10")?.toString() shouldBe "2.10"
        SeasonalEpisodeNumber.parse("2026.11.26") shouldBe null
    }

    @Test
    fun `Absolute progress counts previous seasons without double counting release groups`() {
        val episodes = (1..10).map { episode(it.toLong(), "Episode 1.$it") } +
            (1..10).map { episode(10L + it, "Episode 2.$it") } +
            episode(21, "Episode 3.1") +
            episode(22, "Alternative release 1.10")
        val progress = episodeTrackingNumbers(anime, episodes)

        progress[10] shouldBe 10.0
        progress[11] shouldBe 11.0
        progress[21] shouldBe 21.0
        progress[22] shouldBe 10.0
    }

    @Test
    fun `Missing earlier episodes leave later tracking progress unknown`() {
        val episodes = listOf(episode(1, "Episode 1.1"), episode(3, "Episode 1.3"), episode(4, "Episode 2.1"))

        episodeTrackingNumbers(anime, episodes) shouldBe mapOf(1L to 1.0, 3L to 3.0)
    }

    @Test
    fun `Fractional specials still use the original numbering by default`() {
        val special = episode(1, "Episode 12.5")
        val normal = anime.copy(episodeFlags = Anime.EPISODE_SORTING_NUMBER)

        normal.seasonalEpisodeNumber(special.name) shouldBe null
        episodeTrackingNumbers(normal, listOf(special)) shouldBe mapOf(1L to 12.5)
    }

    private fun episode(id: Long, name: String): Episode = Episode.create().copy(
        id = id,
        name = name,
        episodeNumber = name.substringAfterLast(' ').toDouble(),
    )
}
