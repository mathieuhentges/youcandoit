@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mathieu.youcandoit.ui.lists

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.ui.common.labelRes
import dev.mathieu.youcandoit.ui.common.style
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
    // Whose details are open: a category's id, or UNCATEGORIZED; the counts are read from [state] so they stay live.
    var details by rememberSaveable { mutableStateOf<String?>(null) }
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
                                        onDetails = { details = summary.category.id.value },
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
                            trailing = {
                                CategoryMenu(
                                    name = stringResource(R.string.title_uncategorized),
                                    onDetails = { details = UNCATEGORIZED },
                                )
                            },
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

    details?.let { key ->
        val summary = state.categories.firstOrNull { it.category.id.value == key }
        if (key != UNCATEGORIZED && summary == null) {
            // Deleted meanwhile.
            details = null
        } else {
            DetailsDialog(
                name = summary?.category?.name ?: stringResource(R.string.title_uncategorized),
                counts = summary?.counts ?: state.uncategorized,
                openByPriority = summary?.openByPriority ?: state.uncategorizedByPriority,
                onDismiss = { details = null },
            )
        }
    }
}

/** Category ids are never empty, so this can't clash with one. */
private const val UNCATEGORIZED = ""

@Composable
private fun DetailsDialog(name: String, counts: ListCounts, openByPriority: Map<Priority, Int>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountTile(counts.open, R.string.details_open, Modifier.weight(1f))
                    CountTile(counts.overdue, R.string.details_overdue, Modifier.weight(1f), alert = counts.overdue > 0)
                    CountTile(counts.done, R.string.details_done, Modifier.weight(1f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.details_by_priority),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Most important first.
                    Priority.entries.reversed().forEach { priority ->
                        val count = openByPriority[priority] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth().height(36.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(12.dp).background(priority.style().dot, CircleShape))
                            Text(stringResource(priority.labelRes()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun CountTile(count: Int, label: Int, modifier: Modifier, alert: Boolean = false) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
private fun CategoryMenu(name: String, onDetails: () -> Unit, onRename: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_options, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOfNotNull(
                R.string.action_details to onDetails,
                onRename?.let { R.string.action_rename to it },
                onDelete?.let { R.string.action_delete to it },
            ).forEach { (label, onClick) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onClick()
                    },
                )
            }
        }
    }
}
