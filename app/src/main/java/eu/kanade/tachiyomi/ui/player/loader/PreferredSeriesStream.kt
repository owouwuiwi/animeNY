package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A release's URL and position in a source list normally change between episodes.
 * Keep only the selection data that can be meaningfully compared across episodes.
 */
@Serializable
internal data class PreferredSeriesStream(
    val hosterName: String,
    private val title: String,
    private val releaseGroup: String?,
    private val height: Int?,
) {
    fun matchesHoster(hoster: Hoster): Boolean = hosterName.equals(hoster.hosterName, ignoreCase = true)

    fun selectVideo(videos: List<IndexedValue<Video>>): Int? {
        videos.singleOrNull { (_, video) -> title.equals(video.videoTitle.trim(), ignoreCase = true) }
            ?.let { return it.index }

        // Exact labels like "1080p" work across episodes as-is. Torrent release labels
        // include the episode name, so use the release group with the same resolution.
        if (releaseGroup == null || height == null) return null
        return videos.singleOrNull { (_, video) ->
            releaseGroup.equals(releaseGroupOf(video.videoTitle), ignoreCase = true) && height == heightOf(video)
        }?.index
    }

    fun encode(): String = Json.encodeToString(this)

    companion object {
        private val groupPattern = Regex("""^\s*\[([^]]+)]""")

        fun from(hosterName: String, video: Video): PreferredSeriesStream? {
            val title = video.videoTitle.trim().takeIf { it.isNotEmpty() } ?: return null
            return PreferredSeriesStream(hosterName, title, releaseGroupOf(title), heightOf(video))
        }

        fun decode(value: String): PreferredSeriesStream? =
            runCatching { Json.decodeFromString<PreferredSeriesStream>(value) }.getOrNull()

        private fun releaseGroupOf(title: String): String? = groupPattern.find(title)?.groupValues?.get(1)?.trim()

        private fun heightOf(video: Video): Int? = videoQualityHeight(video.videoTitle) ?: video.resolution
    }
}
