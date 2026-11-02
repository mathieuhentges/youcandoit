package dev.mathieu.youcandoit.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.reminder.ReminderPolicy
import dev.mathieu.youcandoit.domain.usecase.AddTask
import dev.mathieu.youcandoit.domain.usecase.AddTasks
import dev.mathieu.youcandoit.domain.usecase.CreateCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteTask
import dev.mathieu.youcandoit.domain.usecase.GetTask
import dev.mathieu.youcandoit.domain.usecase.MakeSubTask
import dev.mathieu.youcandoit.domain.usecase.MoveOutOfParent
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveSubTasks
import dev.mathieu.youcandoit.domain.usecase.ParentChoices
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
    /** New tasks only: [title] holds one task per line, and the other fields apply to all of them. */
    val severalAtOnce: Boolean = false,
    /** How many tasks "Save and add another" just added, for a one-off confirmation. */
    val justAdded: Int? = null,
    /** Set for a sub task (new or edited): its parent's title, shown instead of the category picker. */
    val parentTitle: String? = null,
    /** Editing a top-level task: its sub tasks, open first. */
    val subTasks: List<Task> = emptyList(),
    /** Editing an open, top-level task: it can take more sub tasks. */
    val canAddSubTasks: Boolean = false,
    /** Editing an open task: the task it's under (null for a main task), changeable through [parentChoices]. */
    val parentId: TaskId? = null,
    val parentChoices: List<Task> = emptyList(),
) {
    /** One level only: a task with sub tasks of its own stays a main task. */
    val canChangeParent: Boolean get() = isEditing && subTasks.isEmpty() && (parentChoices.isNotEmpty() || parentId != null)

    /** The titles a save would add: one per non-blank line in "several" mode, else just [title]. */
    val newTitles: List<String> get() =
        if (severalAtOnce) title.lines().map { it.trim() }.filter { it.isNotEmpty() } else listOfNotNull(title.takeIf { it.isNotBlank() })

    val canSave: Boolean get() = newTitles.isNotEmpty() && !isSaving && !isLoading

    /**
     * The preset chips: relative to the deadline when there is one (plus the task's own "before" reminder
     * if it isn't a preset), else just None. A chosen date and time ([Reminder.At]) has its own chip.
     */
    val reminderOptions: List<Reminder> get() = when {
        !hasDeadline -> listOf(Reminder.None)
        reminder !is Reminder.BeforeDeadline || reminder in REMINDER_PRESETS -> REMINDER_PRESETS
        else -> REMINDER_PRESETS + reminder
    }

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
    fun onParentChange(parentId: TaskId?)
    fun onDeadlineToggle(enabled: Boolean)
    fun onDateChange(date: LocalDate)
    fun onTimeChange(time: LocalTime)
    fun onReminderChange(reminder: Reminder)
    fun onReminderTimeChange(date: LocalDate, time: LocalTime)
    fun onSave()
    fun onSaveAndAddAnother()
    fun onAddedShown()
    fun onSeveralAtOnceChange(enabled: Boolean)
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
    private val addTasks: AddTasks,
    private val updateTask: UpdateTask,
    private val deleteTask: DeleteTask,
    private val createCategory: CreateCategory,
    private val observeSubTasks: ObserveSubTasks,
    private val parentChoices: ParentChoices,
    private val makeSubTask: MakeSubTask,
    private val moveOutOfParent: MoveOutOfParent,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel(), TaskEditorActions {
    val taskId: TaskId? = savedStateHandle.get<String>(NavArgs.TASK_ID)?.let { TaskId(it) }
    private val startingList: TaskList = ListKey.decode(savedStateHandle.get<String>(NavArgs.LIST))
    private val newParentId: TaskId? = savedStateHandle.get<String>(NavArgs.PARENT)?.let { TaskId(it) }

    /** The task as loaded, to tell unchanged fields from edited ones. */
    private var loaded: Loaded? = null

    private data class Loaded(val date: LocalDate?, val time: LocalTime, val deadline: Instant?, val reminder: Reminder, val parentId: TaskId?)

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
        if (newParentId != null) viewModelScope.launch { showParent(newParentId) }
    }

    private suspend fun showParent(id: TaskId) {
        getTask(id).onSuccess { parent -> state.update { it.copy(parentTitle = parent.title, categoryId = parent.categoryId) } }
    }

    private suspend fun load(id: TaskId) {
        getTask(id).fold(
            onSuccess = { task ->
                val local = task.deadline?.atZone(zone)
                val loadedTime = local?.toLocalTime()?.withSecond(0)?.withNano(0) ?: TaskEditorUiState.DEFAULT_TIME
                loaded = Loaded(local?.toLocalDate(), loadedTime, task.deadline, task.reminder, task.parentId)
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
                        canAddSubTasks = task.isOpen && !task.isSubTask,
                        parentId = task.parentId,
                    )
                }
                if (task.isOpen) {
                    viewModelScope.launch {
                        val choices = parentChoices(id)
                        state.update { it.copy(parentChoices = choices) }
                    }
                }
                val parentId = task.parentId
                if (parentId != null) {
                    viewModelScope.launch { showParent(parentId) }
                } else {
                    viewModelScope.launch {
                        observeSubTasks(id).collect { children -> state.update { it.copy(subTasks = children) } }
                    }
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

    /** Under a parent the task takes its category; back to a main task, it keeps that category to start with. */
    override fun onParentChange(parentId: TaskId?) = state.update { form ->
        val parent = form.parentChoices.firstOrNull { it.id == parentId }
        form.copy(
            parentId = parent?.id,
            parentTitle = parent?.title,
            categoryId = parent?.categoryId ?: form.categoryId,
            error = null,
        )
    }

    override fun onDeadlineToggle(enabled: Boolean) = edit {
        if (enabled) {
            it.copy(hasDeadline = true, date = it.date ?: today(), error = null)
        } else {
            // A reminder relative to the deadline goes with it; one at a chosen time stays.
            it.copy(hasDeadline = false, reminder = if (it.reminder.needsDeadline) Reminder.None else it.reminder, error = null)
        }
    }

    override fun onDateChange(date: LocalDate) = edit { it.copy(date = date, error = null) }

    override fun onTimeChange(time: LocalTime) = edit { it.copy(time = time, error = null) }

    override fun onReminderChange(reminder: Reminder) = edit { it.copy(reminder = reminder, error = null) }

    override fun onReminderTimeChange(date: LocalDate, time: LocalTime) =
        onReminderChange(Reminder.At(date.atTime(time).atZone(zone).toInstant()))

    override fun onSave() = save(addAnother = false)

    override fun onSaveAndAddAnother() = save(addAnother = true)

    override fun onAddedShown() = state.update { it.copy(justAdded = null) }

    override fun onSeveralAtOnceChange(enabled: Boolean) = state.update {
        // Back to one task: the lines become one title rather than being lost.
        val title = if (enabled) it.title else it.newTitles.joinToString(" ")
        it.copy(severalAtOnce = enabled, title = title, description = if (enabled) "" else it.description, error = null)
    }

    private fun save(addAnother: Boolean) {
        val current = state.value
        if (current.isSaving) return
        val deadline = deadlineOf(current)
        // No deadline means no reminder relative to one, whatever was picked before the switch was turned off.
        val reminder = if (deadline == null && current.reminder.needsDeadline) Reminder.None else current.reminder
        state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            val result: Result<Int> = when {
                taskId != null -> saveEdit(taskId, current, deadline, reminder).map { 1 }
                current.severalAtOnce ->
                    addTasks(AddTasks.Params(current.newTitles, current.priority, current.categoryId, deadline, reminder, newParentId))
                        .map { it.size }
                else ->
                    addTask(AddTask.Params(current.title, current.description, current.priority, current.categoryId, deadline, reminder, newParentId))
                        .map { 1 }
            }
            result.fold(
                onSuccess = { count ->
                    if (addAnother && taskId == null) {
                        // Ready for the next one: same priority and category, a fresh title and deadline.
                        edit {
                            it.copy(
                                isSaving = false,
                                title = "",
                                description = "",
                                hasDeadline = false,
                                date = null,
                                time = TaskEditorUiState.DEFAULT_TIME,
                                reminder = Reminder.None,
                                justAdded = count,
                            )
                        }
                    } else {
                        state.update { it.copy(isSaving = false, isFinished = true) }
                    }
                },
                onFailure = { error -> state.update { it.copy(isSaving = false, error = error.asDomainError()) } },
            )
        }
    }

    /**
     * Out of a parent first, so the update sees a main task and can place it in the picked category;
     * under a new parent last, so the update doesn't undo the parent's category.
     */
    private suspend fun saveEdit(id: TaskId, form: TaskEditorUiState, deadline: Instant?, reminder: Reminder): Result<Unit> {
        val parentChanged = form.parentId != loaded?.parentId
        if (parentChanged && form.parentId == null) moveOutOfParent(id).onFailure { return Result.failure(it) }
        updateTask(UpdateTask.Params(id, form.title, form.description, form.priority, form.categoryId, deadline, reminder))
            .onFailure { return Result.failure(it) }
        if (parentChanged && form.parentId != null) return makeSubTask(id, form.parentId)
        return Result.success(Unit)
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
