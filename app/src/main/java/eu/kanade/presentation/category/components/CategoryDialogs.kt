package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.category.visualPath
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryTree
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    categories: List<String>,
    parent: Category? = null,
) {
    var name by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(
                text = if (parent == null) {
                    stringResource(MR.strings.action_add_category)
                } else {
                    stringResource(TLMR.strings.category_add_in, parent.name)
                },
            )
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(text = stringResource(MR.strings.name))
                },
                supportingText = {
                    val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = name.isNotEmpty() && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryParentDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Long?) -> Unit,
    categories: List<Category>,
    category: Category,
) {
    val tree = remember(categories) { CategoryTree(categories) }
    val possibleParents = remember(tree, category.id) { listOf(null) + tree.possibleParents(category.id) }
    var selectedId by remember(category) { mutableStateOf(category.parentId) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(TLMR.strings.category_move_parent)) },
        confirmButton = {
            TextButton(
                enabled = selectedId != category.parentId,
                onClick = {
                    onConfirm(selectedId)
                    onDismissRequest()
                },
            ) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                possibleParents.forEach { parent ->
                    val selected = selectedId == parent?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected, role = Role.RadioButton, onClick = { selectedId = parent?.id })
                            .padding(vertical = MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = parent?.visualPath(tree) ?: stringResource(TLMR.strings.category_top_level),
                            modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    categories: List<String>,
    category: String,
) {
    var name by remember { mutableStateOf(category) }
    var valueHasChanged by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = valueHasChanged && name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onRename(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_category))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = name,
                onValueChange = {
                    valueHasChanged = name != it
                    name = it
                },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (valueHasChanged && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = valueHasChanged && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    category: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.delete_category))
        },
        text = {
            Text(text = stringResource(MR.strings.delete_category_confirmation, category))
        },
    )
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: List<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    if (initialSelection.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onEditCategories()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_edit_categories))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_move_category))
            },
            text = {
                Text(text = stringResource(MR.strings.information_empty_category_dialog))
            },
        )
        return
    }
    val tree = remember(initialSelection) { CategoryTree(initialSelection.map { it.value }) }
    var selection by remember(initialSelection) {
        val byId = initialSelection.associateBy { it.value.id }
        mutableStateOf(tree.inHierarchyOrder().mapNotNull { byId[it.id] })
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row {
                tachiyomi.presentation.core.components.material.TextButton(onClick = {
                    onDismissRequest()
                    onEditCategories()
                }) {
                    Text(text = stringResource(MR.strings.action_edit))
                }
                Spacer(modifier = Modifier.weight(1f))
                tachiyomi.presentation.core.components.material.TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm(
                            selection
                                .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
                                .map { it.value.id },
                            selection
                                .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
                                .map { it.value.id },
                        )
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_move_category))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                selection.forEach { checkbox ->
                    val depth = (tree.pathTo(checkbox.value.id).size - 1).coerceIn(0, 4)
                    val onChange: (CheckboxState<Category>) -> Unit = {
                        val index = selection.indexOf(it)
                        if (index != -1) {
                            val mutableList = selection.toMutableList()
                            mutableList[index] = it.next()
                            selection = mutableList.toList()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (depth * 12).dp)
                            .clickable { onChange(checkbox) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (checkbox) {
                            is CheckboxState.TriState -> {
                                TriStateCheckbox(
                                    state = checkbox.asToggleableState(),
                                    onClick = { onChange(checkbox) },
                                )
                            }

                            is CheckboxState.State -> {
                                Checkbox(
                                    checked = checkbox.isChecked,
                                    onCheckedChange = { onChange(checkbox) },
                                )
                            }
                        }

                        Text(
                            text = checkbox.value.visualName,
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                        )
                    }
                }
            }
        },
    )
}
