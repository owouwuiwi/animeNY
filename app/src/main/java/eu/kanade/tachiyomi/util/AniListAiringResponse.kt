package eu.kanade.tachiyomi.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val NO_UPCOMING_EPISODE = 1 to 0L

/** A null result indicates a failed lookup, so callers can retain their cached schedule. */
internal fun Json.parseAniListAiringEpisode(body: String): Pair<Int, Long>? {
    val response = try {
        decodeFromString<AniListAiringResponse>(body)
    } catch (_: SerializationException) {
        return null
    }
    val data = response.data ?: return null
    if (response.errors.isNotEmpty()) return null
    val next = data.media?.nextAiringEpisode ?: return NO_UPCOMING_EPISODE
    return if (next.episode > 0 && next.airingAt > 0L) next.episode to next.airingAt else null
}

@Serializable
private data class AniListAiringResponse(
    val data: MediaData? = null,
    val errors: List<JsonElement> = emptyList(),
) {
    @Serializable
    data class MediaData(@SerialName("Media") val media: Media?)

    @Serializable
    data class Media(val nextAiringEpisode: Episode?)

    @Serializable
    data class Episode(val episode: Int, val airingAt: Long)
}
