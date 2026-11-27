package mihon.feature.migration.list

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MigrationConflictsTest {
    @Test
    fun `holds all origins mapped to the same destination`() {
        val destinations = mapOf(1L to 10L, 2L to 10L, 3L to 11L, 4L to null)

        assertEquals(setOf(1L, 2L), findMigrationConflicts(destinations))
        assertEquals(emptySet<Long>(), findMigrationConflicts(destinations + (2L to 12L)))
    }

    @Test
    fun `holds both sides when a destination is another active origin`() {
        val destinations = mapOf(1L to 2L, 2L to 3L, 3L to null, 4L to 10L)

        assertEquals(setOf(1L, 2L, 3L), findMigrationConflicts(destinations))
    }

    @Test
    fun `holds a self migration without blocking unrelated rows`() {
        assertEquals(setOf(1L), findMigrationConflicts(mapOf(1L to 1L, 2L to 3L)))
    }
}
