package dev.mathieu.youcandoit.ui

import androidx.lifecycle.SavedStateHandle
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
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
import dev.mathieu.youcandoit.ui.editor.TaskEditorViewModel
import dev.mathieu.youcandoit.ui.navigation.ListKey
import dev.mathieu.youcandoit.ui.navigation.NavArgs
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TaskEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val env = TestEnv()

    // NOW is 2026-11-01T12:00Z, i.e. 07:00 in Montreal (EST, UTC-5, since DST ended that night).
    private val zone: ZoneId = ZoneId.of("America/Montreal")

    private fun viewModel(vararg args: Pair<String, String>) = TaskEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(*args)),
        observeCategories = ObserveCategories(env.categories),
        getTask = GetTask(env.tasks),
        addTask = AddTask(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("task")),
        addTasks = AddTasks(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("tasks")),
        updateTask = UpdateTask(env.tasks, env.categories, env.reminders, env.clock),
        deleteTask = DeleteTask(env.tasks, env.reminders),
        createCategory = CreateCategory(env.categories, env.clock, SequentialIds("cat")),
        observeSubTasks = ObserveSubTasks(env.tasks),
        parentChoices = ParentChoices(env.tasks, env.categories),
        makeSubTask = MakeSubTask(env.tasks),
        moveOutOfParent = MoveOutOfParent(env.tasks),
        clock = env.clock,
        zone = zone,
    )

    @Test
    fun `a new task opened from a category list starts in that category`() = runTest {
        env.categories.upsert(aCategory("work", "Work"))

        val vm = viewModel(NavArgs.LIST to ListKey.encode(TaskList.InCategory(CategoryId("work"))))

        val state = vm.uiState.value
        assertFalse(state.isEditing)
        assertEquals(CategoryId("work"), state.categoryId)
        assertEquals(listOf("Work"), state.categories.map { it.name })
    }

    @Test
    fun `turning the deadline on defaults to today at 23 59 local time`() = runTest {
        val vm = viewModel()

        vm.onDeadlineToggle(true)

        assertEquals(LocalDate.of(2026, 11, 1), vm.uiState.value.date)
        assertEquals(LocalTime.of(23, 59), vm.uiState.value.time)
    }

    @Test
    fun `several at once adds one task per line with the shared priority and category`() = runTest {
        env.categories.upsert(aCategory("work", "Work"))
        val vm = viewModel()
        vm.onSeveralAtOnceChange(true)
        vm.onTitleChange("Milk\n\n  Eggs \nBread")
        vm.onPriorityChange(Priority.HIGH)
        vm.onCategoryChange(CategoryId("work"))

        assertEquals(listOf("Milk", "Eggs", "Bread"), vm.uiState.value.newTitles)
        vm.onSave()

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(setOf("Milk", "Eggs", "Bread"), env.tasks.all.map { it.title }.toSet())
        assertTrue(env.tasks.all.all { it.priority == Priority.HIGH && it.categoryId == CategoryId("work") })
    }

    @Test
    fun `turning several at once off joins the lines into one title`() = runTest {
        val vm = viewModel()
        vm.onSeveralAtOnceChange(true)
        vm.onTitleChange("Milk\nEggs")

        vm.onSeveralAtOnceChange(false)

        assertEquals("Milk Eggs", vm.uiState.value.title)
    }

    @Test
    fun `save and add another keeps priority and category, clears the rest, and stays open`() = runTest {
        env.categories.upsert(aCategory("work", "Work"))
        val vm = viewModel()
        vm.onTitleChange("First")
        vm.onDescriptionChange("Notes")
        vm.onPriorityChange(Priority.URGENT)
        vm.onCategoryChange(CategoryId("work"))
        vm.onDeadlineToggle(true)

        vm.onSaveAndAddAnother()

        val state = vm.uiState.value
        assertFalse(state.isFinished)
        assertEquals(1, state.justAdded)
        assertEquals("", state.title)
        assertEquals("", state.description)
        assertFalse(state.hasDeadline)
        assertEquals(Priority.URGENT, state.priority)
        assertEquals(CategoryId("work"), state.categoryId)
        assertEquals(listOf("First"), env.tasks.all.map { it.title })

        vm.onAddedShown()
        assertEquals(null, vm.uiState.value.justAdded)
    }

    @Test
    fun `a new sub task names its parent and is saved under it, in its category`() = runTest {
        env.categories.upsert(aCategory("work", "Work"))
        env.tasks.upsert(aTask("p", categoryId = CategoryId("work")).copy(title = "Move house"))
        val vm = viewModel(NavArgs.PARENT to "p")

        assertEquals("Move house", vm.uiState.value.parentTitle)
        vm.onTitleChange("Book the van")
        vm.onSave()

        val sub = env.tasks.all.single { it.title == "Book the van" }
        assertEquals(TaskId("p"), sub.parentId)
        assertEquals(CategoryId("work"), sub.categoryId)
    }

    @Test
    fun `editing a parent lists its sub tasks and offers to add more`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel(NavArgs.TASK_ID to "p")

        assertEquals(listOf("c"), vm.uiState.value.subTasks.map { it.id.value })
        assertTrue(vm.uiState.value.canAddSubTasks)
    }

    @Test
    fun `a sub task can't take sub tasks of its own`() = runTest {
        env.tasks.upsert(aTask("p").copy(title = "Parent"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel(NavArgs.TASK_ID to "c")

        assertFalse(vm.uiState.value.canAddSubTasks)
        assertEquals("Parent", vm.uiState.value.parentTitle)
    }

    @Test
    fun `an existing task can be put under another, taking its category, and saved with its edits`() = runTest {
        env.categories.upsert(aCategory("work", "Work"))
        env.tasks.upsert(aTask("p", categoryId = CategoryId("work")).copy(title = "Move house"))
        env.tasks.upsert(aTask("t"))
        val vm = viewModel(NavArgs.TASK_ID to "t")

        assertTrue(vm.uiState.value.canChangeParent)
        assertEquals(listOf("p"), vm.uiState.value.parentChoices.map { it.id.value })
        vm.onParentChange(TaskId("p"))
        assertEquals("Move house", vm.uiState.value.parentTitle)
        vm.onTitleChange("Book the van")
        vm.onSave()

        val saved = env.tasks.findById(TaskId("t"))!!
        assertEquals("Book the van", saved.title)
        assertEquals(TaskId("p"), saved.parentId)
        assertEquals(CategoryId("work"), saved.categoryId)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun `a sub task can be made a main task again, in the category picked`() = runTest {
        env.categories.upsert(aCategory("home", "Home"))
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel(NavArgs.TASK_ID to "c")

        assertEquals(TaskId("p"), vm.uiState.value.parentId)
        vm.onParentChange(null)
        assertNull(vm.uiState.value.parentTitle)
        vm.onCategoryChange(CategoryId("home"))
        vm.onSave()

        val saved = env.tasks.findById(TaskId("c"))!!
        assertNull(saved.parentId)
        assertEquals(CategoryId("home"), saved.categoryId)
    }

    @Test
    fun `a task with sub tasks of its own can't go under another`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        env.tasks.upsert(aTask("other"))
        val vm = viewModel(NavArgs.TASK_ID to "p")

        assertFalse(vm.uiState.value.canChangeParent)
    }

    @Test
    fun `saving a new task converts the deadline with the device time zone`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Quarterly report")
        vm.onPriorityChange(Priority.URGENT)
        vm.onDeadlineToggle(true)
        vm.onDateChange(LocalDate.of(2026, 11, 3))
        vm.onTimeChange(LocalTime.of(17, 0))
        vm.onReminderChange(Reminder.BeforeDeadline(Duration.ofHours(1)))

        vm.onSave()

        assertTrue(vm.uiState.value.isFinished)
        val task = env.tasks.all.single()
        assertEquals("Quarterly report", task.title)
        assertEquals(Priority.URGENT, task.priority)
        assertEquals(Instant.parse("2026-11-03T22:00:00Z"), task.deadline)
        assertEquals(Reminder.BeforeDeadline(Duration.ofHours(1)), task.reminder)
        assertEquals(Instant.parse("2026-11-03T21:00:00Z"), env.scheduler.scheduled[task.id])
    }

    @Test
    fun `a reminder on a chosen date and time needs no deadline, and stays when the deadline is turned off`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Call mom")
        vm.onDeadlineToggle(true)
        vm.onReminderTimeChange(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
        vm.onDeadlineToggle(false)
        assertEquals(listOf(Reminder.None), vm.uiState.value.reminderOptions)

        vm.onSave()

        val task = env.tasks.all.single()
        assertNull(task.deadline)
        assertEquals(Reminder.At(Instant.parse("2026-11-02T14:00:00Z")), task.reminder) // 9:00 in Montreal
        assertEquals(Instant.parse("2026-11-02T14:00:00Z"), env.scheduler.scheduled[task.id])
    }

    @Test
    fun `turning the deadline off also drops the reminder`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Call mom")
        vm.onDeadlineToggle(true)
        vm.onReminderChange(Reminder.AtDeadline)
        vm.onDeadlineToggle(false)

        vm.onSave()

        val task = env.tasks.all.single()
        assertNull(task.deadline)
        assertEquals(Reminder.None, task.reminder)
    }

    @Test
    fun `a blank title is reported and nothing is saved`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("   ")

        vm.onSave()

        assertIs<DomainError.BlankTitle>(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isFinished)
        assertTrue(env.tasks.all.isEmpty())

        vm.onTitleChange("Fixed")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `editing loads the task in local time and saves changes in place`() = runTest {
        val original = aTask("a", deadline = NOW.plus(Duration.ofDays(2)), reminder = Reminder.AtDeadline)
        env.tasks.upsert(original)

        val vm = viewModel(NavArgs.TASK_ID to "a")

        val loaded = vm.uiState.value
        assertTrue(loaded.isEditing)
        assertFalse(loaded.isLoading)
        assertEquals("Task a", loaded.title)
        assertTrue(loaded.hasDeadline)
        assertEquals(LocalDate.of(2026, 11, 3), loaded.date)
        assertEquals(LocalTime.of(7, 0), loaded.time)
        assertEquals(Reminder.AtDeadline, loaded.reminder)

        vm.onTitleChange("Renamed")
        vm.onSave()

        val saved = env.tasks.findById(TaskId("a"))!!
        assertEquals("Renamed", saved.title)
        assertEquals(original.deadline, saved.deadline)
        assertEquals(original.createdAt, saved.createdAt)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun `an unusual reminder offset is kept as an extra option`() = runTest {
        val custom = Reminder.BeforeDeadline(Duration.ofMinutes(30))
        env.tasks.upsert(aTask("a", deadline = NOW.plus(Duration.ofDays(1)), reminder = custom))

        val vm = viewModel(NavArgs.TASK_ID to "a")

        assertEquals(custom, vm.uiState.value.reminderOptions.last())
    }

    @Test
    fun `deleting a task closes the editor`() = runTest {
        env.tasks.upsert(aTask("a"))
        val vm = viewModel(NavArgs.TASK_ID to "a")

        vm.onDelete()

        assertTrue(vm.uiState.value.isFinished)
        assertNull(env.tasks.findById(TaskId("a")))
    }

    @Test
    fun `a category created from the editor gets selected`() = runTest {
        val vm = viewModel()

        vm.onNewCategoryClick()
        vm.onNewCategoryNameChange("Garden")
        vm.onNewCategoryConfirm()

        assertNull(vm.uiState.value.newCategory)
        assertEquals(CategoryId("cat-1"), vm.uiState.value.categoryId)
        assertEquals(listOf("Garden"), vm.uiState.value.categories.map { it.name })
    }

    @Test
    fun `editing a task due in the repeated hour of a DST change keeps its exact deadline`() = runTest {
        // 2026-11-01 01:30 happens twice in Montreal; 06:30Z is the second time (EST).
        val secondOneThirty = Instant.parse("2026-11-01T06:30:00Z")
        env.tasks.upsert(aTask("a", deadline = secondOneThirty)) // overdue: NOW is 12:00Z
        val vm = viewModel(NavArgs.TASK_ID to "a")

        vm.onTitleChange("Renamed")
        vm.onSave()

        assertNull(vm.uiState.value.error)
        assertEquals(secondOneThirty, env.tasks.findById(TaskId("a"))?.deadline)
    }

    @Test
    fun `picking a reminder that is already too late explains it and saves at the deadline`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Pay rent")
        vm.onDeadlineToggle(true)
        vm.onDateChange(LocalDate.of(2026, 11, 1))
        vm.onTimeChange(LocalTime.of(10, 0)) // 3 hours from now (07:00 local)
        vm.onReminderChange(Reminder.BeforeDeadline(Duration.ofDays(1)))

        assertTrue(vm.uiState.value.reminderFallsBackToDeadline)

        vm.onSave()

        assertEquals(Reminder.AtDeadline, env.tasks.all.single().reminder)
    }

    @Test
    fun `the fallback note does not show for an untouched reminder that already fired`() = runTest {
        env.tasks.upsert(aTask("a", deadline = NOW.plus(Duration.ofMinutes(30)), reminder = Reminder.BeforeDeadline(Duration.ofHours(1))))
        val vm = viewModel(NavArgs.TASK_ID to "a")

        vm.onTitleChange("Renamed")

        assertFalse(vm.uiState.value.reminderFallsBackToDeadline)
    }
}
