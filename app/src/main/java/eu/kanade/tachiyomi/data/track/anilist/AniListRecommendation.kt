package eu.kanade.tachiyomi.data.track.anilist

internal data class AniListRecommendation(
    val id: Int,
    val title: String,
    val coverUrl: String?,
    val isAdult: Boolean?,
)
