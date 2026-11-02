package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

/** Ticks a task: it stays in its list, checked, until archived. Idempotent: keeps the original done time. */
class MarkTaskDone @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (!task.isOpen) return Result.success(task)

        val done = task.markDone(clock.instant())
        tasks.upsert(done)
        reminders.cancel(id)
        return Result.success(done)
    }
}
