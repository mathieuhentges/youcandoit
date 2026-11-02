package dev.mathieu.youcandoit.domain.reminder

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.Reminder
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderPolicyTest {
    private val deadline = NOW.plus(Duration.ofDays(1))

    @Test
    fun `no reminder means nothing to schedule`() {
        assertNull(ReminderPolicy.triggerAt(aTask("a", deadline = deadline), NOW))
    }

    @Test
    fun `at-deadline reminder fires at the deadline`() {
        val task = aTask("a", deadline = deadline, reminder = Reminder.AtDeadline)
        assertEquals(deadline, ReminderPolicy.triggerAt(task, NOW))
    }

    @Test
    fun `before-deadline reminder fires offset before the deadline`() {
        val task = aTask("a", deadline = deadline, reminder = Reminder.BeforeDeadline(Duration.ofHours(2)))
        assertEquals(deadline.minus(Duration.ofHours(2)), ReminderPolicy.triggerAt(task, NOW))
    }

    @Test
    fun `done tasks never get reminders`() {
        val task = aTask("a", deadline = deadline, reminder = Reminder.AtDeadline).markDone(NOW)
        assertNull(ReminderPolicy.triggerAt(task, NOW))
    }

    @Test
    fun `a reminder time already in the past is skipped`() {
        val task = aTask("a", deadline = NOW.plus(Duration.ofMinutes(30)), reminder = Reminder.BeforeDeadline(Duration.ofHours(1)))
        assertNull(ReminderPolicy.triggerAt(task, NOW))
    }

    @Test
    fun `a before-deadline reminder that is already too late falls back to the deadline`() {
        val soon = NOW.plus(Duration.ofHours(5))
        assertEquals(Reminder.AtDeadline, ReminderPolicy.effectiveReminder(Reminder.BeforeDeadline(Duration.ofDays(1)), soon, NOW))
    }

    @Test
    fun `reminders that still fit, or whose deadline has passed, are kept as they are`() {
        val oneHour = Reminder.BeforeDeadline(Duration.ofHours(1))
        assertEquals(oneHour, ReminderPolicy.effectiveReminder(oneHour, NOW.plus(Duration.ofDays(1)), NOW))
        assertEquals(oneHour, ReminderPolicy.effectiveReminder(oneHour, NOW.minus(Duration.ofHours(1)), NOW))
        assertEquals(Reminder.AtDeadline, ReminderPolicy.effectiveReminder(Reminder.AtDeadline, NOW.plusSeconds(60), NOW))
        assertEquals(Reminder.None, ReminderPolicy.effectiveReminder(Reminder.None, null, NOW))
    }
}
