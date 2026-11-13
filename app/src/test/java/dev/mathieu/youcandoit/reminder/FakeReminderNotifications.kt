package dev.mathieu.youcandoit.reminder

import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId

class FakeReminderNotifications : ReminderNotifications {
    val shown = mutableListOf<Task>()
    val cancelled = mutableListOf<TaskId>()

    override suspend fun show(task: Task) {
        shown += task
    }

    override fun cancel(taskId: TaskId) {
        cancelled += taskId
    }
}
