package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
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

class EditSelectionTest {
    private val env = TestEnv()
    private val deleteTasks = DeleteTasks(env.tasks, env.reminders)
    private val setPriority = SetTasksPriority(env.tasks)
    private val moveTasks = MoveTasksToCategory(env.tasks, env.categories)
    private val observeList = ObserveTaskList(env.tasks)

    private val work = aCategory("work")

    private fun ids(vararg id: String) = id.map { TaskId(it) }.toSet()

    @Test
    fun `deletes every selected task, their sub tasks, and all their reminders`() = runTest {
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("b"))
        env.tasks.upsert(aTask("c", parentId = TaskId("b")))
        env.tasks.upsert(aTask("keep"))
        env.scheduler.scheduled[TaskId("c")] = NOW.plus(Duration.ofDays(1))

        assertEquals(2, deleteTasks(ids("a", "b", "gone")))

        assertEquals(listOf("keep"), env.tasks.all.map { it.id.value })
        assertTrue(env.scheduler.cancelled.containsAll(ids("a", "b", "c")))
    }

    @Test
    fun `sets one priority on every selected task, sub tasks included, archived ones untouched`() = runTest {
        env.tasks.upsert(aTask("a", priority = Priority.LOW))
        env.tasks.upsert(aTask("c", parentId = TaskId("a"), priority = Priority.MINIMAL))
        env.tasks.upsert(aTask("old").markDone(NOW).archive(NOW))

        setPriority(ids("a", "c", "old"), Priority.URGENT)

        assertEquals(Priority.URGENT, env.tasks.findById(TaskId("a"))?.priority)
        assertEquals(Priority.URGENT, env.tasks.findById(TaskId("c"))?.priority)
        assertEquals(Priority.MEDIUM, env.tasks.findById(TaskId("old"))?.priority)
    }

    @Test
    fun `moves the selection to the top of a category, in order, with their sub tasks`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("w", categoryId = work.id, position = 0))
        env.tasks.upsert(aTask("a", position = 0))
        env.tasks.upsert(aTask("b", position = 1))
        env.tasks.upsert(aTask("c", parentId = TaskId("b")))

        moveTasks(ids("b", "a"), work.id).getOrThrow()

        assertEquals(listOf("a", "b", "w"), observeList(TaskList.InCategory(work.id)).first().open.map { it.id.value })
        assertEquals(work.id, env.tasks.findById(TaskId("c"))?.categoryId)
    }

    @Test
    fun `a selected sub task stays with its parent`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))

        moveTasks(ids("c"), work.id).getOrThrow()

        assertNull(env.tasks.findById(TaskId("c"))?.categoryId)
    }

    @Test
    fun `moving to a deleted category fails and changes nothing`() = runTest {
        env.tasks.upsert(aTask("a"))

        assertIs<DomainError.CategoryNotFound>(moveTasks(ids("a"), CategoryId("gone")).exceptionOrNull())
        assertNull(env.tasks.findById(TaskId("a"))?.categoryId)
    }
}
