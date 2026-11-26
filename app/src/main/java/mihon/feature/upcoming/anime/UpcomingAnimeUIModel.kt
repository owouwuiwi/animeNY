package mihon.feature.upcoming.anime

import tachiyomi.domain.entries.anime.model.Anime

data class UpcomingAnimeUIModel(val anime: Anime, val schedule: UpcomingAnimeTime)
