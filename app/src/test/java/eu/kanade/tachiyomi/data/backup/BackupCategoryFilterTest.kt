package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.includesBackupCategory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

class BackupCategoryFilterTest {
    @Test
    fun `a title can match any of its assigned categories`() {
        assertTrue(setOf(3L).includesBackupCategory(listOf(category(2), category(3))))
        assertFalse(setOf(4L).includesBackupCategory(listOf(category(2), category(3))))
    }

    @Test
    fun `only uncategorized titles match an empty category membership by default`() {
        val selected = setOf(Category.UNCATEGORIZED_ID)
        assertTrue(selected.includesBackupCategory(emptyList()))
        assertFalse(selected.includesBackupCategory(listOf(category(2))))
        assertFalse(emptySet<Long>().includesBackupCategory(emptyList()))
    }

    private fun category(id: Long) = Category(id = id, name = "Category $id", order = id, flags = 0, hidden = false)
}
