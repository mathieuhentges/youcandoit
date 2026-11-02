@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mathieu.youcandoit.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.settings.AppearanceDialog
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.common.DoneTaskRow
import dev.mathieu.youcandoit.ui.common.OpenTaskRow
import dev.mathieu.youcandoit.ui.common.RowShape
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.common.countsText
import dev.mathieu.youcandoit.ui.common.label
import dev.mathieu.youcandoit.ui.common.text
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TaskListScreen(
    onAddTask: (TaskList) -> Unit,
    onEditTask: (TaskId) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.title) {
        if (state.title == ListTitle.Missing) onBack?.invoke()
    }
    TaskListContent(
        state = state,
        onAddTask = { onAddTask(state.list) },
        onEditTask = onEditTask,
        onBack = onBack,
        onToggleDone = viewModel::onToggleDone,
        onArchive = viewModel::onArchive,
        onArchiveDone = viewModel::onArchiveDone,
        onMessageShown = viewModel::onMessageShown,
        onReorder = viewModel::onReorder,
        // Home only: the app-wide appearance setting lives on the first screen.
        onOpenAppearance = if (onBack == null) ({ showAppearance = true }) else null,
    )
    if (showAppearance) AppearanceDialog(onDismiss = { showAppearance = false })
}

@Composable
fun TaskListContent(
    state: TaskListUiState,
    onAddTask: () -> Unit,
    onEditTask: (TaskId) -> Unit,
    onBack: (() -> Unit)?,
    onToggleDone: (TaskItem) -> Unit,
    onArchive: (TaskId) -> Unit,
    onArchiveDone: () -> Unit,
    onMessageShown: () -> Unit,
    onReorder: (CategoryId?, List<TaskId>) -> Unit = { _, _ -> },
    onOpenAppearance: (() -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    // The order on screen while dragging; saved on drop, then replaced by what the database says.
    var rows by remember(state.groups) { mutableStateOf(rowsOf(state.groups)) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.moved(from.key, to.key) ?: rows
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun save(taskKey: Any) {
        val (categoryId, ids) = rows.groupOf(taskKey, fallback = state.groups.singleOrNull()?.categoryId)
        onReorder(categoryId, ids)
    }
    val messageText = state.message?.text()
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_task))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + if (onBack == null) 20.dp else 0.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { ListHeader(state, onOpenAppearance) }

            if (!state.isLoading && state.open.isEmpty() && state.done.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.empty_tasks),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                    )
                }
            }

            items(rows, key = { it.key }) { row ->
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                    when (row) {
                        is ListRow.Header -> GroupHeader(row.label)
                        is ListRow.Open -> {
                            val item = row.item
                            val moveUp = rows.moved(row.key, up = true)
                            val moveDown = rows.moved(row.key, up = false)
                            val moveUpLabel = stringResource(R.string.action_move_up)
                            val moveDownLabel = stringResource(R.string.action_move_down)
                            OpenTaskRow(
                                item = item,
                                onToggle = { onToggleDone(item) },
                                onClick = { onEditTask(item.task.id) },
                                // Dragging isn't available to screen reader users: offer the same moves as actions.
                                modifier = Modifier
                                    .shadow(if (isDragging) 8.dp else 0.dp, RowShape)
                                    .semantics {
                                        customActions = listOfNotNull(
                                            moveUp?.let { CustomAccessibilityAction(moveUpLabel) { rows = it; save(row.key); true } },
                                            moveDown?.let { CustomAccessibilityAction(moveDownLabel) { rows = it; save(row.key); true } },
                                        )
                                    },
                                dragHandle = {
                                    Icon(
                                        Icons.Filled.DragIndicator,
                                        contentDescription = stringResource(R.string.action_reorder, item.task.title),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .draggableHandle(
                                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                                onDragStopped = { save(row.key) },
                                            )
                                            .padding(horizontal = 10.dp, vertical = 12.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (state.done.isNotEmpty()) {
                item(key = "done-header") {
                    DoneHeader(count = state.done.size, onArchiveDone = onArchiveDone, modifier = Modifier.animateItem())
                }
                items(state.done, key = { "done-" + it.task.id.value }) { item ->
                    SwipeToArchive(onArchive = { onArchive(item.task.id) }, modifier = Modifier.animateItem()) {
                        DoneTaskRow(
                            item = item,
                            onToggle = { onToggleDone(item) },
                            onClick = { onEditTask(item.task.id) },
                            onArchive = { onArchive(item.task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHeader(state: TaskListUiState, onOpenAppearance: (() -> Unit)?) {
    val title = when (val t = state.title) {
        ListTitle.All -> stringResource(R.string.title_all_tasks)
        ListTitle.Uncategorized -> stringResource(R.string.title_uncategorized)
        is ListTitle.Category -> t.name
        ListTitle.Missing -> ""
    }
    Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (!state.isLoading) {
                Text(countsText(state.counts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onOpenAppearance != null) {
            IconButton(onClick = onOpenAppearance) {
                Icon(Icons.Outlined.DarkMode, contentDescription = stringResource(R.string.appearance_title))
            }
        }
    }
}

@Composable
private fun GroupHeader(label: CategoryLabel) {
    Text(
        label.label(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun DoneHeader(count: Int, onArchiveDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.section_done, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onArchiveDone) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_archive_done))
        }
    }
}

/** Swipe a done task to the left to archive it. */
@Composable
private fun SwipeToArchive(onArchive: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onArchive()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RowShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        stringResource(R.string.action_archive),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    ) {
        content()
    }
}

/** What the open part of the list shows, flattened so a task can be dragged past a header. */
private sealed interface ListRow {
    val key: String

    data class Header(val categoryId: CategoryId?, val label: CategoryLabel) : ListRow {
        override val key = "group-" + (categoryId?.value ?: "none")
    }

    data class Open(val item: TaskItem) : ListRow {
        override val key = "open-" + item.task.id.value
    }
}

private fun rowsOf(groups: List<TaskGroup>): List<ListRow> = groups.flatMap { group ->
    listOfNotNull(group.label?.let { ListRow.Header(group.categoryId, it) }) + group.items.map { ListRow.Open(it) }
}

/** [this] with the row [fromKey] moved to where [toKey] is; null if that's not a valid place for a task. */
private fun List<ListRow>.moved(fromKey: Any, toKey: Any): List<ListRow>? {
    val from = indexOfFirst { it.key == fromKey }
    val to = indexOfFirst { it.key == toKey }
    // Nothing may go above the first header: it would belong to no category.
    if (from < 0 || to < 0 || get(from) !is ListRow.Open || (to == 0 && first() is ListRow.Header)) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** One step up or down, crossing a header into the neighbouring category; null at either end. */
private fun List<ListRow>.moved(key: Any, up: Boolean): List<ListRow>? {
    val from = indexOfFirst { it.key == key }
    val neighbour = getOrNull(if (up) from - 1 else from + 1) ?: return null
    return moved(key, neighbour.key)
}

/** The category whose header [taskKey] sits under, and that group's task ids top to bottom. */
private fun List<ListRow>.groupOf(taskKey: Any, fallback: CategoryId?): Pair<CategoryId?, List<TaskId>> {
    val index = indexOfFirst { it.key == taskKey }
    val start = subList(0, index + 1).indexOfLast { it is ListRow.Header }
    val categoryId = if (start >= 0) (get(start) as ListRow.Header).categoryId else fallback
    val ids = drop(start + 1).takeWhile { it is ListRow.Open }.map { (it as ListRow.Open).item.task.id }
    return categoryId to ids
}
