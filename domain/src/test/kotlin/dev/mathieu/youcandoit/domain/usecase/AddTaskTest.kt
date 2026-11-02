package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AddTaskTest {
    private val env = TestEnv()
    private val addTask = AddTask(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("task"))

    @Test
    fun `creates an open task with a trimmed title and sensible defaults`() = runTest {
        val task = addTask(AddTask.Params(title = "  Buy milk  ")).getOrThrow()

        assertEquals(TaskId("task-1"), task.id)
        assertEquals("Buy milk", task.title)
        assertEquals(Priority.MEDIUM, task.priority)
        assertEquals(TaskStatus.Open, task.status)
        assertEquals(Reminder.None, task.reminder)
        assertEquals(NOW, task.createdAt)
    }

    @Test
    fun `persists the new task`() = runTest {
        val task = addTask(AddTask.Params(title = "Buy milk")).getOrThrow()
        assertEquals(task, env.tasks.findById(task.id))
    }

    @Test
    fun `rejects a blank title and saves nothing`() = runTest {
        val result = addTask(AddTask.Params(title = "   "))

        assertIs<DomainError.BlankTitle>(result.exceptionOrNull())
        assertTrue(env.tasks.all.isEmpty())
    }

    @Test
    fun `rejects a deadline that is not in the future`() = runTest {
        val result = addTask(AddTask.Params(title = "Late", deadline = NOW))
        assertIs<DomainError.DeadlineNotInFuture>(result.exceptionOrNull())
    }

    @Test
    fun `accepts a future deadline and schedules its reminder`() = runTest {
        val deadline = NOW.plus(Duration.ofDays(2))
        val reminder = Reminder.BeforeDeadline(Duration.ofHours(1))

        val task = addTask(AddTask.Params(title = "Report", deadline = deadline, reminder = reminder)).getOrThrow()

        assertEquals(deadline, task.deadline)
        assertEquals(reminder, task.reminder)
        assertEquals(deadline.minus(Duration.ofHours(1)), env.scheduler.scheduled[task.id])
    }

    @Test
    fun `a reminder at a chosen time needs no deadline, and is scheduled`() = runTest {
        val at = NOW.plus(Duration.ofHours(2))

        val task = addTask(AddTask.Params(title = "Call", reminder = Reminder.At(at))).getOrThrow()

        assertEquals(Reminder.At(at), task.reminder)
        assertEquals(at, env.scheduler.scheduled[task.id])
    }

    @Test
    fun `rejects a reminder time that has passed`() = runTest {
        val result = addTask(AddTask.Params(title = "Call", reminder = Reminder.At(NOW.minusSeconds(60))))

        assertIs<DomainError.ReminderNotInFuture>(result.exceptionOrNull())
        assertTrue(env.tasks.all.isEmpty())
    }

    @Test
    fun `rejects a reminder without a deadline`() = runTest {
        val result = addTask(AddTask.Params(title = "x", reminder = Reminder.AtDeadline))
        assertIs<DomainError.ReminderWithoutDeadline>(result.exceptionOrNull())
    }

    @Test
    fun `rejects an unknown category`() = runTest {
        val result = addTask(AddTask.Params(title = "x", categoryId = CategoryId("nope")))

        val error = assertIs<DomainError.CategoryNotFound>(result.exceptionOrNull())
        assertEquals(CategoryId("nope"), error.id)
    }

    @Test
    fun `assigns an existing category`() = runTest {
        val work = aCategory("work")
        env.categories.upsert(work)

        val task = addTask(AddTask.Params(title = "x", categoryId = work.id)).getOrThrow()

        assertEquals(work.id, task.categoryId)
    }

    @Test
    fun `a before-deadline reminder that is already too late is saved as at-deadline`() = runTest {
        val deadline = NOW.plus(Duration.ofHours(5))

        val task = addTask(
            AddTask.Params(title = "Pay rent", deadline = deadline, reminder = Reminder.BeforeDeadline(Duration.ofDays(1))),
        ).getOrThrow()

        assertEquals(Reminder.AtDeadline, task.reminder)
        assertEquals(deadline, env.scheduler.scheduled[task.id])
    }
}
