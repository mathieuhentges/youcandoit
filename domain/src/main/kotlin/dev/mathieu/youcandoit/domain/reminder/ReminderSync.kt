package dev.mathieu.youcandoit.domain.reminder

import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import java.time.Clock
import javax.inject.Inject

class ReminderSync @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    /** After the user changed [task]: brings its reminder in line with its new state. */
    suspend fun sync(task: Task) {
        val at = ReminderPolicy.triggerAt(task, clock.instant())
        when {
            at != null -> scheduler.schedule(task.id, at)
            // Still wanted but its time has passed: it already fired and its notification may be
            // showing. Leave it alone, so editing e.g. the title doesn't dismiss it.
            ReminderPolicy.wantsReminder(task) -> Unit
            else -> scheduler.cancel(task.id)
        }
    }

    /**
     * Re-registers a future reminder, never cancels anything. For app start and system events,
     * which can run at the very moment a reminder is being shown.
     */
    suspend fun restore(task: Task) {
        ReminderPolicy.triggerAt(task, clock.instant())?.let { scheduler.schedule(task.id, it) }
    }

    suspend fun cancel(id: TaskId) = scheduler.cancel(id)
}
