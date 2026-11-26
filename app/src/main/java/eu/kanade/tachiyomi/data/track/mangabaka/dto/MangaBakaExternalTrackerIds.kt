package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MangaBakaExternalTrackerResult(val data: MangaBakaExternalTrackerLinks)

@Serializable
internal data class MangaBakaExternalTrackerLinks(val source: MangaBakaExternalTrackerIds? = null)

@Serializable
internal data class MangaBakaExternalTrackerIds(
    @SerialName("anilist")
    val aniList: NumericId? = null,
    @SerialName("my_anime_list")
    val myAnimeList: NumericId? = null,
    val kitsu: NumericId? = null,
    val shikimori: NumericId? = null,
    @SerialName("manga_updates")
    val mangaUpdates: MangaUpdatesId? = null,
) {
    @Serializable
    data class NumericId(val id: Long? = null)

    @Serializable
    data class MangaUpdatesId(val id: String? = null) {
        fun decimalId(): Long? = id?.toLongOrNull(radix = 36)
    }
}
