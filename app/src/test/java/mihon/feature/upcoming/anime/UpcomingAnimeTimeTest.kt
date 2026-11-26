package mihon.feature.upcoming.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class UpcomingAnimeTimeTest {
    private val startOfDay = Instant.parse("2026-11-26T23:00:00Z")
    private val tracked = Instant.parse("2026-11-27T00:30:00Z")
    private val estimate = Instant.parse("2026-11-28T10:00:00Z")

    @Test
    fun `an airing date takes precedence over the estimated release`() {
        assertEquals(
            UpcomingAnimeTime(tracked, episode = 10, estimated = false),
            upcomingAnimeTime(startOfDay, tracked.epochSeconds, 10, estimate),
        )
    }

    @Test
    fun `old or absent tracker data uses a future estimate`() {
        assertEquals(
            UpcomingAnimeTime(estimate, episode = null, estimated = true),
            upcomingAnimeTime(startOfDay, startOfDay.epochSeconds - 1, 10, estimate),
        )
        assertNull(upcomingAnimeTime(startOfDay, 0, 0, null))
    }
}
