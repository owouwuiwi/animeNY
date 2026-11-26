package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryParentChange
import tachiyomi.domain.category.model.moveCategoryAmongSiblings
import kotlin.time.Clock

@Inject
class ReorderAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val mutex = Mutex()

    suspend fun await(category: Category, sibling: Category) = withNonCancellableContext {
        mutex.withLock {
            val categories = categoryRepository.getAllAnimeCategories()
                .filterNot(Category::isSystemCategory)
            val reordered = categories.moveCategoryAmongSiblings(category.id, sibling.id)
            if (reordered == categories) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                categoryRepository.updateAnimeCategoryAllOrders(orderedIds = reordered.map { it.id })
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    suspend fun moveToParent(category: Category, parentId: Long?) = withNonCancellableContext {
        mutex.withLock {
            try {
                val updatedAt = maxOf(Clock.System.now().toEpochMilliseconds(), category.parentUpdatedAt + 1)
                categoryRepository.updateAnimeCategoryParents(
                    mapOf(category.id to CategoryParentChange(parentId, updatedAt)),
                )
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
