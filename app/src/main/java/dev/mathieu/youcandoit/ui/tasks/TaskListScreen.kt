@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mathieu.youcandoit.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DoneOrder
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.settings.ListSort
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.common.DoneTaskRow
import dev.mathieu.youcandoit.ui.common.OpenTaskRow
import dev.mathieu.youcandoit.ui.common.RowShape
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.common.countsText
import dev.mathieu.youcandoit.ui.common.label
import dev.mathieu.youcandoit.ui.common.labelRes
import dev.mathieu.youcandoit.ui.common.style
import dev.mathieu.youcandoit.ui.common.text
import kotlin.math.abs
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TaskListScreen(
    onAddTask: (TaskList) -> Unit,
    onEditTask: (TaskId) -> Unit,
    onBack: (() -> Unit)?,
    onShowWelcome: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onOpenCustomize: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        onSortChange = viewModel::onSortChange,
        onReorderSubTasks = viewModel::onReorderSubTasks,
        onToggleSubTasks = viewModel::onToggleSubTasks,
        onMakeSubTask = viewModel::onMakeSubTask,
        onMoveOutOfParent = viewModel::onMoveOutOfParent,
        onConfirmDone = viewModel::onConfirmDone,
        onDismissConfirmDone = viewModel::onDismissConfirmDone,
        selection = viewModel,
        onOpenCustomize = onOpenCustomize,
        onShowWelcome = onShowWelcome,
        onOpenAbout = onOpenAbout,
        onExport = onExport,
        onImport = onImport,
    )
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
    onSortChange: (ListSort) -> Unit = {},
    onReorderSubTasks: (TaskId, List<TaskId>) -> Unit = { _, _ -> },
    onConfirmDone: () -> Unit = {},
    selection: TaskSelectionActions = TaskSelectionActions.None,
    onDismissConfirmDone: () -> Unit = {},
    onOpenCustomize: (() -> Unit)? = null,
    onShowWelcome: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onMakeSubTask: (TaskId, TaskId) -> Unit = { _, _ -> },
    onMoveOutOfParent: (TaskId) -> Unit = {},
    onToggleSubTasks: (TaskId) -> Unit = {},
) {
    // Category headers folded away for now (not saved across launches).
    var collapsedGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
    // In selection mode a tap (on the row or its check) picks the task instead of opening or ticking it.
    val selecting = state.isSelecting
    BackHandler(enabled = selecting, onBack = selection::onClearSelection)
    val tap: (TaskItem) -> Unit = { if (selecting) selection.onToggleSelection(it.task.id) else onEditTask(it.task.id) }
    val check: (TaskItem) -> Unit = { if (selecting) selection.onToggleSelection(it.task.id) else onToggleDone(it) }
    val longPress: (TaskItem) -> Unit = { selection.onToggleSelection(it.task.id) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    // The order on screen while dragging; saved on drop, then replaced by what the database says.
    var rows by remember { mutableStateOf(rowsOf(state.groups, collapsedGroups)) }
    var dragging by remember { mutableStateOf(false) }
    var shownGroups by remember { mutableStateOf(state.groups) }
    // Fresh data replaces the on-screen order, except mid-drag: the row would jump out of the
    // user's hand. The drop saves, and the database answers with the merged result.
    if (!dragging && shownGroups != state.groups) {
        shownGroups = state.groups
        rows = rowsOf(state.groups, collapsedGroups)
    }
    fun toggleGroup(key: String) {
        collapsedGroups = if (key in collapsedGroups) collapsedGroups - key else collapsedGroups + key
        rows = rowsOf(shownGroups, collapsedGroups)
    }
    // Far enough right nests the dragged task under the one above it.
    val sideways = rememberSideways()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.moved(from.key, to.key) ?: rows
        sideways.reset()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun save(taskKey: Any) {
        val (categoryId, ids) = rows.groupOf(taskKey, fallback = state.groups.singleOrNull()?.categoryId)
        // Dropped under a collapsed header: it goes on top of the tasks hidden there.
        val hidden = shownGroups
            .firstOrNull { it.label != null && it.categoryId == categoryId && headerKey(categoryId) in collapsedGroups }
            ?.items.orEmpty().map { it.task.id }.filter { it !in ids }
        onReorder(categoryId, ids + hidden)
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
            if (state.isSelecting) {
                SelectionBar(state, selection)
                return@Scaffold
            }
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
            item(key = "header") { ListHeader(state, onSortChange, AppMenuActions(onOpenCustomize, onShowWelcome, onOpenAbout, onExport, onImport)) }

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
                        is ListRow.Header -> GroupHeader(
                            label = row.label,
                            count = row.count,
                            collapsed = row.key in collapsedGroups,
                            onToggle = { toggleGroup(row.key) },
                        )
                        is ListRow.Open -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val item = row.item
                            val expanded = item.task.id !in state.collapsed
                            val canDrag = state.sort.canDrag && !selecting
                            val moveUp = if (canDrag) rows.moved(row.key, up = true) else null
                            val moveDown = if (canDrag) rows.moved(row.key, up = false) else null
                            val moveUpLabel = stringResource(R.string.action_move_up)
                            val moveDownLabel = stringResource(R.string.action_move_down)
                            val children = state.subTasks[item.task.id].orEmpty()
                            // One level only: a task with sub tasks of its own can't go under another.
                            val newParent = if (canDrag && children.isEmpty()) rows.taskAbove(row.key) else null
                            val nestLabel = newParent?.let { stringResource(R.string.action_make_sub_task, it.item.task.title) }
                            Box {
                                OpenTaskRow(
                                    item = item,
                                    onToggle = { check(item) },
                                    onClick = { tap(item) },
                                    onLongClick = { longPress(item) },
                                    selected = item.task.id in state.selected,
                                    // Dragging isn't available to screen reader users: offer the same moves as actions.
                                    modifier = Modifier
                                        .nestPreview { if (isDragging && newParent != null) sideways.progress.coerceAtLeast(0f) else 0f }
                                        .shadow(if (isDragging) 8.dp else 0.dp, RowShape)
                                        .semantics {
                                            customActions = listOfNotNull(
                                                moveUp?.let { CustomAccessibilityAction(moveUpLabel) { rows = it; save(row.key); true } },
                                                moveDown?.let { CustomAccessibilityAction(moveDownLabel) { rows = it; save(row.key); true } },
                                                newParent?.let {
                                                    CustomAccessibilityAction(nestLabel!!) { onMakeSubTask(item.task.id, it.item.task.id); true }
                                                },
                                            )
                                        },
                                    // Sorted by priority or deadline, the order isn't the user's to change.
                                    dragHandle = if (!canDrag) null else ({
                                        Icon(
                                            Icons.Filled.DragIndicator,
                                            contentDescription = stringResource(R.string.action_reorder, item.task.title),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .trackSideways(sideways) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        dragging = true
                                                        sideways.reset()
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDragStopped = {
                                                        dragging = false
                                                        // Read where it was dropped: the task above may have changed during the drag.
                                                        val parent = rows.taskAbove(row.key).takeIf { children.isEmpty() }
                                                        if (sideways.progress >= 1f && parent != null) {
                                                            onMakeSubTask(item.task.id, parent.item.task.id)
                                                        } else {
                                                            save(row.key)
                                                        }
                                                    },
                                                )
                                                .padding(horizontal = 10.dp, vertical = 12.dp),
                                        )
                                    }),
                                    expanded = expanded,
                                    onToggleExpanded = { onToggleSubTasks(item.task.id) },
                                )
                                // Says what letting go will do, so nesting can be found by dragging.
                                if (isDragging && newParent != null) {
                                    val ready = sideways.progress >= 1f
                                    NestHint(
                                        text = stringResource(
                                            if (ready) R.string.nest_hint_release else R.string.nest_hint_push,
                                            newParent.item.task.title,
                                        ),
                                        ready = ready,
                                        toRight = true,
                                        modifier = Modifier.align(Alignment.TopCenter),
                                    )
                            }
                            }
                            if (expanded && children.isNotEmpty()) {
                                SubTaskBlock(
                                    parentTitle = item.task.title,
                                    children = children,
                                    canDrag = canDrag,
                                    selected = state.selected,
                                    onCheck = check,
                                    onTap = tap,
                                    onLongPress = longPress,
                                    onReorder = { ids -> onReorderSubTasks(item.task.id, ids) },
                                    onMoveOut = onMoveOutOfParent,
                                )
                            }
                        }
                    }
                }
            }

            if (state.done.isNotEmpty()) {
                item(key = "done-header") {
                    DoneHeader(
                        count = state.done.size,
                        sort = state.sort,
                        // On a single list every done task shares the category: sorting by it changes nothing.
                        showCategory = state.list == TaskList.All,
                        onSortChange = onSortChange,
                        onArchiveDone = onArchiveDone,
                        modifier = Modifier.animateItem(),
                    )
                }
                items(state.done, key = { "done-" + it.task.id.value }) { item ->
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Swiping the parent archives its sub tasks with it.
                        SwipeToArchive(onArchive = { onArchive(item.task.id) }, enabled = !selecting) {
                            DoneTaskRow(
                                item = item,
                                onToggle = { check(item) },
                                onClick = { tap(item) },
                                onArchive = { onArchive(item.task.id) },
                                onLongClick = { longPress(item) },
                                selected = item.task.id in state.selected,
                            )
                        }
                        val children = state.subTasks[item.task.id].orEmpty()
                        if (children.isNotEmpty()) {
                            Column(Modifier.padding(start = SubTaskIndent), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                children.forEach { child ->
                                    key(child.task.id.value) {
                                        DoneTaskRow(
                                            item = child,
                                            onToggle = { check(child) },
                                            onClick = { tap(child) },
                                            onLongClick = { longPress(child) },
                                            selected = child.task.id in state.selected,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.confirmDone?.let { parent ->
        val openCount = state.subTasks[parent.task.id].orEmpty().count { it.task.isOpen }
        AlertDialog(
            onDismissRequest = onDismissConfirmDone,
            title = { Text(stringResource(R.string.dialog_mark_all_done)) },
            text = { Text(pluralStringResource(R.plurals.dialog_mark_all_done_body, openCount, openCount)) },
            confirmButton = { TextButton(onClick = onConfirmDone) { Text(stringResource(R.string.action_mark_all_done)) } },
            dismissButton = { TextButton(onClick = onDismissConfirmDone) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ListHeader(
    state: TaskListUiState,
    onSortChange: (ListSort) -> Unit,
    menu: AppMenuActions,
) {
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
        SortMenu(sort = state.sort, showGrouping = state.list == TaskList.All, onSortChange = onSortChange)
        AppMenu(menu)
    }
}

/** Home's app-wide items; each one is null where it isn't offered. */
private data class AppMenuActions(
    val onOpenCustomize: (() -> Unit)?,
    val onShowWelcome: (() -> Unit)?,
    val onOpenAbout: (() -> Unit)?,
    val onExport: (() -> Unit)?,
    val onImport: (() -> Unit)?,
)

@Composable
private fun AppMenu(actions: AppMenuActions) {
    val items = listOfNotNull(
        actions.onOpenCustomize?.let { Triple(R.string.title_customize, Icons.Outlined.Palette, it) },
        actions.onShowWelcome?.let { Triple(R.string.action_show_welcome, Icons.Outlined.FavoriteBorder, it) },
        actions.onExport?.let { Triple(R.string.action_export, Icons.Outlined.FileUpload, it) },
        actions.onImport?.let { Triple(R.string.action_import, Icons.Outlined.FileDownload, it) },
        actions.onOpenAbout?.let { Triple(R.string.title_about, Icons.Outlined.Info, it) },
    )
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, icon, onClick) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        open = false
                        onClick()
                    },
                )
            }
        }
    }
}

/** Replaces the list's header bar in selection mode: how many are picked, and what to do with them. */
@Composable
private fun SelectionBar(state: TaskListUiState, selection: TaskSelectionActions) {
    var menu by remember { mutableStateOf<SelectionMenu?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val count = state.selected.size
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = selection::onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menu = SelectionMenu.PRIORITY }) {
                    Icon(Icons.Outlined.Flag, contentDescription = stringResource(R.string.action_set_priority))
                }
                DropdownMenu(expanded = menu == SelectionMenu.PRIORITY, onDismissRequest = { menu = null }) {
                    Priority.entries.reversed().forEach { priority ->
                        DropdownMenuItem(
                            text = { Text(stringResource(priority.labelRes())) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(priority.style().dot, CircleShape)) },
                            onClick = {
                                menu = null
                                selection.onSetSelectedPriority(priority)
                            },
                        )
                    }
                }
            }
            // Sub tasks follow their parent, so only top-level tasks can move.
            if (state.canMoveSelection) {
                Box {
                    IconButton(onClick = { menu = SelectionMenu.CATEGORY }) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = stringResource(R.string.action_move_to_category))
                    }
                    DropdownMenu(expanded = menu == SelectionMenu.CATEGORY, onDismissRequest = { menu = null }) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                menu = null
                                selection.onMoveSelected(category.id)
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.title_uncategorized)) }, onClick = {
                            menu = null
                            selection.onMoveSelected(null)
                        })
                    }
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete_selected))
            }
        },
    )
    if (confirmDelete) {
        val alsoSubTasks = state.subTasksDeletedWithSelection
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.dialog_delete_selected, count, count)) },
            text = {
                val body = stringResource(R.string.dialog_delete_task_body)
                Text(if (alsoSubTasks == 0) body else body + " " + pluralStringResource(R.plurals.dialog_delete_task_sub_tasks, alsoSubTasks, alsoSubTasks))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    selection.onDeleteSelected()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private enum class SelectionMenu { PRIORITY, CATEGORY }

/** A parent's sub tasks, indented: open ones (draggable among themselves), then done ones. */
@Composable
private fun SubTaskBlock(
    parentTitle: String,
    children: List<TaskItem>,
    canDrag: Boolean,
    selected: Set<TaskId>,
    onCheck: (TaskItem) -> Unit,
    onTap: (TaskItem) -> Unit,
    onLongPress: (TaskItem) -> Unit,
    onReorder: (List<TaskId>) -> Unit,
    onMoveOut: (TaskId) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Far enough left takes the dragged sub task out of its parent.
    val sideways = rememberSideways()
    val moveOutLabel = stringResource(R.string.action_move_out_of, parentTitle)
    val freshOpen = children.filter { it.task.isOpen }
    var open by remember { mutableStateOf(freshOpen) }
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(freshOpen) }
    // As for the main list: new data only replaces the order when nothing is being dragged.
    if (!dragging && shown != freshOpen) {
        shown = freshOpen
        open = freshOpen
    }
    fun move(from: Int, to: Int) {
        open = open.toMutableList().apply { add(to, removeAt(from)) }
        onReorder(open.map { it.task.id })
    }
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    Column(Modifier.padding(start = SubTaskIndent), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReorderableColumn(
            list = open,
            onSettle = { from, to -> if (from != to) move(from, to) },
            onMove = {
                sideways.reset()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { index, child, isDragging ->
            key(child.task.id.value) {
                // Even an only child has a handle: dragging it left takes it out of its parent.
                val reorderable = canDrag && open.size > 1
                Box {
                    OpenTaskRow(
                        item = child,
                        onToggle = { onCheck(child) },
                        onClick = { onTap(child) },
                        onLongClick = { onLongPress(child) },
                        selected = child.task.id in selected,
                        modifier = Modifier
                            .nestPreview { if (isDragging) sideways.progress.coerceAtMost(0f) else 0f }
                            .shadow(if (isDragging) 8.dp else 0.dp, RowShape)
                            .semantics {
                                if (canDrag) {
                                    customActions = listOfNotNull(
                                        CustomAccessibilityAction(moveUpLabel) { move(index, index - 1); true }.takeIf { reorderable && index > 0 },
                                        CustomAccessibilityAction(moveDownLabel) { move(index, index + 1); true }
                                            .takeIf { reorderable && index < open.lastIndex },
                                        CustomAccessibilityAction(moveOutLabel) { onMoveOut(child.task.id); true },
                                    )
                                }
                            },
                        dragHandle = if (!canDrag) null else ({
                            Icon(
                                Icons.Filled.DragIndicator,
                                contentDescription = stringResource(R.string.action_reorder, child.task.title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .trackSideways(sideways) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                    .draggableHandle(
                                        onDragStarted = {
                                            dragging = true
                                            sideways.reset()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            dragging = false
                                            if (sideways.progress <= -1f) onMoveOut(child.task.id)
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                            )
                        }),
                    )
                    if (isDragging) {
                        val ready = sideways.progress <= -1f
                        NestHint(
                            text = stringResource(if (ready) R.string.move_out_hint_release else R.string.move_out_hint_push),
                            ready = ready,
                            toRight = false,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                }
                }
            }
        }
        children.filter { it.task.isDone }.forEach { child ->
            key(child.task.id.value) {
                DoneTaskRow(
                    item = child,
                    onToggle = { onCheck(child) },
                    onClick = { onTap(child) },
                    onLongClick = { onLongPress(child) },
                    selected = child.task.id in selected,
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sort: ListSort, showGrouping: Boolean, onSortChange: (ListSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TaskOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes())) },
                    leadingIcon = { RadioButton(selected = sort.order == order, onClick = null) },
                    onClick = {
                        onSortChange(sort.copy(order = order))
                        open = false
                    },
                    modifier = Modifier.semantics { selected = sort.order == order },
                )
            }
            if (showGrouping) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_group_by_category)) },
                    // My order is kept per category, so it always has headers.
                    leadingIcon = { Checkbox(checked = sort.isGrouped, onCheckedChange = null, enabled = !sort.canDrag) },
                    enabled = !sort.canDrag,
                    onClick = { onSortChange(sort.copy(groupByCategory = !sort.groupByCategory)) },
                )
            }
        }
    }
}

@Composable
private fun DoneSortMenu(sort: ListSort, showCategory: Boolean, onSortChange: (ListSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // A Category choice made on Home reads as Recently done in a single list.
    val current = if (!showCategory && sort.doneOrder == DoneOrder.CATEGORY) DoneOrder.RECENTLY_DONE else sort.doneOrder
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort_done))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DoneOrder.entries.filter { showCategory || it != DoneOrder.CATEGORY }.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes())) },
                    leadingIcon = { RadioButton(selected = current == order, onClick = null) },
                    onClick = {
                        onSortChange(sort.copy(doneOrder = order))
                        open = false
                    },
                    modifier = Modifier.semantics { selected = current == order },
                )
            }
        }
    }
}

private fun DoneOrder.labelRes(): Int = when (this) {
    DoneOrder.RECENTLY_DONE -> R.string.sort_recently_done
    DoneOrder.PRIORITY -> R.string.sort_priority
    DoneOrder.CATEGORY -> R.string.sort_category
}

private fun TaskOrder.labelRes(): Int = when (this) {
    TaskOrder.MY_ORDER -> R.string.sort_my_order
    TaskOrder.PRIORITY -> R.string.sort_priority
    TaskOrder.DEADLINE -> R.string.sort_deadline
    TaskOrder.DATE_ADDED -> R.string.sort_date_added
}

/** A category's header on Home; tap to fold its tasks away for now. */
@Composable
private fun GroupHeader(label: CategoryLabel, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (collapsed) R.string.state_collapsed else R.string.state_expanded)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RowShape)
            .clickable(
                onClickLabel = stringResource(if (collapsed) R.string.action_expand_category else R.string.action_collapse_category),
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics {
                heading()
                stateDescription = state
            }
            .padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.label(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "  $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp).size(20.dp),
        )
    }
}

@Composable
private fun DoneHeader(
    count: Int,
    sort: ListSort,
    showCategory: Boolean,
    onSortChange: (ListSort) -> Unit,
    onArchiveDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        DoneSortMenu(sort = sort, showCategory = showCategory, onSortChange = onSortChange)
        TextButton(onClick = onArchiveDone) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_archive_done))
        }
    }
}

/** Swipe a done task to the left to archive it. */
@Composable
private fun SwipeToArchive(onArchive: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
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
        gesturesEnabled = enabled,
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

    data class Header(val categoryId: CategoryId?, val label: CategoryLabel, val count: Int) : ListRow {
        override val key = headerKey(categoryId)
    }

    data class Open(val item: TaskItem) : ListRow {
        override val key = "open-" + item.task.id.value
    }
}

private fun headerKey(categoryId: CategoryId?) = "group-" + (categoryId?.value ?: "none")

/** A collapsed header ([collapsed] holds header keys) keeps its place but hides its tasks. */
private fun rowsOf(groups: List<TaskGroup>, collapsed: List<String>): List<ListRow> = groups.flatMap { group ->
    val header = group.label?.let { ListRow.Header(group.categoryId, it, group.items.size) }
    if (header != null && header.key in collapsed) listOf(header)
    else listOfNotNull(header) + group.items.map { ListRow.Open(it) }
}

/** The task just above [key] under the same header: the one a drag to the right puts it under. */
private fun List<ListRow>.taskAbove(key: Any): ListRow.Open? = getOrNull(indexOfFirst { it.key == key } - 1) as? ListRow.Open

/**
 * A label over the dragged row: which way to push it, then (once [ready]) that letting go will nest it
 * or take it out. Drawn above the row without taking room, so the list doesn't move under the finger.
 */
@Composable
private fun NestHint(text: String, ready: Boolean, toRight: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (ready) colors.primary else colors.inverseSurface,
        contentColor = if (ready) colors.onPrimary else colors.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = modifier.offset(y = (-20).dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (toRight) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How far sideways a dragged handle must go to nest a task (right) or take it out (left). */
private val NestDistance = 48.dp
private val SubTaskIndent = 28.dp

/** The dragged row follows the finger sideways, up to one sub-task indent, as a preview of nesting. */
private fun Modifier.nestPreview(progress: () -> Float): Modifier = graphicsLayer {
    translationX = progress().coerceIn(-1f, 1f) * SubTaskIndent.toPx()
}

/**
 * How far a dragged handle has gone sideways since the task last changed place, as a fraction of
 * [NestDistance]; 0 while that movement is more up or down than sideways. So a long, slightly
 * slanted drag doesn't nest by accident: the user puts the task in place, then pushes it sideways.
 */
private class Sideways(private val threshold: Float) {
    private var x by mutableFloatStateOf(0f)
    private var y by mutableFloatStateOf(0f)
    val progress: Float get() = if (abs(x) > abs(y)) x / threshold else 0f

    /** Adds a move; true when that crosses the threshold either way. */
    fun add(delta: Offset): Boolean {
        val before = abs(progress) >= 1f
        x += delta.x
        y += delta.y
        return before != abs(progress) >= 1f
    }

    fun reset() {
        x = 0f
        y = 0f
    }
}

@Composable
private fun rememberSideways(): Sideways {
    val threshold = with(LocalDensity.current) { NestDistance.toPx() }
    return remember(threshold) { Sideways(threshold) }
}

/**
 * Feeds a drag on this handle's sideways movement into [sideways], without taking the gesture from
 * the reorder drag (it only watches, in the Initial pass). [onCross] fires each time it passes the threshold.
 */
private fun Modifier.trackSideways(sideways: Sideways, onCross: () -> Unit): Modifier = pointerInput(sideways) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (sideways.add(change.positionChange())) onCross()
            if (!change.pressed) break
        }
    }
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
