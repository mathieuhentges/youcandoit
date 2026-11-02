package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.IdGenerator
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.reminder.ReminderPolicy
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class AddTask @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    data class Params(
        val title: String,
        val description: String = "",
        val priority: Priority = Priority.MEDIUM,
        val categoryId: CategoryId? = null,
        val deadline: Instant? = null,
        val reminder: Reminder = Reminder.None,
        /** Makes it a sub task; [categoryId] is then ignored: a sub task is in its parent's category. */
        val parentId: TaskId? = null,
    )

    suspend operator fun invoke(params: Params): Result<Task> {
        val now = clock.instant()
        val parent = tasks.parentFor(params.parentId).getOrElse { return Result.failure(it) }
        val categoryId = if (parent != null) parent.categoryId else params.categoryId
        validateTaskInput(params.title, params.deadline, params.reminder, categoryId, now, categories)
            ?.let { return Result.failure(it) }

        val task = Task(
            id = TaskId(ids.newId()),
            title = params.title.trim(),
            description = params.description.trim(),
            priority = params.priority,
            categoryId = categoryId,
            deadline = params.deadline,
            reminder = ReminderPolicy.effectiveReminder(params.reminder, params.deadline, now),
            createdAt = now,
            status = TaskStatus.Open,
            position = tasks.topPositionIn(categoryId, params.parentId),
            parentId = params.parentId,
        )
        tasks.upsert(task)
        reminders.sync(task)
        return Result.success(task)
    }
}
