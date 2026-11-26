package eu.kanade.tachiyomi.data.download.anime

import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.SeasonalEpisodeNumber
import java.util.Locale

internal fun findImportEpisodes(
    fileNames: List<String>,
    episodes: List<Episode>,
    seasonal: Boolean,
): List<Episode?> {
    val matches = fileNames.map { name ->
        if (name.importVideoExtension() == null) return@map null

        val stem = name.substringBeforeLast('.').trim()
        val exact = episodes.filter { it.displayTitle.equals(stem, ignoreCase = true) || it.name.equals(stem, true) }
        if (exact.isNotEmpty()) return@map exact.singleOrNull()

        val stripped = decorations.replace(stem, "").trim()
        val normalized = versionSuffix.replace(stripped.replace('_', ' '), "")
        val seasons = explicitSeason.findAll(normalized).take(2).count()
        if (seasonal || seasons != 0) {
            if (seasons > 1) return@map null
            val parsed = SeasonalEpisodeNumber.parse(normalized) ?: return@map null
            return@map episodes.singleOrNull { SeasonalEpisodeNumber.parse(it.name) == parsed }
        }

        val labelled = labelledEpisode.findAll(stripped).take(2).toList()
        val match = if (labelled.isEmpty()) bareOrTrailingEpisode.find(stripped) else labelled.singleOrNull()
        val number = match?.groupValues?.get(1)?.toDoubleOrNull() ?: return@map null
        episodes.singleOrNull { it.episodeNumber == number }
    }
    val matchingCounts = matches.filterNotNull().groupingBy { it.id }.eachCount()
    return matches.map { it?.takeIf { episode -> matchingCounts[episode.id] == 1 } }
}

internal fun String.importVideoExtension(): String? {
    return substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it == "mp4" || it == "mkv" }
}

private val decorations = Regex("""\[[^\]]*]|\([^)]*\)""")
private val explicitSeason = Regex("""\b(?:s|season)\s*\d+\s*[-_]?\s*(?:e|episode)\s*\d+""", RegexOption.IGNORE_CASE)
private val versionSuffix = Regex("""(?<=\d)v\d+\b""", RegexOption.IGNORE_CASE)
private const val EPISODE_NUMBER = """(\d+(?:\.\d+)?)(?:v\d+)?"""
private val labelledEpisode = Regex(
    """(?<![a-z0-9])(?:episodes?|ep|e)[ ._-]*$EPISODE_NUMBER(?![a-z0-9.])""",
    RegexOption.IGNORE_CASE,
)
private val bareOrTrailingEpisode = Regex("""(?:^|\s+-\s+)$EPISODE_NUMBER$""", RegexOption.IGNORE_CASE)
