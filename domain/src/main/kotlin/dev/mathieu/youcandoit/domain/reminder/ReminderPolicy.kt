package dev.mathieu.youcandoit.domain.reminder

import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import java.time.Instant

object ReminderPolicy {
    /** When the reminder for [task] should fire, or null if nothing should be scheduled now. */
    fun triggerAt(task: Task, now: Instant): Instant? {
        if (!task.isOpen) return null
        val at = when (val reminder = task.reminder) {
            Reminder.None -> return null
            Reminder.AtDeadline -> task.deadline ?: return null
            is Reminder.BeforeDeadline -> task.deadline?.minus(reminder.offset) ?: return null
            is Reminder.At -> reminder.at
        }
        return at.takeIf { it.isAfter(now) }
    }

    /** The task is set up to be reminded at all: open, with a reminder (and a deadline, if it's relative to one). */
    fun wantsReminder(task: Task): Boolean = task.isOpen && hasReminder(task)

    /** A reminder that can fire: a chosen time, or one relative to a deadline the task has. */
    fun hasReminder(task: Task): Boolean = when (task.reminder) {
        Reminder.None -> false
        is Reminder.At -> true
        else -> task.deadline != null
    }

    /**
     * A "before the deadline" reminder whose time has already passed (e.g. "1 day before" on a task
     * due in 5 hours) falls back to the deadline itself, as long as the deadline is still ahead.
     * Applied when a reminder or deadline is set, never to one that's unchanged (it may have fired).
     */
    fun effectiveReminder(reminder: Reminder, deadline: Instant?, now: Instant): Reminder {
        if (reminder !is Reminder.BeforeDeadline || deadline == null) return reminder
        val at = deadline.minus(reminder.offset)
        return if (!at.isAfter(now) && deadline.isAfter(now)) Reminder.AtDeadline else reminder
    }
}
