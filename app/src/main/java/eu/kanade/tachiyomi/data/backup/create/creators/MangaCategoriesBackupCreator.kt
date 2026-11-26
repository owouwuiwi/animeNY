package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.toCategoryBackup
import tachiyomi.domain.category.manga.interactor.GetMangaCategories

@Inject
class MangaCategoriesBackupCreator(
    private val getMangaCategories: GetMangaCategories,
) {

    suspend operator fun invoke(selectedCategoryIds: Set<Long>? = null): List<BackupCategory> {
        return getMangaCategories.await().toCategoryBackup(selectedCategoryIds)
    }
}
