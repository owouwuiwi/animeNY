package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.toCategoryBackup
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories

@Inject
class AnimeCategoriesBackupCreator(
    private val getAnimeCategories: GetAnimeCategories,
) {

    suspend operator fun invoke(selectedCategoryIds: Set<Long>? = null): List<BackupCategory> {
        return getAnimeCategories.await().toCategoryBackup(selectedCategoryIds)
    }
}
