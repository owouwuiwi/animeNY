package eu.kanade.tachiyomi.data.backup.create

import tachiyomi.domain.category.model.Category

data class BackupCategoryFilter(
    val animeIds: Set<Long> = emptySet(),
    val mangaIds: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean
        get() = animeIds.isEmpty() && mangaIds.isEmpty()
}

internal fun Set<Long>.includesBackupCategory(categories: List<Category>): Boolean =
    if (categories.isEmpty()) {
        Category.UNCATEGORIZED_ID in this
    } else {
        categories.any { it.id in this }
    }
