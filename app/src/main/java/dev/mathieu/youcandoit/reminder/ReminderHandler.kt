package dev.mathieu.youcandoit.reminder

import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.usecase.GetTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.RescheduleAllReminders
import javax.inject.Inject

/** What the broadcast receivers do, kept out of them so it can be unit-tested. */
class ReminderHandler @Inject constructor(
    private val getTask: GetTask,
    private val markTaskDone: MarkTaskDone,
    private val rescheduleAllReminders: RescheduleAllReminders,
    private val notifications: ReminderNotifications,
) {
    /** The alarm went off. The task may have been ticked or deleted since: then stay quiet. */
    suspend fun onReminderDue(id: TaskId) {
        val task = getTask(id).getOrNull() ?: return
        if (task.isOpen) notifications.show(task)
    }

    /** "Mark done" on the notification. */
    suspend fun onMarkDone(id: TaskId) {
        markTaskDone(id)
        notifications.cancel(id)
    }

    /** After a reboot, an app update, a clock change or a new exact-alarm permission. */
    suspend fun onRescheduleNeeded() {
        rescheduleAllReminders()
    }
}
