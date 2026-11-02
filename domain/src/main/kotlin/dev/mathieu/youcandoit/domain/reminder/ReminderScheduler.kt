package dev.mathieu.youcandoit.domain.reminder

import dev.mathieu.youcandoit.domain.model.TaskId
import java.time.Instant

/** Port implemented by the Android layer (AlarmManager + notifications). */
interface ReminderScheduler {
    /** Schedules the reminder for [taskId], replacing any pending one. */
    suspend fun schedule(taskId: TaskId, at: Instant)

    /**
     * The task no longer needs reminding (ticked, deleted, reminder removed): drops the pending
     * reminder AND dismisses its notification if it's showing.
     */
    suspend fun cancel(taskId: TaskId)
}
