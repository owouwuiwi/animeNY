package mihon.feature.upcoming.anime

import kotlinx.datetime.LocalDate
import tachiyomi.domain.entries.anime.model.Anime

sealed interface UpcomingAnimeUIModel {
    data class Header(val date: LocalDate, val animeCount: Int) : UpcomingAnimeUIModel
    data class Item(val anime: Anime, val schedule: UpcomingAnimeTime) : UpcomingAnimeUIModel

    val key: String
        get() = when (this) {
            is Header -> "date-$date"
            is Item -> "anime-${anime.id}"
        }
}
