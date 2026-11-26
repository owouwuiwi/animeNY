package tachiyomi.domain.category.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoryTreeTest {
    @Test
    fun `descendants are shown directly after their parent in stored sibling order`() {
        val parent = category(1)
        val anotherRoot = category(2)
        val child = category(3, 1)
        val anotherChild = category(4, 1)
        val grandchild = category(5, 3)
        val tree = CategoryTree(listOf(parent, anotherRoot, child, anotherChild, grandchild))

        assertEquals(listOf(parent, child, grandchild, anotherChild, anotherRoot), tree.inHierarchyOrder())
        assertEquals(listOf(parent, child, grandchild), tree.pathTo(grandchild.id))
        assertEquals(listOf(anotherRoot), tree.possibleParents(parent.id))
    }

    @Test
    fun `a filtered out parent leaves its visible descendants accessible`() {
        val root = category(2)
        val child = category(3, 1)
        val grandchild = category(4, 3)
        val tree = CategoryTree(listOf(root, child, grandchild))

        assertEquals(listOf(root, child, grandchild), tree.inHierarchyOrder())
        assertEquals(listOf(child, grandchild), tree.pathTo(grandchild.id))
    }

    @Test
    fun `reordering compares siblings and does not move descendants to a different parent`() {
        val first = category(1)
        val second = category(2)
        val child = category(3, 1)
        val categories = listOf(first, second, child)

        assertEquals(
            listOf(second, first, child),
            CategoryTree(categories.moveCategoryAmongSiblings(first.id, second.id)).inHierarchyOrder(),
        )
        assertEquals(categories, categories.moveCategoryAmongSiblings(child.id, second.id))
    }

    @Test
    fun `parent assignments can reverse a branch atomically`() {
        val parent = category(1)
        val child = category(2, 1)
        val changes = mapOf(
            parent.id to CategoryParentChange(child.id, 5),
            child.id to CategoryParentChange(null, 5),
        )

        assertEquals(changes, CategoryTree(listOf(parent, child)).validParentChanges(changes))
    }

    @Test
    fun `cycles stale edits missing parents and system parents are rejected`() {
        val tree = CategoryTree(listOf(category(0), category(1), category(2, 1, 20), category(3)))

        assertTrue(tree.validParentChanges(mapOf(1L to CategoryParentChange(2, 10))).isEmpty())
        assertTrue(tree.validParentChanges(mapOf(2L to CategoryParentChange(3, 19))).isEmpty())
        assertTrue(tree.validParentChanges(mapOf(3L to CategoryParentChange(0, 10))).isEmpty())
        assertTrue(tree.validParentChanges(mapOf(3L to CategoryParentChange(9, 10))).isEmpty())
        assertEquals(
            mapOf(3L to CategoryParentChange(2, 10)),
            tree.validParentChanges(
                mapOf(1L to CategoryParentChange(9, 10), 3L to CategoryParentChange(2, 10)),
            ),
        )
    }

    private fun category(id: Long, parentId: Long? = null, changedAt: Long = 0) =
        Category(id, "Category $id", id, 0, false, parentId, changedAt)
}
