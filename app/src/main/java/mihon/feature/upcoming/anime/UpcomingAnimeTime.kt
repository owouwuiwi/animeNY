package mihon.feature.upcoming.anime

import kotlin.time.Instant

data class UpcomingAnimeTime(val time: Instant, val episode: Int?, val estimated: Boolean)

internal fun upcomingAnimeTime(
    startOfDay: Instant,
    trackedAiringAt: Long,
    trackedEpisode: Int,
    estimatedUpdate: Instant?,
): UpcomingAnimeTime? {
    if (trackedAiringAt > 0L) {
        val trackedTime = Instant.fromEpochSeconds(trackedAiringAt)
        if (trackedTime >= startOfDay) {
            return UpcomingAnimeTime(trackedTime, trackedEpisode.takeIf { it > 0 }, estimated = false)
        }
    }
    return estimatedUpdate
        ?.takeIf { it >= startOfDay }
        ?.let { UpcomingAnimeTime(it, episode = null, estimated = true) }
}
