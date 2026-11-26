package eu.kanade.domain.track.repeat

import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALRepeatItem
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepeatSessionTest {
    private val target = RepeatTarget(2L, 4L, 6L, "test-user", 3)
    private val key = RepeatKey(RepeatKind.Anime, 8L, 10L, "/example")
    private val fresh = RepeatSession("cycle", key, listOf(target))

    @Test
    fun `opening a later episode cannot start an armed repeat`() {
        assertEquals(fresh, fresh.record(2, partCompleted = true))
        assertFalse(fresh.requiresSync())
        val started = fresh.record(1, partCompleted = false)
        assertEquals(emptySet<Int>(), started.completed)
        assertTrue(started.requiresSync())
    }

    @Test
    fun `the repeat needs each part rather than a jump to the finale`() {
        val partial = fresh.record(1, partCompleted = true)
            .record(3, partCompleted = true)
            .record(3, partCompleted = true)
        assertFalse(partial.isComplete(3))
        assertEquals(2, partial.nextPart())
        assertTrue(partial.record(2, partCompleted = true).isComplete(3))
        assertNull(1.5.toRepeatPart())
        assertNull(Double.NaN.toRepeatPart())
    }

    @Test
    fun `an acknowledged count is not incremented on replay`() {
        val pending = target.copy(targetCount = 4, startConfirmed = true)
        val original = fresh.copy(targets = listOf(pending))
        val afterRestart = Json.decodeFromString<RepeatSession>(Json.encodeToString(original)).targets.single()
        assertEquals(RepeatAction.Count, repeatAction(afterRestart, remote(RepeatStatus.Repeating, 3), complete = true))
        assertEquals(
            RepeatAction.Forget,
            repeatAction(afterRestart, remote(RepeatStatus.Completed, 4), complete = true),
        )
        assertEquals(
            RepeatAction.Forget,
            repeatAction(afterRestart, remote(RepeatStatus.Completed, 5), complete = true),
        )
    }

    @Test
    fun `remote manual changes are respected even for a completed current cycle`() {
        val pending = target.copy(targetCount = 4, startConfirmed = true)
        assertEquals(RepeatAction.Forget, repeatAction(pending, remote(RepeatStatus.Repeating, 2), complete = true))
        assertEquals(RepeatAction.Forget, repeatAction(pending, remote(RepeatStatus.Completed, 3), complete = true))
        assertEquals(RepeatAction.Forget, repeatAction(pending, remote(RepeatStatus.Other, 3), complete = true))
    }

    @Test
    fun `MAL serves repeat count and flags independently of completed status`() {
        val json = Json { ignoreUnknownKeys = true }
        val entry = json.decodeFromString<MALRepeatItem>(
            """{"my_list_status":{"is_rewatching":true,"status":"completed","num_times_rewatched":2,"score":9}}""",
        )
        val status = requireNotNull(entry.myListStatus)
        assertEquals(RemoteRepeat(6L, RepeatStatus.Repeating, 2), status.toRemoteRepeat(RepeatKind.Anime, 6L))
        assertNull(status.toRemoteRepeat(RepeatKind.Manga, 6L).count)
    }

    private fun remote(status: RepeatStatus, count: Int) = RemoteRepeat(6L, status, count)
}
