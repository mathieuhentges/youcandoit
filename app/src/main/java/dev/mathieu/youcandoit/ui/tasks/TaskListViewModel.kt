package dev.mathieu.youcandoit.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.usecase.ArchiveDoneTasks
import dev.mathieu.youcandoit.domain.usecase.ArchiveTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.MarkTaskNotDone
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveTaskList
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.common.Ticker
import dev.mathieu.youcandoit.ui.common.UserMessage
import dev.mathieu.youcandoit.ui.common.asDomainError
import dev.mathieu.youcandoit.ui.common.toItem
import dev.mathieu.youcandoit.ui.navigation.ListKey
import dev.mathieu.youcandoit.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ListTitle {
    data object All : ListTitle
    data object Uncategorized : ListTitle
    data class Category(val name: String) : ListTitle

    /** The category was deleted while open; the screen closes itself. */
    data object Missing : ListTitle
}

data class TaskListUiState(
    val isLoading: Boolean = true,
    val list: TaskList = TaskList.All,
    val title: ListTitle = ListTitle.All,
    val open: List<TaskItem> = emptyList(),
    val done: List<TaskItem> = emptyList(),
    val message: UserMessage? = null,
) {
    val counts: ListCounts get() = ListCounts(open = open.size, overdue = open.count { it.isOverdue }, done = done.size)
}

/** Home (All tasks) and a single category's list. */
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
) : ViewModel() {
    val list: TaskList = ListKey.decode(savedStateHandle.get<String>(NavArgs.LIST))
    private val message = MutableStateFlow<UserMessage?>(null)

    val uiState: StateFlow<TaskListUiState> =
        // The ticker re-evaluates "overdue" every minute, not only when data changes.
        combine(observeTaskList(list), observeCategories(), message, ticker.minutes) { view, categories, msg, now ->
            val byId = categories.associateBy { it.id }
            val showCategory = list !is TaskList.InCategory
            TaskListUiState(
                isLoading = false,
                list = list,
                title = titleFor(list, byId),
                open = view.open.map { it.toItem(byId, showCategory, now) },
                done = view.done.map { it.toItem(byId, showCategory, now) },
                message = msg,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState(list = list))

    fun onToggleDone(item: TaskItem) {
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

    fun onMessageShown() {
        message.value = null
    }

    private fun report(error: Throwable) {
        message.value = UserMessage.Error(error.asDomainError())
    }

    private fun titleFor(list: TaskList, categories: Map<CategoryId, Category>): ListTitle = when (list) {
        TaskList.All -> ListTitle.All
        TaskList.Uncategorized -> ListTitle.Uncategorized
        is TaskList.InCategory -> categories[list.id]?.let { ListTitle.Category(it.name) } ?: ListTitle.Missing
    }
}
