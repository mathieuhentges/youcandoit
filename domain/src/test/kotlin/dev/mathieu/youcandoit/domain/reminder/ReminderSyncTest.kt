package dev.mathieu.youcandoit.domain.reminder

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.usecase.RescheduleAllReminders
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ReminderSyncTest {
    private val env = TestEnv()

    // A reminder that has already fired: "1 hour before" a deadline 30 minutes away.
    private val fired = aTask("fired", deadline = NOW.plus(Duration.ofMinutes(30)), reminder = Reminder.BeforeDeadline(Duration.ofHours(1)))

    @Test
    fun `rescheduling everything never cancels, so a reminder being shown stays shown`() = runTest {
        env.tasks.upsert(fired)
        env.tasks.upsert(aTask("overdue", deadline = NOW.minus(Duration.ofHours(1)), reminder = Reminder.AtDeadline))
        env.tasks.upsert(aTask("plain"))

        RescheduleAllReminders(env.tasks, env.reminders)()

        assertTrue(env.scheduler.cancelled.isEmpty())
        assertTrue(env.scheduler.scheduled.isEmpty())
    }

    @Test
    fun `rescheduling registers future reminders`() = runTest {
        val deadline = NOW.plus(Duration.ofDays(1))
        env.tasks.upsert(aTask("a", deadline = deadline, reminder = Reminder.AtDeadline))

        RescheduleAllReminders(env.tasks, env.reminders)()

        assertEquals(mapOf(TaskId("a") to deadline), env.scheduler.scheduled)
    }

    @Test
    fun `editing a task whose reminder already fired leaves it alone`() = runTest {
        env.reminders.sync(fired.copy(title = "Renamed"))

        assertTrue(env.scheduler.cancelled.isEmpty())
        assertTrue(env.scheduler.scheduled.isEmpty())
    }

    @Test
    fun `a task that no longer wants a reminder gets it cancelled`() = runTest {
        env.reminders.sync(fired.copy(reminder = Reminder.None))
        env.reminders.sync(aTask("done", deadline = NOW.plus(Duration.ofDays(1)), reminder = Reminder.AtDeadline).markDone(NOW))

        assertEquals(listOf(TaskId("fired"), TaskId("done")), env.scheduler.cancelled)
    }
}
