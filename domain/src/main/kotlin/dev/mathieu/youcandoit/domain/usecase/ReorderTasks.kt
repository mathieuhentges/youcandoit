package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * After a drag: [orderedIds] is the new top-to-bottom order of the open tasks under one category's
 * header ([categoryId], null for "Uncategorized"). A task dragged in from another header joins it.
 */
class ReorderTasks @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(categoryId: CategoryId?, orderedIds: List<TaskId>): Result<Unit> {
        if (categoryId != null && categories.findById(categoryId) == null) {
            return Result.failure(DomainError.CategoryNotFound(categoryId))
        }
        val byId = tasks.observeAll().first().associateBy { it.id }
        // A task ticked or deleted while it was being dragged keeps its new state.
        val reordered = orderedIds
            .mapNotNull { byId[it]?.takeIf { task -> task.isOpen } }
            .mapIndexed { index, task -> task.copy(categoryId = categoryId, position = index.toLong()) }
        if (reordered.isNotEmpty()) tasks.upsertAll(reordered)
        return Result.success(Unit)
    }
}

/** A position above every task already in [categoryId], so a new arrival shows first. */
internal suspend fun TaskRepository.topPositionIn(categoryId: CategoryId?): Long =
    observeAll().first()
        .filter { it.categoryId == categoryId }
        .minOfOrNull { it.position }
        ?.minus(1) ?: 0
