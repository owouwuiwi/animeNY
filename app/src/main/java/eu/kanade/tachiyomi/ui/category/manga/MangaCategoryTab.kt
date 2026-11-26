package eu.kanade.tachiyomi.ui.category.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryParentDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.mangaCategoryTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { MangaCategoryScreenModel() }

    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = AYMR.strings.label_manga,
        searchEnabled = false,
        content = { contentPadding, _ ->
            if (state is MangaCategoryScreenState.Loading) {
                LoadingScreen()
            } else {
                val successState = state as MangaCategoryScreenState.Success

                CategoryScreen(
                    categories = successState.categories,
                    onClickCreate = { screenModel.showDialog(MangaCategoryDialog.Create(it)) },
                    onClickMove = { screenModel.showDialog(MangaCategoryDialog.Move(it)) },
                    onClickRename = { screenModel.showDialog(MangaCategoryDialog.Rename(it)) },
                    onClickHide = screenModel::hideCategory,
                    onClickDelete = { screenModel.showDialog(MangaCategoryDialog.Delete(it)) },
                    onChangeOrder = screenModel::changeOrder,
                )

                when (val dialog = successState.dialog) {
                    null -> {}

                    is MangaCategoryDialog.Create -> {
                        CategoryCreateDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onCreate = { name -> screenModel.createCategory(name, dialog.parent?.id) },
                            categories = successState.categoryNames,
                            parent = dialog.parent,
                        )
                    }

                    is MangaCategoryDialog.Move -> {
                        CategoryParentDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onConfirm = { parentId -> screenModel.moveCategory(dialog.category, parentId) },
                            categories = successState.categories,
                            category = dialog.category,
                        )
                    }

                    is MangaCategoryDialog.Rename -> {
                        CategoryRenameDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onRename = { screenModel.renameCategory(dialog.category, it) },
                            categories = successState.categoryNames,
                            category = dialog.category.name,
                        )
                    }

                    is MangaCategoryDialog.Delete -> {
                        CategoryDeleteDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onDelete = { screenModel.deleteCategory(dialog.category.id) },
                            category = dialog.category.name,
                        )
                    }
                }
            }
        },
        navigateUp = navigator::pop,
    )
}
