package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject

/** Unticks a done task. Archived tasks must be restored first. A sub task's done parent is unticked too. */
class MarkTaskNotDone @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (task.isArchived) return Result.failure(DomainError.TaskArchived(id))
        if (task.isOpen) return Result.success(task)

        val open = task.markNotDone()
        val parent = task.parentId?.let { tasks.findById(it) }?.takeIf { it.isDone }?.markNotDone()
        val changed = listOfNotNull(open, parent)
        tasks.upsertAll(changed)
        changed.forEach { reminders.sync(it) }
        return Result.success(open)
    }
}
