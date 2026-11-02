package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

// Selection mode: one change applied to several tasks at once, each in a single write.

/** Deletes the selected tasks and their sub tasks. Returns how many of the selected were deleted. */
class DeleteTasks @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(ids: Set<TaskId>): Int {
        val all = tasks.observeAll().first()
        val found = all.filter { it.id in ids }.map { it.id }.toSet()
        if (found.isEmpty()) return 0
        val children = all.filter { it.parentId in found }.map { it.id }
        tasks.deleteAll(found)
        (found + children).forEach { reminders.cancel(it) }
        return found.size
    }
}

/** Gives the selected tasks one priority. Archived tasks are read-only and left as they are. */
class SetTasksPriority @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(ids: Set<TaskId>, priority: Priority) {
        val changed = tasks.observeAll().first()
            .filter { it.id in ids && !it.isArchived && it.priority != priority }
            .map { it.copy(priority = priority) }
        if (changed.isNotEmpty()) tasks.upsertAll(changed)
    }
}

/**
 * Moves the selected tasks to [categoryId] (null: Uncategorized), at the top there, keeping their
 * order. Sub tasks always follow their parent: selected ones are skipped, a moved parent takes its own.
 */
class MoveTasksToCategory @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(ids: Set<TaskId>, categoryId: CategoryId?): Result<Unit> {
        if (categoryId != null && categories.findById(categoryId) == null) {
            return Result.failure(DomainError.CategoryNotFound(categoryId))
        }
        val all = tasks.observeAll().first()
        val moving = all
            .filter { it.id in ids && !it.isSubTask && !it.isArchived && it.categoryId != categoryId }
            .sortedWith(ObserveTaskList.comparatorFor(TaskOrder.MY_ORDER))
        if (moving.isEmpty()) return Result.success(Unit)

        val top = tasks.topPositionIn(categoryId)
        val moved = moving.mapIndexed { index, task -> task.copy(categoryId = categoryId, position = top - (moving.size - 1) + index) }
        val movingIds = moving.map { it.id }.toSet()
        val children = all.filter { it.parentId in movingIds }.map { it.copy(categoryId = categoryId) }
        tasks.upsertAll(moved + children)
        return Result.success(Unit)
    }
}
