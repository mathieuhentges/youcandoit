package dev.mathieu.youcandoit.ui

import androidx.lifecycle.SavedStateHandle
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.DoneOrder
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.usecase.ArchiveDoneTasks
import dev.mathieu.youcandoit.domain.usecase.ArchiveTask
import dev.mathieu.youcandoit.domain.usecase.DeleteTasks
import dev.mathieu.youcandoit.domain.usecase.MakeSubTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.MarkTaskNotDone
import dev.mathieu.youcandoit.domain.usecase.MoveOutOfParent
import dev.mathieu.youcandoit.domain.usecase.MoveTasksToCategory
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveTaskList
import dev.mathieu.youcandoit.domain.usecase.ReorderSubTasks
import dev.mathieu.youcandoit.domain.usecase.ReorderTasks
import dev.mathieu.youcandoit.domain.usecase.SetTasksPriority
import dev.mathieu.youcandoit.settings.FakeSettings
import dev.mathieu.youcandoit.settings.ListSort
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.common.SubTaskCount
import dev.mathieu.youcandoit.ui.common.UserMessage
import dev.mathieu.youcandoit.ui.navigation.ListKey
import dev.mathieu.youcandoit.ui.navigation.NavArgs
import dev.mathieu.youcandoit.ui.tasks.ListTitle
import dev.mathieu.youcandoit.ui.tasks.TaskListViewModel
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TaskListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val env = TestEnv()
    private val settings = FakeSettings()
    private val ticker = FakeTicker(env.clock)
    private val work = aCategory("work", "Work")

    private fun viewModel(list: TaskList = TaskList.All) = TaskListViewModel(
        savedStateHandle = SavedStateHandle(mapOf(NavArgs.LIST to ListKey.encode(list))),
        observeTaskList = ObserveTaskList(env.tasks, env.categories),
        observeCategories = ObserveCategories(env.categories),
        ticker = ticker,
        markTaskDone = MarkTaskDone(env.tasks, env.reminders, env.clock),
        markTaskNotDone = MarkTaskNotDone(env.tasks, env.reminders),
        archiveTask = ArchiveTask(env.tasks, env.clock),
        archiveDoneTasks = ArchiveDoneTasks(env.tasks, env.clock),
        reorderTasks = ReorderTasks(env.tasks, env.categories),
        reorderSubTasks = ReorderSubTasks(env.tasks),
        deleteTasks = DeleteTasks(env.tasks, env.reminders),
        setTasksPriority = SetTasksPriority(env.tasks),
        moveTasksToCategory = MoveTasksToCategory(env.tasks, env.categories),
        makeSubTask = MakeSubTask(env.tasks),
        moveOutOfParent = MoveOutOfParent(env.tasks),
        settings = settings,
    )

    @Test
    fun `home groups open tasks under category headers, uncategorized last, then done tasks`() = runTest {
        val home = aCategory("home", "Home")
        env.categories.upsert(work)
        env.categories.upsert(home)
        env.tasks.upsert(aTask("a", categoryId = work.id))
        env.tasks.upsert(aTask("b", deadline = NOW.minus(Duration.ofHours(1))))
        env.tasks.upsert(aTask("h", categoryId = home.id))
        env.tasks.upsert(aTask("c", categoryId = work.id).markDone(NOW))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ListTitle.All, state.title)
        assertEquals(
            listOf(CategoryLabel.Named("Home"), CategoryLabel.Named("Work"), CategoryLabel.Uncategorized),
            state.groups.map { it.label },
        )
        assertEquals(listOf(listOf("h"), listOf("a"), listOf("b")), state.groups.map { g -> g.items.map { it.task.id.value } })
        // The header already says it; done tasks are ungrouped, so they keep theirs.
        assertNull(state.open.first().category)
        assertEquals(CategoryLabel.Named("Work"), state.done.single().category)
        assertTrue(state.groups.last().items.single().isOverdue)
        assertEquals(ListCounts(open = 3, overdue = 1, done = 1), state.counts)
    }

    @Test
    fun `a category with only done tasks gets no header`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("c", categoryId = work.id).markDone(NOW))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        assertEquals(listOf(CategoryLabel.Uncategorized), vm.uiState.value.groups.map { it.label })
    }

    @Test
    fun `dropping a task under another header saves the order and moves it there`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("w", categoryId = work.id))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onReorder(work.id, listOf(TaskId("a"), TaskId("w")))

        val groups = vm.uiState.value.groups
        assertEquals(listOf(CategoryLabel.Named("Work")), groups.map { it.label })
        assertEquals(listOf("a", "w"), groups.single().items.map { it.task.id.value })
    }

    @Test
    fun `sorted by priority without grouping, home is one flat list whose rows show their category`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("low", priority = Priority.LOW, position = 0))
        env.tasks.upsert(aTask("urgent", categoryId = work.id, priority = Priority.URGENT, position = 0))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onSortChange(ListSort(TaskOrder.PRIORITY, groupByCategory = false))

        val state = vm.uiState.value
        assertEquals(listOf(null), state.groups.map { it.label })
        assertEquals(listOf("urgent", "low"), state.open.map { it.task.id.value })
        assertEquals(listOf(CategoryLabel.Named("Work"), CategoryLabel.Uncategorized), state.open.map { it.category })
        assertEquals(TaskOrder.PRIORITY, settings.listSort.value.order)
    }

    @Test
    fun `the done section has its own order, kept when the open tasks' order changes`() = runTest {
        env.tasks.upsert(aTask("urgent", priority = Priority.URGENT).markDone(NOW))
        env.tasks.upsert(aTask("low", priority = Priority.LOW).markDone(NOW.plusSeconds(60)))
        val vm = viewModel()
        keepCollecting(vm.uiState)
        assertEquals(listOf("low", "urgent"), vm.uiState.value.done.map { it.task.id.value })

        vm.onSortChange(vm.uiState.value.sort.copy(doneOrder = DoneOrder.PRIORITY))
        vm.onSortChange(vm.uiState.value.sort.copy(order = TaskOrder.DEADLINE))

        assertEquals(listOf("urgent", "low"), vm.uiState.value.done.map { it.task.id.value })
        assertEquals(ListSort(TaskOrder.DEADLINE, doneOrder = DoneOrder.PRIORITY), settings.listSort.value)
    }

    @Test
    fun `sorted by deadline with grouping, each header's tasks are by deadline`() = runTest {
        env.tasks.upsert(aTask("later", deadline = NOW.plus(Duration.ofDays(2)), position = 0))
        env.tasks.upsert(aTask("sooner", deadline = NOW.plus(Duration.ofDays(1)), position = 1))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onSortChange(ListSort(TaskOrder.DEADLINE, groupByCategory = true))

        val group = vm.uiState.value.groups.single()
        assertEquals(CategoryLabel.Uncategorized, group.label)
        assertEquals(listOf("sooner", "later"), group.items.map { it.task.id.value })
    }

    @Test
    fun `a task put under another becomes its sub task, and can be taken back out`() = runTest {
        env.tasks.upsert(aTask("a", position = 0))
        env.tasks.upsert(aTask("b", position = 1))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onMakeSubTask(TaskId("b"), TaskId("a"))

        assertEquals(listOf("a"), vm.uiState.value.open.map { it.task.id.value })
        assertEquals(listOf("b"), vm.uiState.value.subTasks.getValue(TaskId("a")).map { it.task.id.value })

        vm.onMoveOutOfParent(TaskId("b"))

        assertEquals(listOf("a", "b"), vm.uiState.value.open.map { it.task.id.value })
    }

    @Test
    fun `a task with its own sub tasks can't go under another, and the user is told`() = runTest {
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onMakeSubTask(TaskId("p"), TaskId("a"))

        assertIs<DomainError.HasSubTasks>((vm.uiState.value.message as UserMessage.Error).error)
    }

    @Test
    fun `folding a parent's sub tasks is remembered, and unfolding brings them back`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onToggleSubTasks(TaskId("p"))

        assertEquals(setOf(TaskId("p")), vm.uiState.value.collapsed)
        assertEquals(setOf("p"), settings.collapsedParents.value)
        // A new screen (or the next launch) starts folded too.
        val again = viewModel()
        keepCollecting(again.uiState)
        assertEquals(setOf(TaskId("p")), again.uiState.value.collapsed)

        vm.onToggleSubTasks(TaskId("p"))

        assertTrue(vm.uiState.value.collapsed.isEmpty())
    }

    @Test
    fun `sub tasks show under their parent with a done count, and count like any task`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c1", parentId = TaskId("p"), position = 0))
        env.tasks.upsert(aTask("c2", parentId = TaskId("p"), position = 1).markDone(NOW))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        val state = vm.uiState.value
        assertEquals(listOf("p"), state.open.map { it.task.id.value })
        assertEquals(SubTaskCount(done = 1, total = 2), state.open.single().subTasks)
        assertEquals(listOf("c1", "c2"), state.subTasks.getValue(TaskId("p")).map { it.task.id.value })
        assertTrue(state.done.isEmpty())
        assertEquals(ListCounts(open = 2, overdue = 0, done = 1), state.counts)
    }

    @Test
    fun `ticking a parent with open sub tasks asks first, then ticks them all`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onToggleDone(vm.uiState.value.open.single())
        assertEquals(TaskId("p"), vm.uiState.value.confirmDone?.task?.id)
        assertTrue(env.tasks.findById(TaskId("p"))!!.isOpen)

        vm.onDismissConfirmDone()
        assertNull(vm.uiState.value.confirmDone)
        assertTrue(env.tasks.findById(TaskId("p"))!!.isOpen)

        vm.onToggleDone(vm.uiState.value.open.single())
        vm.onConfirmDone()
        assertTrue(env.tasks.findById(TaskId("p"))!!.isDone)
        assertTrue(env.tasks.findById(TaskId("c"))!!.isDone)
    }

    @Test
    fun `the uncategorized list doesn't repeat "Uncategorized" on its rows`() = runTest {
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("b").markDone(NOW))
        val vm = viewModel(TaskList.Uncategorized)
        keepCollecting(vm.uiState)

        assertNull(vm.uiState.value.open.single().category)
        assertNull(vm.uiState.value.done.single().category)
    }

    @Test
    fun `a long press starts selecting, taps add and remove, and a deleted task drops out`() = runTest {
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("b"))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onToggleSelection(TaskId("a"))
        vm.onToggleSelection(TaskId("b"))
        assertEquals(setOf(TaskId("a"), TaskId("b")), vm.uiState.value.selected)
        vm.onToggleSelection(TaskId("a"))
        assertEquals(setOf(TaskId("b")), vm.uiState.value.selected)

        env.tasks.delete(TaskId("b"))
        assertFalse(vm.uiState.value.isSelecting)
    }

    @Test
    fun `bulk priority, move and delete apply to the selection, then leave selection mode`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("b"))
        env.tasks.upsert(aTask("c", parentId = TaskId("b")))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onToggleSelection(TaskId("a"))
        vm.onToggleSelection(TaskId("c"))
        vm.onSetSelectedPriority(Priority.URGENT)
        assertFalse(vm.uiState.value.isSelecting)
        assertEquals(Priority.URGENT, env.tasks.findById(TaskId("a"))?.priority)
        assertEquals(Priority.URGENT, env.tasks.findById(TaskId("c"))?.priority)

        vm.onToggleSelection(TaskId("b"))
        vm.onMoveSelected(work.id)
        assertEquals(work.id, env.tasks.findById(TaskId("c"))?.categoryId)

        vm.onToggleSelection(TaskId("b"))
        assertEquals(1, vm.uiState.value.subTasksDeletedWithSelection)
        vm.onDeleteSelected()
        assertEquals(listOf("a"), env.tasks.all.map { it.id.value })
        assertEquals(UserMessage.Deleted(1), vm.uiState.value.message)
    }

    @Test
    fun `only sub tasks selected - nothing to move`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onToggleSelection(TaskId("c"))

        assertFalse(vm.uiState.value.canMoveSelection)
    }

    @Test
    fun `a category list is titled with its name and hides the category on each row`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a", categoryId = work.id))
        env.tasks.upsert(aTask("b"))
        val vm = viewModel(TaskList.InCategory(work.id))
        keepCollecting(vm.uiState)

        val state = vm.uiState.value
        assertEquals(ListTitle.Category("Work"), state.title)
        assertEquals(listOf("a"), state.open.map { it.task.id.value })
        assertNull(state.open.single().category)
    }

    @Test
    fun `a list whose category was deleted reports it as missing`() = runTest {
        val vm = viewModel(TaskList.InCategory(CategoryId("ghost")))
        keepCollecting(vm.uiState)

        assertEquals(ListTitle.Missing, vm.uiState.value.title)
    }

    @Test
    fun `ticking moves a task to the done section and unticking brings it back`() = runTest {
        env.tasks.upsert(aTask("a"))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onToggleDone(vm.uiState.value.open.single())
        assertEquals(listOf("a"), vm.uiState.value.done.map { it.task.id.value })
        assertTrue(vm.uiState.value.open.isEmpty())

        vm.onToggleDone(vm.uiState.value.done.single())
        assertEquals(listOf("a"), vm.uiState.value.open.map { it.task.id.value })
    }

    @Test
    fun `archive done clears the done section and says how many were archived`() = runTest {
        env.tasks.upsert(aTask("a").markDone(NOW))
        env.tasks.upsert(aTask("b").markDone(NOW))
        env.tasks.upsert(aTask("c"))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onArchiveDone()

        assertTrue(vm.uiState.value.done.isEmpty())
        assertEquals(1, vm.uiState.value.open.size)
        assertEquals(UserMessage.Archived(2), vm.uiState.value.message)

        vm.onMessageShown()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `swiping archives a single done task`() = runTest {
        env.tasks.upsert(aTask("a").markDone(NOW))
        env.tasks.upsert(aTask("b").markDone(NOW))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onArchive(TaskId("a"))

        assertEquals(listOf("b"), vm.uiState.value.done.map { it.task.id.value })
        assertIs<TaskStatus.Archived>(env.tasks.findById(TaskId("a"))?.status)
    }

    @Test
    fun `a failed action surfaces the domain error`() = runTest {
        env.tasks.upsert(aTask("a"))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onArchive(TaskId("a")) // still open: can't be archived

        val message = assertIs<UserMessage.Error>(vm.uiState.value.message)
        assertIs<DomainError.TaskNotDone>(message.error)
    }

    @Test
    fun `a task turns overdue when its deadline passes, without any data change`() = runTest {
        env.tasks.upsert(aTask("a", deadline = NOW.plus(Duration.ofMinutes(30))))
        val vm = viewModel()
        keepCollecting(vm.uiState)
        assertFalse(vm.uiState.value.open.single().isOverdue)

        env.clock.advanceBy(Duration.ofHours(1))
        ticker.tick(env.clock.instant())

        assertTrue(vm.uiState.value.open.single().isOverdue)
    }
}
