package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryParentChange
import tachiyomi.domain.category.model.CategoryTree

@Serializable
data class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var flags: Long = 0,
    // An empty name denotes a root. A missing field leaves the hierarchy from an older backup unspecified.
    @ProtoNumber(101) var parentName: String? = null,
    @ProtoNumber(102) var parentUpdatedAt: Long = 0,
)

internal fun List<Category>.toCategoryBackup(selectedIds: Set<Long>?): List<BackupCategory> {
    val userCategories = filterNot(Category::isSystemCategory)
    val tree = CategoryTree(userCategories)
    val selectedWithAncestors = selectedIds?.flatMapTo(HashSet()) { id -> tree.pathTo(id).map(Category::id) }
    val categoryById = userCategories.associateBy(Category::id)
    return userCategories
        .filter { selectedWithAncestors == null || it.id in selectedWithAncestors }
        .map { category ->
            BackupCategory(
                id = category.id,
                name = category.name,
                order = category.order,
                flags = category.flags,
                parentName = category.parentId?.let { categoryById[it]?.name }.orEmpty(),
                parentUpdatedAt = category.parentUpdatedAt,
            )
        }
}

internal fun List<BackupCategory>.resolveCategoryParents(categories: List<Category>): Map<Long, CategoryParentChange> {
    val idsByName = categories.filterNot(Category::isSystemCategory).associate { it.name to it.id }
    return buildMap {
        for (category in this@resolveCategoryParents) {
            val categoryId = idsByName[category.name] ?: continue
            val parentName = category.parentName ?: continue
            val parentId = if (parentName.isEmpty()) null else idsByName[parentName] ?: continue
            put(categoryId, CategoryParentChange(parentId, category.parentUpdatedAt))
        }
    }
}

internal fun mergeCategoryBackups(local: List<BackupCategory>?, remote: List<BackupCategory>?): List<BackupCategory> {
    if (local == null || remote == null) return local ?: remote.orEmpty()

    val merged = local.associateBy(BackupCategory::name).toMutableMap()
    remote.associateBy(BackupCategory::name).forEach { (name, remoteCategory) ->
        val localCategory = merged[name]
        merged[name] = if (localCategory == null) {
            remoteCategory
        } else {
            val preferred = if (localCategory.order > remoteCategory.order) localCategory else remoteCategory
            val hierarchy = when {
                remoteCategory.parentName == null -> localCategory
                localCategory.parentName == null -> remoteCategory
                localCategory.parentUpdatedAt > remoteCategory.parentUpdatedAt -> localCategory
                else -> remoteCategory
            }
            preferred.copy(parentName = hierarchy.parentName, parentUpdatedAt = hierarchy.parentUpdatedAt)
        }
    }
    return merged.values.toList()
}
