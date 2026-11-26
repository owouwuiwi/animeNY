package eu.kanade.tachiyomi.data.track.simkl

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimklEpisodeHistoryChangeTest {
    @Test
    fun `finishing a show never removes previously watched episodes`() {
        val change = SimklEpisodeHistoryChange.fromProgress(12.0, previousEditedProgress = null)

        assertEquals(1..12, change.watched)
        assertTrue(change.unwatched.isEmpty())
    }

    @Test
    fun `an explicit correction removes only the discarded episodes`() {
        val change = SimklEpisodeHistoryChange.fromProgress(9.0, previousEditedProgress = 12.0)

        assertEquals(1..9, change.watched)
        assertEquals(10..12, change.unwatched)
        assertEquals(
            Json.parseToJsonElement(
                """{"shows":[{"ids":{"simkl":42},"seasons":[{"number":1,"episodes":[{"number":10},{"number":11},{"number":12}]}]}]}""",
            ),
            simklEpisodeHistoryPayload(42, change.unwatched),
        )
    }

    @Test
    fun `an empty update cannot expand to the whole season`() {
        val change = SimklEpisodeHistoryChange.fromProgress(0.0, previousEditedProgress = null)

        assertTrue(change.watched.isEmpty())
        assertTrue(change.unwatched.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            simklEpisodeHistoryPayload(42, change.watched)
        }
    }
}
