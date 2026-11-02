package dev.mathieu.youcandoit.ui

import androidx.lifecycle.SavedStateHandle
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.usecase.ArchiveDoneTasks
import dev.mathieu.youcandoit.domain.usecase.ArchiveTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.MarkTaskNotDone
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveTaskList
import dev.mathieu.youcandoit.domain.usecase.ReorderTasks
import dev.mathieu.youcandoit.ui.common.CategoryLabel
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
    private val ticker = FakeTicker(env.clock)
    private val work = aCategory("work", "Work")

    private fun viewModel(list: TaskList = TaskList.All) = TaskListViewModel(
        savedStateHandle = SavedStateHandle(mapOf(NavArgs.LIST to ListKey.encode(list))),
        observeTaskList = ObserveTaskList(env.tasks),
        observeCategories = ObserveCategories(env.categories),
        ticker = ticker,
        markTaskDone = MarkTaskDone(env.tasks, env.reminders, env.clock),
        markTaskNotDone = MarkTaskNotDone(env.tasks, env.reminders),
        archiveTask = ArchiveTask(env.tasks, env.clock),
        archiveDoneTasks = ArchiveDoneTasks(env.tasks, env.clock),
        reorderTasks = ReorderTasks(env.tasks, env.categories),
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
