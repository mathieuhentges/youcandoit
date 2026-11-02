package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderPolicy
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Replaces the editable fields of an open or done task. Archived tasks must be restored first. */
class UpdateTask @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    data class Params(
        val id: TaskId,
        val title: String,
        val description: String,
        val priority: Priority,
        val categoryId: CategoryId?,
        val deadline: Instant?,
        val reminder: Reminder,
    ) {
        companion object {
            /** Starting point for an edit form: the task's current values. */
            fun from(task: Task) = Params(
                id = task.id,
                title = task.title,
                description = task.description,
                priority = task.priority,
                categoryId = task.categoryId,
                deadline = task.deadline,
                reminder = task.reminder,
            )
        }
    }

    suspend operator fun invoke(params: Params): Result<Task> {
        val existing = tasks.findById(params.id) ?: return Result.failure(DomainError.TaskNotFound(params.id))
        if (existing.isArchived) return Result.failure(DomainError.TaskArchived(params.id))

        val now = clock.instant()
        validateTaskInput(
            title = params.title,
            deadline = params.deadline,
            reminder = params.reminder,
            categoryId = params.categoryId,
            now = now,
            categories = categories,
            previousDeadline = existing.deadline,
        )?.let { return Result.failure(it) }

        // Only a reminder or deadline the user just set can fall back; an unchanged one may have fired already.
        val reminderChanged = params.reminder != existing.reminder || params.deadline != existing.deadline
        val reminder = if (reminderChanged) ReminderPolicy.effectiveReminder(params.reminder, params.deadline, now) else params.reminder

        val updated = existing.copy(
            title = params.title.trim(),
            description = params.description.trim(),
            priority = params.priority,
            categoryId = params.categoryId,
            deadline = params.deadline,
            reminder = reminder,
        )
        tasks.upsert(updated)
        reminders.sync(updated)
        return Result.success(updated)
    }
}
