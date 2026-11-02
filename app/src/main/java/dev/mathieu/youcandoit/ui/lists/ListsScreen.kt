@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mathieu.youcandoit.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.ui.common.NameDialog
import dev.mathieu.youcandoit.ui.common.RowShape
import dev.mathieu.youcandoit.ui.common.countsText

@Composable
fun ListsScreen(onOpenList: (TaskList) -> Unit, viewModel: ListsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ListsContent(
        state = state,
        onOpenList = onOpenList,
        onNewCategory = viewModel::onNewCategory,
        onRename = viewModel::onRename,
        onDelete = viewModel::onDelete,
        onDialogNameChange = viewModel::onDialogNameChange,
        onDialogConfirm = viewModel::onDialogConfirm,
        onDialogDismiss = viewModel::onDialogDismiss,
    )
}

@Composable
fun ListsContent(
    state: ListsUiState,
    onOpenList: (TaskList) -> Unit,
    onNewCategory: () -> Unit,
    onRename: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onDialogNameChange: (String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.title_lists), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.subtitle_lists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "categories") {
                Surface(shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column {
                        state.categories.forEach { summary ->
                            ListRow(
                                name = summary.category.name,
                                counts = summary.counts,
                                onClick = { onOpenList(TaskList.InCategory(summary.category.id)) },
                                trailing = {
                                    CategoryMenu(
                                        name = summary.category.name,
                                        onRename = { onRename(summary.category) },
                                        onDelete = { onDelete(summary.category) },
                                    )
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                        ListRow(
                            name = stringResource(R.string.title_uncategorized),
                            counts = state.uncategorized,
                            onClick = { onOpenList(TaskList.Uncategorized) },
                            trailing = null,
                            muted = true,
                        )
                    }
                }
            }
            item(key = "new") {
                TextButton(onClick = onNewCategory) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_new_category))
                }
            }
            item(key = "note") {
                Text(
                    stringResource(R.string.lists_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        is CategoryDialog.Create -> NameDialog(
            title = R.string.dialog_new_category,
            name = dialog.name,
            error = dialog.error,
            confirmLabel = R.string.action_create,
            onNameChange = onDialogNameChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is CategoryDialog.Rename -> NameDialog(
            title = R.string.dialog_rename_category,
            name = dialog.name,
            error = dialog.error,
            confirmLabel = R.string.action_rename,
            onNameChange = onDialogNameChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is CategoryDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = onDialogDismiss,
            title = { Text(stringResource(R.string.dialog_delete_category, dialog.category.name)) },
            text = { Text(stringResource(R.string.dialog_delete_category_body)) },
            confirmButton = {
                TextButton(onClick = onDialogConfirm) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDialogDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun ListRow(
    name: String,
    counts: ListCounts,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    muted: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { Text(countsText(counts)) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (muted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CategoryMenu(name: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_options, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
