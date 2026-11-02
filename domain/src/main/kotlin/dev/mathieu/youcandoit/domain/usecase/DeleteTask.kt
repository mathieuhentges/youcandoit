package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject

/** Permanently removes a task, and its sub tasks. Closing is the normal way to finish one. */
class DeleteTask @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(id: TaskId): Result<Unit> {
        tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        val children = tasks.subTasksOf(id)
        // One delete: the repository removes the sub tasks with their parent, all or nothing.
        tasks.delete(id)
        (children.map { it.id } + id).forEach { reminders.cancel(it) }
        return Result.success(Unit)
    }
}
