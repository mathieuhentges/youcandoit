package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SubTasksTest {
    private val env = TestEnv()
    private val addTask = AddTask(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("sub"))
    private val addTasks = AddTasks(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("subs"))
    private val updateTask = UpdateTask(env.tasks, env.categories, env.reminders, env.clock)
    private val markDone = MarkTaskDone(env.tasks, env.reminders, env.clock)
    private val markNotDone = MarkTaskNotDone(env.tasks, env.reminders)
    private val archive = ArchiveTask(env.tasks, env.clock)
    private val restore = RestoreTask(env.tasks)
    private val delete = DeleteTask(env.tasks, env.reminders)
    private val reorder = ReorderTasks(env.tasks, env.categories)
    private val reorderSubTasks = ReorderSubTasks(env.tasks)
    private val observeList = ObserveTaskList(env.tasks, env.categories)

    private val work = aCategory("work")
    private val parentId = TaskId("p")

    private suspend fun task(id: String): Task = env.tasks.findById(TaskId(id))!!

    private suspend fun givenParent(categoryId: CategoryId? = work.id) {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("p", categoryId = categoryId))
    }

    private suspend fun givenChildren(vararg ids: String) = ids.forEachIndexed { i, id ->
        env.tasks.upsert(aTask(id, categoryId = task("p").categoryId, parentId = parentId, position = i.toLong()))
    }

    @Test
    fun `a sub task goes in its parent's category, whatever category was asked for`() = runTest {
        givenParent()

        val sub = addTask(AddTask.Params(title = "Step", categoryId = null, parentId = parentId)).getOrThrow()

        assertEquals(parentId, sub.parentId)
        assertEquals(work.id, sub.categoryId)
    }

    @Test
    fun `under an uncategorized parent, a sub task is uncategorized too`() = runTest {
        givenParent(categoryId = null)

        val sub = addTask(AddTask.Params(title = "Step", categoryId = work.id, parentId = parentId)).getOrThrow()

        assertNull(sub.categoryId)
    }

    @Test
    fun `several sub tasks can be added at once`() = runTest {
        givenParent()

        addTasks(AddTasks.Params(titles = listOf("One", "Two"), parentId = parentId)).getOrThrow()

        assertEquals(listOf("One", "Two"), observeList().first().subTasks.getValue(parentId).map { it.title })
    }

    @Test
    fun `only one level - a sub task can't have sub tasks`() = runTest {
        givenParent()
        givenChildren("c")

        val error = addTask(AddTask.Params(title = "Deeper", parentId = TaskId("c"))).exceptionOrNull()

        assertIs<DomainError.SubTaskNotAllowed>(error)
    }

    @Test
    fun `a done parent can't take a new sub task, and a missing one is reported`() = runTest {
        givenParent()
        markDone(parentId)

        assertIs<DomainError.SubTaskNotAllowed>(addTask(AddTask.Params("Late", parentId = parentId)).exceptionOrNull())
        assertIs<DomainError.TaskNotFound>(addTask(AddTask.Params("Lost", parentId = TaskId("gone"))).exceptionOrNull())
    }

    @Test
    fun `each sub task has its own priority, deadline and reminder`() = runTest {
        givenParent()
        val deadline = NOW.plus(Duration.ofDays(1))

        val sub = addTask(AddTask.Params("Step", deadline = deadline, reminder = Reminder.AtDeadline, parentId = parentId)).getOrThrow()

        assertEquals(deadline, env.scheduler.scheduled[sub.id])
    }

    @Test
    fun `the list shows sub tasks under their parent, not as tasks of their own`() = runTest {
        givenParent()
        givenChildren("b", "a")
        markDone(TaskId("b"))

        val view = observeList(TaskList.InCategory(work.id)).first()

        assertEquals(listOf("p"), view.open.map { it.id.value })
        assertTrue(view.done.isEmpty())
        assertEquals(listOf("a", "b"), view.subTasks.getValue(parentId).map { it.id.value }) // open first
    }

    @Test
    fun `ticking a parent ticks its open sub tasks and cancels their reminders`() = runTest {
        givenParent()
        env.tasks.upsert(aTask("c", categoryId = work.id, parentId = parentId, deadline = NOW.plus(Duration.ofDays(1)), reminder = Reminder.AtDeadline))
        env.scheduler.scheduled[TaskId("c")] = NOW.plus(Duration.ofDays(1))

        markDone(parentId).getOrThrow()

        assertTrue(task("c").isDone)
        assertTrue(TaskId("c") !in env.scheduler.scheduled)
    }

    @Test
    fun `ticking the last sub task leaves the parent open`() = runTest {
        givenParent()
        givenChildren("c")

        markDone(TaskId("c"))

        assertTrue(task("p").isOpen)
    }

    @Test
    fun `unticking a sub task of a done parent reopens the parent`() = runTest {
        givenParent()
        givenChildren("c")
        markDone(parentId)

        markNotDone(TaskId("c")).getOrThrow()

        assertTrue(task("c").isOpen)
        assertTrue(task("p").isOpen)
    }

    @Test
    fun `archiving a parent archives its sub tasks, and restoring brings them back`() = runTest {
        givenParent()
        givenChildren("c")
        markDone(parentId)

        archive(parentId).getOrThrow()
        assertTrue(task("c").isArchived)

        restore(parentId).getOrThrow()
        assertTrue(task("p").isDone)
        assertTrue(task("c").isDone)
    }

    @Test
    fun `restoring a sub task restores its archived parent`() = runTest {
        givenParent()
        givenChildren("c")
        markDone(parentId)
        archive(parentId)

        restore(TaskId("c")).getOrThrow()

        assertTrue(task("p").isDone)
    }

    @Test
    fun `deleting a parent deletes its sub tasks and their reminders`() = runTest {
        givenParent()
        givenChildren("c")
        env.scheduler.scheduled[TaskId("c")] = NOW.plus(Duration.ofDays(1))

        delete(parentId).getOrThrow()

        assertTrue(env.tasks.all.isEmpty())
        assertTrue(TaskId("c") in env.scheduler.cancelled)
    }

    @Test
    fun `moving a parent to another category, in the editor or by drag, takes its sub tasks along`() = runTest {
        givenParent(categoryId = null)
        givenChildren("c")

        updateTask(UpdateTask.Params.from(task("p")).copy(categoryId = work.id)).getOrThrow()
        assertEquals(work.id, task("c").categoryId)

        reorder(null, listOf(parentId)).getOrThrow()
        assertNull(task("c").categoryId)
    }

    @Test
    fun `a sub task's category can't be changed on its own`() = runTest {
        givenParent()
        givenChildren("c")

        updateTask(UpdateTask.Params.from(task("c")).copy(categoryId = null, title = "Renamed")).getOrThrow()

        assertEquals(work.id, task("c").categoryId)
        assertEquals("Renamed", task("c").title)
    }

    @Test
    fun `sub tasks are reordered within their parent, and can't be dropped among top-level tasks`() = runTest {
        givenParent()
        givenChildren("a", "b")

        reorderSubTasks(parentId, listOf(TaskId("b"), TaskId("a")))
        assertEquals(listOf("b", "a"), observeList().first().subTasks.getValue(parentId).map { it.id.value })

        reorder(null, listOf(TaskId("a"), parentId)).getOrThrow()
        assertEquals(parentId, task("a").parentId)
    }

    @Test
    fun `a new top-level task's position ignores sub tasks`() = runTest {
        givenParent()
        env.tasks.upsert(aTask("c", categoryId = work.id, parentId = parentId, position = -50))

        val added = addTask(AddTask.Params("Next", categoryId = work.id)).getOrThrow()

        assertEquals(-1, added.position)
    }
}
