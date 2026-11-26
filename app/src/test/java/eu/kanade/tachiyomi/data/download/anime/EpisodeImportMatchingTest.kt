package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.episode.model.Episode

class EpisodeImportMatchingTest {
    @Test
    fun `matches the source name and conservatively accepts explicit episode numbers`() {
        val episodes = listOf(episode(1, "Pilot", 1.0), episode(7, "Episode 7", 7.0))

        assertEquals(
            listOf(1L, 7L, null, null),
            findImportEpisodes(
                listOf("PILOT.MP4", "[Group] Title - 07v2 [1080p].mkv", "Title 1 1080p.mkv", "Episode 1.exe"),
                episodes,
                seasonal = false,
            ).map { it?.id },
        )
    }

    @Test
    fun `does not guess between groups or between files for the same episode`() {
        val episodes = listOf(episode(1, "Raw", 1.0), episode(11, "Dub", 1.0), episode(2, "Part II", 2.0))

        assertEquals(
            listOf(null, null, null, null),
            findImportEpisodes(
                listOf("Episode 1.mp4", "Episode 2.mkv", "Part II.mp4", "Episode 1 Episode 2 - 02.mkv"),
                episodes,
                seasonal = false,
            ).map { it?.id },
        )
    }

    @Test
    fun `seasonal imports distinguish episode one from episode ten`() {
        val episodes = listOf(episode(1, "1.1", 1.1), episode(10, "1.10", 1.1))

        assertEquals(
            listOf(1L, 10L, null),
            findImportEpisodes(
                listOf("Title_S01E01v2.mkv", "Title - 1.10.mp4", "S01E01 S01E10.mkv"),
                episodes,
                seasonal = true,
            ).map { it?.id },
        )
    }

    private fun episode(id: Long, name: String, number: Double): Episode =
        Episode.create().copy(id = id, name = name, episodeNumber = number)
}
