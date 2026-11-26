package eu.kanade.presentation.library.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualPath
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryTree
import tachiyomi.presentation.core.components.material.TabText

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getNumberOfItemsForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    val tree = remember(categories) { CategoryTree(categories) }
    PrimaryScrollableTabRow(
        selectedTabIndex = currentPageIndex,
        edgePadding = 0.dp,
    ) {
        categories.forEachIndexed { index, category ->
            Tab(
                selected = currentPageIndex == index,
                onClick = { onTabItemClick(index) },
                text = {
                    TabText(
                        text = category.visualPath(tree),
                        badgeCount = getNumberOfItemsForCategory(category),
                    )
                },
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
