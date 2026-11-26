package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryTree
import tachiyomi.domain.category.model.moveCategoryAmongSiblings
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun CategoryScreen(
    categories: List<Category>,
    onClickCreate: (Category?) -> Unit,
    onClickMove: (Category) -> Unit,
    onClickRename: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Category) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = { onClickCreate(null) },
            )
        },
    ) { paddingValues ->
        if (categories.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CategoryContent(
            categories = categories,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickCreate = onClickCreate,
            onClickMove = onClickMove,
            onClickRename = onClickRename,
            onClickHide = onClickHide,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
        )
    }
}

@Composable
private fun CategoryContent(
    categories: List<Category>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickCreate: (Category) -> Unit,
    onClickMove: (Category) -> Unit,
    onClickRename: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Category) -> Unit,
) {
    val categoriesState = remember { categories.toMutableStateList() }
    val categoryTree = remember(categoriesState.toList()) { CategoryTree(categoriesState.toList()) }
    val rows = categoryTree.inHierarchyOrder()
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val item = categoriesState.firstOrNull { it.id == from.key } ?: return@rememberReorderableLazyListState
        val target = categoriesState.firstOrNull { it.id == to.key } ?: return@rememberReorderableLazyListState
        val reordered = categoriesState.moveCategoryAmongSiblings(item.id, target.id)
        if (reordered != categoriesState) {
            categoriesState.clear()
            categoriesState.addAll(reordered)
            onChangeOrder(item, target)
        }
    }

    LaunchedEffect(categories, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            categoriesState.clear()
            categoriesState.addAll(categories)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = rows,
            key = Category::id,
        ) { category ->
            val depth = (categoryTree.pathTo(category.id).size - 1).coerceIn(0, 4)
            ReorderableItem(reorderableState, category.id) {
                CategoryListItem(
                    modifier = Modifier.animateItem().padding(start = (depth * 12).dp),
                    category = category,
                    onCreateChild = { onClickCreate(category) },
                    onMove = { onClickMove(category) },
                    onRename = { onClickRename(category) },
                    onHide = { onClickHide(category) },
                    onDelete = { onClickDelete(category) },
                )
            }
        }
    }
}
