package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
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

/**
 * Quick add: one task per title, all sharing the other fields. Blank titles are skipped; the
 * tasks keep the given order at the top of their category. Adds all of them or none.
 */
class AddTasks @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    data class Params(
        val titles: List<String>,
        val priority: Priority = Priority.MEDIUM,
        val categoryId: CategoryId? = null,
        val deadline: Instant? = null,
        val reminder: Reminder = Reminder.None,
        /** Adds them all as sub tasks of this task, in its category. */
        val parentId: TaskId? = null,
    )

    suspend operator fun invoke(params: Params): Result<List<Task>> {
        val titles = params.titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (titles.isEmpty()) return Result.failure(DomainError.BlankTitle())
        val now = clock.instant()
        val parent = tasks.parentFor(params.parentId).getOrElse { return Result.failure(it) }
        val categoryId = if (parent != null) parent.categoryId else params.categoryId
        validateTaskInput(titles.first(), params.deadline, params.reminder, categoryId, now, categories)
            ?.let { return Result.failure(it) }

        val top = tasks.topPositionIn(categoryId, params.parentId)
        val reminder = ReminderPolicy.effectiveReminder(params.reminder, params.deadline, now)
        val added = titles.mapIndexed { index, title ->
            Task(
                id = TaskId(ids.newId()),
                title = title,
                description = "",
                priority = params.priority,
                categoryId = categoryId,
                deadline = params.deadline,
                reminder = reminder,
                createdAt = now,
                status = TaskStatus.Open,
                position = top - (titles.size - 1) + index,
                parentId = params.parentId,
            )
        }
        tasks.upsertAll(added)
        added.forEach { reminders.sync(it) }
        return Result.success(added)
    }
}
