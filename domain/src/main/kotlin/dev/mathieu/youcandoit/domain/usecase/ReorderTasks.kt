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
        val all = tasks.observeAll().first()
        val byId = all.associateBy { it.id }
        // A task ticked or deleted while it was being dragged keeps its new state.
        val reordered = orderedIds
            .mapNotNull { byId[it]?.takeIf { task -> task.isOpen && !task.isSubTask } }
            .mapIndexed { index, task -> task.copy(categoryId = categoryId, position = index.toLong()) }
        // Sub tasks always follow their parent's category.
        val moved = reordered.filter { it.categoryId != byId.getValue(it.id).categoryId }.map { it.id }.toSet()
        val children = all.filter { it.parentId in moved }.map { it.copy(categoryId = categoryId) }
        if (reordered.isNotEmpty()) tasks.upsertAll(reordered + children)
        return Result.success(Unit)
    }
}

/** After a drag inside a parent: [orderedIds] is the new order of its open sub tasks. */
class ReorderSubTasks @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(parentId: TaskId, orderedIds: List<TaskId>) {
        val byId = tasks.subTasksOf(parentId).associateBy { it.id }
        val reordered = orderedIds
            .mapNotNull { byId[it]?.takeIf { task -> task.isOpen } }
            .mapIndexed { index, task -> task.copy(position = index.toLong()) }
        if (reordered.isNotEmpty()) tasks.upsertAll(reordered)
    }
}
