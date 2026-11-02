package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Ticks a task: it stays in its list, checked, until archived. Idempotent: keeps the original done time.
 * Its open sub tasks are ticked with it (the list asks first; a notification's "Mark done" can't).
 */
class MarkTaskDone @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (!task.isOpen) return Result.success(task)

        val now = clock.instant()
        val done = task.markDone(now)
        val children = tasks.subTasksOf(id).filter { it.isOpen }.map { it.markDone(now) }
        tasks.upsertAll(listOf(done) + children)
        (listOf(done) + children).forEach { reminders.cancel(it.id) }
        return Result.success(done)
    }
}
