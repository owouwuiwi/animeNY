package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category

@Inject
class MangaCategoriesBackupCreator(
    private val getMangaCategories: GetMangaCategories,
) {

    suspend operator fun invoke(selectedCategoryIds: Set<Long>? = null): List<BackupCategory> {
        return getMangaCategories.await()
            .filterNot(Category::isSystemCategory)
            .filter { selectedCategoryIds == null || it.id in selectedCategoryIds }
            .map(backupCategoryMapper)
    }
}
