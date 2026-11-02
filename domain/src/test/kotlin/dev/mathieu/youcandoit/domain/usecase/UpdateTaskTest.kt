package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
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

class UpdateTaskTest {
    private val env = TestEnv()
    private val updateTask = UpdateTask(env.tasks, env.categories, env.reminders, env.clock)
    private val tomorrow = NOW.plus(Duration.ofDays(1))

    @Test
    fun `updates and persists the editable fields`() = runTest {
        val original = aTask("a")
        env.tasks.upsert(original)
        env.categories.upsert(aCategory("home"))

        val updated = updateTask(
            UpdateTask.Params.from(original).copy(
                title = "  Clean garage ",
                priority = Priority.URGENT,
                categoryId = CategoryId("home"),
                deadline = tomorrow,
            ),
        ).getOrThrow()

        assertEquals("Clean garage", updated.title)
        assertEquals(Priority.URGENT, updated.priority)
        assertEquals(CategoryId("home"), updated.categoryId)
        assertEquals(tomorrow, updated.deadline)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(updated, env.tasks.findById(TaskId("a")))
    }

    @Test
    fun `an overdue task can be edited without changing its deadline`() = runTest {
        val overdue = aTask("a", deadline = NOW.minus(Duration.ofDays(1)))
        env.tasks.upsert(overdue)

        val result = updateTask(UpdateTask.Params.from(overdue).copy(title = "Renamed"))

        assertEquals("Renamed", result.getOrThrow().title)
    }

    @Test
    fun `a new deadline must be in the future`() = runTest {
        val task = aTask("a", deadline = tomorrow)
        env.tasks.upsert(task)

        val result = updateTask(UpdateTask.Params.from(task).copy(deadline = NOW.minusSeconds(1)))

        assertIs<DomainError.DeadlineNotInFuture>(result.exceptionOrNull())
    }

    @Test
    fun `archived tasks cannot be edited`() = runTest {
        val archived = aTask("a").markDone(NOW).archive(NOW)
        env.tasks.upsert(archived)

        val result = updateTask(UpdateTask.Params.from(archived).copy(title = "Nope"))

        assertIs<DomainError.TaskArchived>(result.exceptionOrNull())
        assertEquals(archived, env.tasks.findById(TaskId("a")))
    }

    @Test
    fun `a done task can still be edited and stays done`() = runTest {
        val done = aTask("a").markDone(NOW)
        env.tasks.upsert(done)

        val updated = updateTask(UpdateTask.Params.from(done).copy(title = "Fixed typo")).getOrThrow()

        assertEquals("Fixed typo", updated.title)
        assertEquals(TaskStatus.Done(NOW), updated.status)
    }

    @Test
    fun `fails for an unknown task`() = runTest {
        val result = updateTask(UpdateTask.Params.from(aTask("ghost")))
        assertIs<DomainError.TaskNotFound>(result.exceptionOrNull())
    }

    @Test
    fun `adding a reminder schedules it and removing it cancels it`() = runTest {
        val task = aTask("a", deadline = tomorrow)
        env.tasks.upsert(task)

        updateTask(UpdateTask.Params.from(task).copy(reminder = Reminder.AtDeadline)).getOrThrow()
        assertEquals(tomorrow, env.scheduler.scheduled[TaskId("a")])

        updateTask(UpdateTask.Params.from(task).copy(reminder = Reminder.None)).getOrThrow()
        assertTrue(env.scheduler.scheduled.isEmpty())
    }

    @Test
    fun `an unchanged reminder that already fired is kept and not rescheduled`() = runTest {
        val fired = aTask("a", deadline = NOW.plus(Duration.ofMinutes(30)), reminder = Reminder.BeforeDeadline(Duration.ofHours(1)))
        env.tasks.upsert(fired)

        val updated = updateTask(UpdateTask.Params.from(fired).copy(title = "Renamed")).getOrThrow()

        assertEquals(Reminder.BeforeDeadline(Duration.ofHours(1)), updated.reminder)
        assertTrue(env.scheduler.scheduled.isEmpty())
        assertTrue(env.scheduler.cancelled.isEmpty())
    }

    @Test
    fun `a newly picked reminder that is too late falls back to the deadline`() = runTest {
        val task = aTask("a", deadline = NOW.plus(Duration.ofHours(5)))
        env.tasks.upsert(task)

        val updated = updateTask(UpdateTask.Params.from(task).copy(reminder = Reminder.BeforeDeadline(Duration.ofDays(1)))).getOrThrow()

        assertEquals(Reminder.AtDeadline, updated.reminder)
        assertEquals(task.deadline, env.scheduler.scheduled[TaskId("a")])
    }
}
