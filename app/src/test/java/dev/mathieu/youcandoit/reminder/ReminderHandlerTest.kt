package dev.mathieu.youcandoit.reminder

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.usecase.GetTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.RescheduleAllReminders
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReminderHandlerTest {
    private val env = TestEnv()
    private val notifications = FakeReminderNotifications()
    private val handler = ReminderHandler(
        getTask = GetTask(env.tasks),
        markTaskDone = MarkTaskDone(env.tasks, env.reminders, env.clock),
        rescheduleAllReminders = RescheduleAllReminders(env.tasks, env.reminders),
        notifications = notifications,
    )
    private val deadline = NOW.plus(Duration.ofDays(1))

    @Test
    fun `a due reminder shows a notification for an open task`() = runTest {
        env.tasks.upsert(aTask("a", deadline = deadline, reminder = Reminder.AtDeadline))

        handler.onReminderDue(TaskId("a"))

        assertEquals(listOf("a"), notifications.shown.map { it.id.value })
    }

    @Test
    fun `nothing is shown for a task ticked or deleted in the meantime`() = runTest {
        env.tasks.upsert(aTask("done", deadline = deadline, reminder = Reminder.AtDeadline).markDone(NOW))

        handler.onReminderDue(TaskId("done"))
        handler.onReminderDue(TaskId("deleted"))

        assertTrue(notifications.shown.isEmpty())
    }

    @Test
    fun `mark done from the notification ticks the task and removes the notification`() = runTest {
        env.tasks.upsert(aTask("a", deadline = deadline, reminder = Reminder.AtDeadline))

        handler.onMarkDone(TaskId("a"))

        assertEquals(TaskStatus.Done(NOW), env.tasks.findById(TaskId("a"))?.status)
        assertEquals(listOf(TaskId("a")), notifications.cancelled)
    }

    @Test
    fun `rescheduling registers every open task's reminder again`() = runTest {
        env.tasks.upsert(aTask("a", deadline = deadline, reminder = Reminder.AtDeadline))
        env.tasks.upsert(aTask("b", deadline = deadline, reminder = Reminder.AtDeadline).markDone(NOW))

        handler.onRescheduleNeeded()

        assertEquals(mapOf(TaskId("a") to deadline), env.scheduler.scheduled)
    }

    @Test
    fun `rescheduling while a reminder is showing does not dismiss it`() = runTest {
        // The alarm just fired (deadline reached) and the app process was started for it.
        val due = aTask("a", deadline = NOW, reminder = Reminder.AtDeadline)
        env.tasks.upsert(due)
        handler.onReminderDue(TaskId("a"))

        handler.onRescheduleNeeded()

        assertEquals(listOf("a"), notifications.shown.map { it.id.value })
        assertTrue(env.scheduler.cancelled.isEmpty())
    }
}
