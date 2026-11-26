package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.VisibilityOff
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onCreateChild: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small)
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.medium)
                    .draggableHandle(),
            )
            Text(
                text = category.name,
                modifier = Modifier.weight(1f),
            )
            if (category.hidden) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.VisibilityOff,
                    contentDescription = stringResource(AYMR.strings.action_hide),
                )
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val visibility = if (category.hidden) TLMR.strings.category_show else AYMR.strings.action_hide
                    val actions = listOf(
                        TLMR.strings.category_add_subcategory to onCreateChild,
                        TLMR.strings.category_move_parent to onMove,
                        MR.strings.action_rename_category to onRename,
                        visibility to onHide,
                        MR.strings.action_delete to onDelete,
                    )
                    actions.forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                menuOpen = false
                                action()
                            },
                        )
                    }
                }
            }
        }
    }
}
