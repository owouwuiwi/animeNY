package tachiyomi.domain.items.episode.service

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

data class SeasonalEpisodeNumber(val season: Int, val episode: Int) : Comparable<SeasonalEpisodeNumber> {
    override fun compareTo(other: SeasonalEpisodeNumber): Int {
        return compareValuesBy(this, other, SeasonalEpisodeNumber::season, SeasonalEpisodeNumber::episode)
    }

    override fun toString(): String = "$season.$episode"

    companion object {
        private val explicit = Regex(
            """\b(?:s|season)\s*(\d+)\s*[-_]?\s*(?:e|episode)\s*(\d+)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val decimal = Regex("""(?<![\d.])(\d+)\.(\d+)(?![\d.])""")

        fun parse(name: String): SeasonalEpisodeNumber? {
            val match = explicit.find(name) ?: decimal.findAll(name).singleOrNull() ?: return null
            val season = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val episode = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return SeasonalEpisodeNumber(season, episode)
        }
    }
}

fun Anime.seasonalEpisodeNumber(name: String): SeasonalEpisodeNumber? {
    return if (usesSeasonalEpisodeNumbers) SeasonalEpisodeNumber.parse(name) else null
}

/**
 * Translates source numbering to absolute progress. Earlier seasons are usable as offsets only
 * when their episodes are contiguous from 1. Missing earlier seasons leave later items unmapped.
 */
fun episodeTrackingNumbers(anime: Anime, episodes: List<Episode>): Map<Long, Double> {
    if (!anime.usesSeasonalEpisodeNumbers) return episodes.associate { it.id to it.episodeNumber }
    val numbered = episodes.mapNotNull { episode ->
        SeasonalEpisodeNumber.parse(episode.name)?.let { episode.id to it }
    }
    val episodesBySeason = numbered.groupBy({ it.second.season }, { it.second.episode }).toSortedMap()
    val offsets = mutableMapOf<Int, Long>()
    var expectedSeason = 1
    var offset = 0L
    for ((season, members) in episodesBySeason) {
        if (season != expectedSeason) break
        offsets[season] = offset
        val unique = members.toSet()
        val last = unique.max()
        if (unique.size != last) break
        offset += last
        expectedSeason++
    }
    return numbered.mapNotNull { (id, number) ->
        offsets[number.season]?.let { id to (it + number.episode).toDouble() }
    }.toMap()
}
