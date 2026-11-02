package dev.mathieu.youcandoit.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.reminder.ReminderPolicy
import dev.mathieu.youcandoit.domain.usecase.AddTask
import dev.mathieu.youcandoit.domain.usecase.CreateCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteTask
import dev.mathieu.youcandoit.domain.usecase.GetTask
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.UpdateTask
import dev.mathieu.youcandoit.ui.common.asDomainError
import dev.mathieu.youcandoit.ui.navigation.ListKey
import dev.mathieu.youcandoit.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewCategoryDialog(val name: String = "", val error: DomainError? = null)

data class TaskEditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Saved or deleted: the screen closes. */
    val isFinished: Boolean = false,
    val title: String = "",
    val description: String = "",
    val priority: Priority = Priority.MEDIUM,
    val categoryId: CategoryId? = null,
    val categories: List<Category> = emptyList(),
    val hasDeadline: Boolean = false,
    val date: LocalDate? = null,
    val time: LocalTime = DEFAULT_TIME,
    val reminder: Reminder = Reminder.None,
    val error: DomainError? = null,
    val newCategory: NewCategoryDialog? = null,
    /** The picked "before" reminder is already too late; it will be set at the deadline instead. */
    val reminderFallsBackToDeadline: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving && !isLoading

    /** The preset choices, plus the task's own reminder if it isn't one of them. */
    val reminderOptions: List<Reminder> get() = if (reminder in REMINDER_PRESETS) REMINDER_PRESETS else REMINDER_PRESETS + reminder

    companion object {
        /** Agreed default: a deadline without a picked time is at the end of the day. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(23, 59)
        val REMINDER_PRESETS: List<Reminder> = listOf(
            Reminder.None,
            Reminder.AtDeadline,
            Reminder.BeforeDeadline(Duration.ofHours(1)),
            Reminder.BeforeDeadline(Duration.ofDays(1)),
        )
    }
}

/** What the editor screen can ask for; implemented by the ViewModel, easy to fake in previews. */
interface TaskEditorActions {
    fun onTitleChange(title: String)
    fun onDescriptionChange(description: String)
    fun onPriorityChange(priority: Priority)
    fun onCategoryChange(categoryId: CategoryId?)
    fun onDeadlineToggle(enabled: Boolean)
    fun onDateChange(date: LocalDate)
    fun onTimeChange(time: LocalTime)
    fun onReminderChange(reminder: Reminder)
    fun onSave()
    fun onDelete()
    fun onNewCategoryClick()
    fun onNewCategoryNameChange(name: String)
    fun onNewCategoryConfirm()
    fun onNewCategoryDismiss()
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCategories: ObserveCategories,
    private val getTask: GetTask,
    private val addTask: AddTask,
    private val updateTask: UpdateTask,
    private val deleteTask: DeleteTask,
    private val createCategory: CreateCategory,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel(), TaskEditorActions {
    private val taskId: TaskId? = savedStateHandle.get<String>(NavArgs.TASK_ID)?.let { TaskId(it) }
    private val startingList: TaskList = ListKey.decode(savedStateHandle.get<String>(NavArgs.LIST))

    /** The task as loaded, to tell unchanged fields from edited ones. */
    private var loaded: Loaded? = null

    private data class Loaded(val date: LocalDate?, val time: LocalTime, val deadline: Instant?, val reminder: Reminder)

    private val state = MutableStateFlow(
        TaskEditorUiState(
            isEditing = taskId != null,
            isLoading = taskId != null,
            categoryId = (startingList as? TaskList.InCategory)?.id,
        ),
    )
    val uiState: StateFlow<TaskEditorUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeCategories().collect { categories -> state.update { it.copy(categories = categories) } }
        }
        if (taskId != null) viewModelScope.launch { load(taskId) }
    }

    private suspend fun load(id: TaskId) {
        getTask(id).fold(
            onSuccess = { task ->
                val local = task.deadline?.atZone(zone)
                val loadedTime = local?.toLocalTime()?.withSecond(0)?.withNano(0) ?: TaskEditorUiState.DEFAULT_TIME
                loaded = Loaded(local?.toLocalDate(), loadedTime, task.deadline, task.reminder)
                edit {
                    it.copy(
                        isLoading = false,
                        title = task.title,
                        description = task.description,
                        priority = task.priority,
                        categoryId = task.categoryId,
                        hasDeadline = local != null,
                        date = local?.toLocalDate(),
                        time = loadedTime,
                        reminder = task.reminder,
                    )
                }
            },
            onFailure = { error -> state.update { it.copy(isLoading = false, error = error.asDomainError()) } },
        )
    }

    /** Every form change goes through here, so derived fields stay current. */
    private fun edit(change: (TaskEditorUiState) -> TaskEditorUiState) = state.update { derive(change(it)) }

    private fun derive(form: TaskEditorUiState): TaskEditorUiState {
        val deadline = deadlineOf(form)
        val previous = loaded
        val reminderChanged = previous == null || form.reminder != previous.reminder || deadline != previous.deadline
        val fallsBack = reminderChanged &&
            ReminderPolicy.effectiveReminder(form.reminder, deadline, clock.instant()) != form.reminder
        return form.copy(reminderFallsBackToDeadline = fallsBack)
    }

    /**
     * The deadline the form describes. If the date and time weren't touched, it's the loaded instant
     * itself: converting back through the time zone could pick the other side of a daylight-saving
     * change for times like 1:30 on the night clocks go back.
     */
    private fun deadlineOf(form: TaskEditorUiState): Instant? {
        if (!form.hasDeadline) return null
        val date = form.date ?: return null
        val previous = loaded
        if (previous != null && previous.date == date && previous.time == form.time) return previous.deadline
        return date.atTime(form.time).atZone(zone).toInstant()
    }

    override fun onTitleChange(title: String) = state.update { it.copy(title = title, error = null) }

    override fun onDescriptionChange(description: String) = state.update { it.copy(description = description) }

    override fun onPriorityChange(priority: Priority) = state.update { it.copy(priority = priority) }

    override fun onCategoryChange(categoryId: CategoryId?) = state.update { it.copy(categoryId = categoryId, error = null) }

    override fun onDeadlineToggle(enabled: Boolean) = edit {
        if (enabled) {
            it.copy(hasDeadline = true, date = it.date ?: today(), error = null)
        } else {
            it.copy(hasDeadline = false, error = null)
        }
    }

    override fun onDateChange(date: LocalDate) = edit { it.copy(date = date, error = null) }

    override fun onTimeChange(time: LocalTime) = edit { it.copy(time = time, error = null) }

    override fun onReminderChange(reminder: Reminder) = edit { it.copy(reminder = reminder, error = null) }

    override fun onSave() {
        val current = state.value
        if (current.isSaving) return
        val deadline = deadlineOf(current)
        // No deadline means no reminder, whatever was picked before the switch was turned off.
        val reminder = if (deadline == null) Reminder.None else current.reminder
        state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            val result = if (taskId == null) {
                addTask(AddTask.Params(current.title, current.description, current.priority, current.categoryId, deadline, reminder))
            } else {
                updateTask(UpdateTask.Params(taskId, current.title, current.description, current.priority, current.categoryId, deadline, reminder))
            }
            result.fold(
                onSuccess = { state.update { it.copy(isSaving = false, isFinished = true) } },
                onFailure = { error -> state.update { it.copy(isSaving = false, error = error.asDomainError()) } },
            )
        }
    }

    override fun onDelete() {
        val id = taskId ?: return
        viewModelScope.launch {
            deleteTask(id).fold(
                onSuccess = { state.update { it.copy(isFinished = true) } },
                onFailure = { error -> state.update { it.copy(error = error.asDomainError()) } },
            )
        }
    }

    override fun onNewCategoryClick() = state.update { it.copy(newCategory = NewCategoryDialog()) }

    override fun onNewCategoryNameChange(name: String) = state.update {
        it.copy(newCategory = it.newCategory?.copy(name = name, error = null))
    }

    override fun onNewCategoryDismiss() = state.update { it.copy(newCategory = null) }

    private var creatingCategory = false

    override fun onNewCategoryConfirm() {
        val dialog = state.value.newCategory ?: return
        if (creatingCategory) return // a second tap while the first is saving
        creatingCategory = true
        viewModelScope.launch {
            try {
                createCategory(dialog.name).fold(
                    onSuccess = { category -> state.update { it.copy(newCategory = null, categoryId = category.id) } },
                    onFailure = { error -> state.update { it.copy(newCategory = dialog.copy(error = error.asDomainError())) } },
                )
            } finally {
                creatingCategory = false
            }
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()
}
