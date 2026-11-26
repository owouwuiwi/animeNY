package mihon.feature.migration.list

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MigrationSourceCompatibilityTest {
    @Test
    fun `returns no result for an incompatible extension and allows another source`() {
        val incompatible = compatibleMigrationSourceCall(1L) { throw AbstractMethodError() }
        val available = compatibleMigrationSourceCall(2L) { listOf("chapter") }

        assertNull(incompatible)
        assertEquals(listOf("chapter"), available)
    }

    @Test
    fun `propagates migration cancellation`() {
        assertThrows(CancellationException::class.java) {
            compatibleMigrationSourceCall(1L) { throw CancellationException() }
        }
    }
}
