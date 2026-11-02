package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DeleteTaskTest {
    private val env = TestEnv()
    private val deleteTask = DeleteTask(env.tasks, env.reminders)

    @Test
    fun `deletes the task and cancels its reminder`() = runTest {
        val deadline = NOW.plus(Duration.ofDays(1))
        env.tasks.upsert(aTask("a", deadline = deadline, reminder = Reminder.AtDeadline))
        env.scheduler.schedule(TaskId("a"), deadline)

        deleteTask(TaskId("a")).getOrThrow()

        assertNull(env.tasks.findById(TaskId("a")))
        assertTrue(env.scheduler.scheduled.isEmpty())
    }

    @Test
    fun `fails for an unknown task`() = runTest {
        assertIs<DomainError.TaskNotFound>(deleteTask(TaskId("ghost")).exceptionOrNull())
    }
}
