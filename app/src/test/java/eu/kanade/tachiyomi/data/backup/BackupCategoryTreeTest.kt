package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.mergeCategoryBackups
import eu.kanade.tachiyomi.data.backup.models.resolveCategoryParents
import eu.kanade.tachiyomi.data.backup.models.toCategoryBackup
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryParentChange

class BackupCategoryTreeTest {
    @Test
    fun `a partial category backup includes its ancestors and reconnects them by name on restore`() {
        val backup = listOf(
            category(1, "Parent"),
            category(2, "Unrelated"),
            category(3, "Child", parentId = 1, changedAt = 100),
        ).toCategoryBackup(setOf(3))

        assertEquals(listOf("Parent", "Child"), backup.map(BackupCategory::name))
        assertEquals(
            mapOf(21L to CategoryParentChange(null, 0), 15L to CategoryParentChange(21, 100)),
            backup.resolveCategoryParents(listOf(category(15, "Child"), category(21, "Parent"))),
        )
    }

    @Test
    fun `protobuf readers preserve flat backups and skip new hierarchy fields`() {
        val legacy = LegacyCategory("Category", 1)
        val legacyBytes = ProtoBuf.encodeToByteArray(LegacyCategory.serializer(), legacy)
        assertNull(ProtoBuf.decodeFromByteArray(BackupCategory.serializer(), legacyBytes).parentName)

        val current = BackupCategory(legacy.name, legacy.order, parentName = "Parent", parentUpdatedAt = 1)
        val currentBytes = ProtoBuf.encodeToByteArray(BackupCategory.serializer(), current)
        assertEquals(legacy, ProtoBuf.decodeFromByteArray(LegacyCategory.serializer(), currentBytes))
        assertEquals(current, ProtoBuf.decodeFromByteArray(BackupCategory.serializer(), currentBytes))
    }

    @Test
    fun `sync chooses the latest move even when category order or a legacy client differs`() {
        val previous = BackupCategory("Child", order = 10, parentName = "Parent", parentUpdatedAt = 20)
        val moved = BackupCategory("Child", order = 1, parentName = "", parentUpdatedAt = 21)
        val legacy = BackupCategory("Child", order = 15)

        val update = mergeCategoryBackups(listOf(moved), listOf(previous)).single()
        assertEquals(previous.order, update.order)
        assertEquals("", update.parentName)
        assertEquals(21L, update.parentUpdatedAt)
        assertEquals("", mergeCategoryBackups(listOf(update), listOf(legacy)).single().parentName)
    }

    private fun category(id: Long, name: String, parentId: Long? = null, changedAt: Long = 0) =
        Category(id, name, id, 0, false, parentId, changedAt)

    @Serializable
    private data class LegacyCategory(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val order: Long = 0,
    )
}
