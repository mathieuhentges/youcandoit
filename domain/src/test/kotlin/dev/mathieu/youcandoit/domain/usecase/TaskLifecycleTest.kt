package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TaskLifecycleTest {
    private val env = TestEnv()
    private val markDone = MarkTaskDone(env.tasks, env.reminders, env.clock)
    private val markNotDone = MarkTaskNotDone(env.tasks, env.reminders)
    private val archive = ArchiveTask(env.tasks, env.clock)
    private val archiveDone = ArchiveDoneTasks(env.tasks, env.clock)
    private val restore = RestoreTask(env.tasks)
    private val a = TaskId("a")
    private val deadline = NOW.plus(Duration.ofDays(1))

    private suspend fun statusOf(id: String) = env.tasks.findById(TaskId(id))?.status

    @Test
    fun `marking done records the time and keeps the task`() = runTest {
        env.tasks.upsert(aTask("a"))
        env.clock.advanceBy(Duration.ofHours(2))

        val done = markDone(a).getOrThrow()

        assertEquals(TaskStatus.Done(NOW.plus(Duration.ofHours(2))), done.status)
        assertEquals(done, env.tasks.findById(a))
    }

    @Test
    fun `marking done twice keeps the original time`() = runTest {
        env.tasks.upsert(aTask("a"))
        markDone(a).getOrThrow()
        env.clock.advanceBy(Duration.ofDays(1))

        assertEquals(TaskStatus.Done(NOW), markDone(a).getOrThrow().status)
    }

    @Test
    fun `ticking cancels the reminder and unticking restores it`() = runTest {
        env.tasks.upsert(aTask("a", deadline = deadline, reminder = Reminder.AtDeadline))
        env.scheduler.schedule(a, deadline)

        markDone(a).getOrThrow()
        assertTrue(env.scheduler.scheduled.isEmpty())

        val open = markNotDone(a).getOrThrow()
        assertEquals(TaskStatus.Open, open.status)
        assertEquals(deadline, env.scheduler.scheduled[a])
    }

    @Test
    fun `unknown tasks fail everywhere`() = runTest {
        val ghost = TaskId("ghost")
        assertIs<DomainError.TaskNotFound>(markDone(ghost).exceptionOrNull())
        assertIs<DomainError.TaskNotFound>(markNotDone(ghost).exceptionOrNull())
        assertIs<DomainError.TaskNotFound>(archive(ghost).exceptionOrNull())
        assertIs<DomainError.TaskNotFound>(restore(ghost).exceptionOrNull())
    }

    @Test
    fun `archiving a done task keeps its done time`() = runTest {
        env.tasks.upsert(aTask("a").markDone(NOW))
        env.clock.advanceBy(Duration.ofDays(1))

        val archived = archive(a).getOrThrow()

        assertEquals(TaskStatus.Archived(doneAt = NOW, archivedAt = NOW.plus(Duration.ofDays(1))), archived.status)
        assertEquals(archived, env.tasks.findById(a))
    }

    @Test
    fun `an open task must be done before it can be archived`() = runTest {
        env.tasks.upsert(aTask("a"))

        assertIs<DomainError.TaskNotDone>(archive(a).exceptionOrNull())
        assertEquals(TaskStatus.Open, statusOf("a"))
    }

    @Test
    fun `an archived task must be restored before it can be unticked`() = runTest {
        env.tasks.upsert(aTask("a").markDone(NOW).archive(NOW))

        assertIs<DomainError.TaskArchived>(markNotDone(a).exceptionOrNull())

        assertEquals(TaskStatus.Done(NOW), restore(a).getOrThrow().status)
        assertEquals(TaskStatus.Open, markNotDone(a).getOrThrow().status)
    }

    @Test
    fun `archive done only archives done tasks in that list`() = runTest {
        val work = CategoryId("work")
        env.tasks.upsert(aTask("work-done", categoryId = work).markDone(NOW))
        env.tasks.upsert(aTask("work-open", categoryId = work))
        env.tasks.upsert(aTask("home-done", categoryId = CategoryId("home")).markDone(NOW))
        env.clock.advanceBy(Duration.ofHours(1))

        assertEquals(1, archiveDone(TaskList.InCategory(work)))
        assertIs<TaskStatus.Archived>(statusOf("work-done"))
        assertEquals(TaskStatus.Open, statusOf("work-open"))
        assertIs<TaskStatus.Done>(statusOf("home-done"))

        assertEquals(1, archiveDone(TaskList.All))
        assertIs<TaskStatus.Archived>(statusOf("home-done"))
        assertEquals(0, archiveDone(TaskList.All))
    }
}
