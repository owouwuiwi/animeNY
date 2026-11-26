package mihon.domain.migration.usecases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MergeMigrationTagsTest {
    @Test
    fun `keeps destination tags and adds unique source tags`() {
        assertEquals(
            listOf("Action", "Adventure", "Found Family"),
            mergeMigrationTags(listOf(" action ", " Found Family ", "found family", ""), listOf("Action", "Adventure")),
        )
    }

    @Test
    fun `does not create an override for blank or repeated tags`() {
        assertNull(mergeMigrationTags(listOf("  ", "ACTION"), listOf("Action")))
        assertNull(mergeMigrationTags(null, listOf("Action")))
    }
}
