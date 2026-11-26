package eu.kanade.tachiyomi.data.download.anime

import tachiyomi.domain.items.episode.model.Episode

/**
 * Downloads include the episode URL in their path. A source may replace that URL while keeping
 * the same episode. Only pair identities that occur exactly once on each side of a refresh.
 */
internal fun matchEpisodeDownloadReplacements(
    previous: List<Episode>,
    refreshed: List<Episode>,
): List<Pair<Episode, Episode>> {
    val previousUrls = previous.mapTo(mutableSetOf(), Episode::url)
    val refreshedUrls = refreshed.mapTo(mutableSetOf(), Episode::url)
    val matches = refreshed.groupBy(Episode::downloadIdentity)

    return previous.groupBy(Episode::downloadIdentity).mapNotNull { (identity, originals) ->
        if (identity == null) return@mapNotNull null
        val old = originals.singleOrNull() ?: return@mapNotNull null
        val new = matches[identity]?.singleOrNull() ?: return@mapNotNull null
        (old to new).takeIf { old.url !in refreshedUrls && new.url !in previousUrls }
    }
}

private data class DownloadIdentity(val animeId: Long, val number: Double, val name: String, val scanlator: String)

private fun Episode.downloadIdentity(): DownloadIdentity? {
    if (!isRecognizedNumber || !episodeNumber.isFinite() || name.isBlank()) return null
    return DownloadIdentity(animeId, episodeNumber, name.trim(), scanlator.orEmpty().trim())
}
