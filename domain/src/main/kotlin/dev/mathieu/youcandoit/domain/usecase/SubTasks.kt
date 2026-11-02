package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first

/**
 * The parent a new sub task would go under, checked: it must exist, be open, and not be a sub task
 * itself. A done parent never has open sub tasks, so they always have a visible place in the list.
 */
internal suspend fun TaskRepository.parentFor(parentId: TaskId?): Result<Task?> {
    if (parentId == null) return Result.success(null)
    val parent = findById(parentId) ?: return Result.failure(DomainError.TaskNotFound(parentId))
    if (!parent.isOpen || parent.isSubTask) return Result.failure(DomainError.SubTaskNotAllowed(parentId))
    return Result.success(parent)
}

internal suspend fun TaskRepository.subTasksOf(parentId: TaskId): List<Task> =
    observeAll().first().filter { it.parentId == parentId }

/**
 * A position above every sibling of a newcomer, so it shows first: the sub tasks of [parentId],
 * or the top-level tasks of [categoryId].
 */
internal suspend fun TaskRepository.topPositionIn(categoryId: CategoryId?, parentId: TaskId? = null): Long =
    observeAll().first()
        .filter { it.parentId == parentId && (parentId != null || it.categoryId == categoryId) }
        .minOfOrNull { it.position }
        ?.minus(1) ?: 0
