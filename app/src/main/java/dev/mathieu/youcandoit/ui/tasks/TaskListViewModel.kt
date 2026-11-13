package dev.mathieu.youcandoit.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.usecase.ArchiveDoneTasks
import dev.mathieu.youcandoit.domain.usecase.ArchiveTask
import dev.mathieu.youcandoit.domain.usecase.MakeSubTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.MarkTaskNotDone
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveTaskList
import dev.mathieu.youcandoit.domain.usecase.DeleteTasks
import dev.mathieu.youcandoit.domain.usecase.MoveOutOfParent
import dev.mathieu.youcandoit.domain.usecase.MoveTasksToCategory
import dev.mathieu.youcandoit.domain.usecase.ReorderSubTasks
import dev.mathieu.youcandoit.domain.usecase.SetTasksPriority
import dev.mathieu.youcandoit.domain.usecase.ReorderTasks
import dev.mathieu.youcandoit.settings.ListSort
import dev.mathieu.youcandoit.settings.SettingsRepository
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.common.SubTaskCount
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.common.Ticker
import dev.mathieu.youcandoit.ui.common.UserMessage
import dev.mathieu.youcandoit.ui.common.asDomainError
import dev.mathieu.youcandoit.ui.common.toItem
import dev.mathieu.youcandoit.ui.navigation.ListKey
import dev.mathieu.youcandoit.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ListTitle {
    data object All : ListTitle
    data object Uncategorized : ListTitle
    data class Category(val name: String) : ListTitle

    /** The category was deleted while open; the screen closes itself. */
    data object Missing : ListTitle
}

/**
 * Open tasks under one header. On Home there's a group per category with open tasks (alphabetical,
 * "Uncategorized" last); a single category's list has one group and [label] null: no header.
 */
data class TaskGroup(
    val categoryId: CategoryId?,
    val label: CategoryLabel?,
    val items: List<TaskItem>,
)

data class TaskListUiState(
    val isLoading: Boolean = true,
    val list: TaskList = TaskList.All,
    val title: ListTitle = ListTitle.All,
    val groups: List<TaskGroup> = emptyList(),
    val done: List<TaskItem> = emptyList(),
    val message: UserMessage? = null,
    val sort: ListSort = ListSort(),
    /** Each parent's sub tasks, open ones first; shown under the parent's row. */
    val subTasks: Map<TaskId, List<TaskItem>> = emptyMap(),
    /** Parents whose sub tasks are folded away (remembered across launches). */
    val collapsed: Set<TaskId> = emptySet(),
    /** A parent was ticked while it has open sub tasks: asks before ticking them too. */
    val confirmDone: TaskItem? = null,
    /** Selection mode (entered by a long press) when not empty: the tasks a bulk change applies to. */
    val selected: Set<TaskId> = emptySet(),
    /** For "Move to category" in selection mode, alphabetical. */
    val categories: List<Category> = emptyList(),
) {
    val isSelecting: Boolean get() = selected.isNotEmpty()

    /** Moving only applies to top-level tasks: sub tasks follow their parent. */
    val canMoveSelection: Boolean get() = (open + done).any { it.task.id in selected }

    /** Sub tasks that a delete of the selection takes with it, beyond the selected tasks themselves. */
    val subTasksDeletedWithSelection: Int get() =
        selected.sumOf { id -> subTasks[id].orEmpty().count { it.task.id !in selected } }

    val open: List<TaskItem> get() = groups.flatMap { it.items }

    /** Sub tasks count like any task. */
    val counts: ListCounts get() {
        val children = subTasks.values.flatten()
        val openChildren = children.filter { it.task.isOpen }
        return ListCounts(
            open = open.size + openChildren.size,
            overdue = (open + openChildren).count { it.isOverdue },
            done = done.size + children.count { it.task.isDone },
        )
    }
}

/** What selection mode (entered by a long press) can do; implemented by the ViewModel. */
interface TaskSelectionActions {
    fun onToggleSelection(id: TaskId)
    fun onClearSelection()
    fun onDeleteSelected()
    fun onSetSelectedPriority(priority: Priority)
    fun onMoveSelected(categoryId: CategoryId?)

    /** For screens and previews that don't select. */
    object None : TaskSelectionActions {
        override fun onToggleSelection(id: TaskId) = Unit
        override fun onClearSelection() = Unit
        override fun onDeleteSelected() = Unit
        override fun onSetSelectedPriority(priority: Priority) = Unit
        override fun onMoveSelected(categoryId: CategoryId?) = Unit
    }
}

/** Home (All tasks) and a single category's list. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTaskList: ObserveTaskList,
    observeCategories: ObserveCategories,
    ticker: Ticker,
    private val markTaskDone: MarkTaskDone,
    private val markTaskNotDone: MarkTaskNotDone,
    private val archiveTask: ArchiveTask,
    private val archiveDoneTasks: ArchiveDoneTasks,
    private val reorderTasks: ReorderTasks,
    private val reorderSubTasks: ReorderSubTasks,
    private val deleteTasks: DeleteTasks,
    private val setTasksPriority: SetTasksPriority,
    private val moveTasksToCategory: MoveTasksToCategory,
    private val makeSubTask: MakeSubTask,
    private val moveOutOfParent: MoveOutOfParent,
    private val settings: SettingsRepository,
) : ViewModel(), TaskSelectionActions {
    val list: TaskList = ListKey.decode(savedStateHandle.get<String>(NavArgs.LIST))
    private val message = MutableStateFlow<UserMessage?>(null)
    private val confirmDone = MutableStateFlow<TaskId?>(null)
    private val selected = MutableStateFlow<Set<TaskId>>(emptySet())

    private data class Transient(val message: UserMessage?, val confirmDone: TaskId?, val selected: Set<TaskId>)
    private val transient = combine(message, confirmDone, selected, ::Transient)

    private val sortedList = settings.listSort.flatMapLatest { sort -> observeTaskList(list, sort.order, sort.doneOrder).map { sort to it } }

    val uiState: StateFlow<TaskListUiState> =
        // The ticker re-evaluates "overdue" every minute, not only when data changes.
        combine(sortedList, observeCategories(), transient, ticker.minutes, settings.collapsedParents) { (sort, view), categories, ui, now, collapsed ->
            val byId = categories.associateBy { it.id }
            // A category's list and the Uncategorized list already say it in their title.
            val showCategory = list == TaskList.All
            // Under a header, open rows don't repeat their category; in one flat list they need it.
            val headers = list == TaskList.All && sort.isGrouped
            // Sub tasks sit under their parent, which already says where it belongs.
            val subTasks = view.subTasks.mapValues { (_, children) -> children.map { it.toItem(byId, showCategory = false, now) } }
            fun TaskItem.withCount() = subTasks[task.id]?.let { children ->
                copy(subTasks = SubTaskCount(done = children.count { it.task.isDone }, total = children.size))
            } ?: this
            val open = view.open.map { it.toItem(byId, showCategory && !headers, now).withCount() }
            TaskListUiState(
                isLoading = false,
                list = list,
                title = titleFor(list, byId),
                groups = groupsFor(list, headers, open, categories),
                done = view.done.map { it.toItem(byId, showCategory, now).withCount() },
                message = ui.message,
                sort = sort,
                subTasks = subTasks,
                collapsed = collapsed.mapTo(mutableSetOf()) { TaskId(it) },
                confirmDone = open.firstOrNull { it.task.id == ui.confirmDone },
                // A selected task that was meanwhile deleted or archived drops out of the selection.
                selected = ui.selected.filterTo(mutableSetOf()) { id ->
                    view.open.any { it.id == id } || view.done.any { it.id == id } || view.subTasks.values.any { c -> c.any { it.id == id } }
                },
                categories = categories,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState(list = list))

    fun onToggleDone(item: TaskItem) {
        val hasOpenSubTasks = uiState.value.subTasks[item.task.id].orEmpty().any { it.task.isOpen }
        if (item.task.isOpen && hasOpenSubTasks) {
            confirmDone.value = item.task.id
            return
        }
        toggle(item)
    }

    /** "Mark all done": the parent and its open sub tasks. */
    fun onConfirmDone() {
        val item = uiState.value.confirmDone ?: return
        confirmDone.value = null
        toggle(item)
    }

    fun onDismissConfirmDone() {
        confirmDone.value = null
    }

    private fun toggle(item: TaskItem) {
        val id = item.task.id
        viewModelScope.launch {
            val result = if (item.task.isOpen) markTaskDone(id) else markTaskNotDone(id)
            result.onFailure(::report)
        }
    }

    fun onArchive(id: TaskId) {
        viewModelScope.launch { archiveTask(id).onFailure(::report) }
    }

    fun onArchiveDone() {
        viewModelScope.launch {
            val count = archiveDoneTasks(list)
            if (count > 0) message.value = UserMessage.Archived(count)
        }
    }

    fun onSortChange(sort: ListSort) {
        viewModelScope.launch { settings.setListSort(sort) }
    }

    /** A drag ended: [orderedIds] is now the order under the header of [categoryId]. */
    fun onReorder(categoryId: CategoryId?, orderedIds: List<TaskId>) {
        viewModelScope.launch { reorderTasks(categoryId, orderedIds).onFailure(::report) }
    }

    /** Dragged to the right: [id] goes under [parentId], the task just above it. */
    fun onMakeSubTask(id: TaskId, parentId: TaskId) {
        viewModelScope.launch { makeSubTask(id, parentId).onFailure(::report) }
    }

    /** A sub task dragged to the left: it leaves its parent. */
    fun onMoveOutOfParent(id: TaskId) {
        viewModelScope.launch { moveOutOfParent(id).onFailure(::report) }
    }

    /** The sub-task count next to a parent was tapped: fold its sub tasks away, or show them again. */
    fun onToggleSubTasks(parentId: TaskId) {
        viewModelScope.launch {
            val now = uiState.value.collapsed
            val toggled = if (parentId in now) now - parentId else now + parentId
            // Only parents that still have sub tasks, so ids of deleted tasks don't pile up.
            val parents = uiState.value.subTasks.filterValues { it.isNotEmpty() }.keys
            settings.setCollapsedParents(toggled.filter { it in parents || it == parentId }.mapTo(mutableSetOf()) { it.value })
        }
    }

    /** A drag inside a parent ended: [orderedIds] is now the order of its open sub tasks. */
    fun onReorderSubTasks(parentId: TaskId, orderedIds: List<TaskId>) {
        viewModelScope.launch { reorderSubTasks(parentId, orderedIds) }
    }

    /** Long press: enters selection mode, or adds/removes a task once in it. Tap does the same in selection mode. */
    override fun onToggleSelection(id: TaskId) = selected.update { if (id in it) it - id else it + id }

    override fun onClearSelection() {
        selected.value = emptySet()
    }

    override fun onDeleteSelected() = editSelection {
        val count = deleteTasks(it)
        if (count > 0) message.value = UserMessage.Deleted(count)
    }

    override fun onSetSelectedPriority(priority: Priority) = editSelection { setTasksPriority(it, priority) }

    override fun onMoveSelected(categoryId: CategoryId?) = editSelection { moveTasksToCategory(it, categoryId).onFailure(::report) }

    /** Applies a bulk change to the current selection, then leaves selection mode. */
    private fun editSelection(change: suspend (Set<TaskId>) -> Unit) {
        val ids = uiState.value.selected
        if (ids.isEmpty()) return
        selected.value = emptySet()
        viewModelScope.launch { change(ids) }
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun report(error: Throwable) {
        message.value = UserMessage.Error(error.asDomainError())
    }

    private fun groupsFor(list: TaskList, headers: Boolean, open: List<TaskItem>, categories: List<Category>): List<TaskGroup> = when {
        list is TaskList.InCategory -> listOf(TaskGroup(list.id, label = null, open))
        !headers -> listOf(TaskGroup(null, label = null, open))
        else -> {
            val known = categories.map { it.id }.toSet()
            // A task whose category vanished mid-update shows as uncategorized, like its row label.
            val byCategory = open.groupBy { item -> item.task.categoryId?.takeIf { it in known } }
            categories.mapNotNull { category ->
                byCategory[category.id]?.let { TaskGroup(category.id, CategoryLabel.Named(category.name), it) }
            } + listOfNotNull(byCategory[null]?.let { TaskGroup(null, CategoryLabel.Uncategorized, it) })
        }
    }

    private fun titleFor(list: TaskList, categories: Map<CategoryId, Category>): ListTitle = when (list) {
        TaskList.All -> ListTitle.All
        TaskList.Uncategorized -> ListTitle.Uncategorized
        is TaskList.InCategory -> categories[list.id]?.let { ListTitle.Category(it.name) } ?: ListTitle.Missing
    }
}
