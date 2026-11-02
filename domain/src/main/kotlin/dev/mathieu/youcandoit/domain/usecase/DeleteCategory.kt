package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject

/** Deletes a category; its tasks are kept and moved to "Uncategorized". */
class DeleteCategory @Inject constructor(
    private val categories: CategoryRepository,
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(id: CategoryId): Result<Unit> {
        categories.findById(id) ?: return Result.failure(DomainError.CategoryNotFound(id))
        // Unassign first: if the process dies in between, we're left with an empty category, never orphaned tasks.
        tasks.unassignCategory(id)
        categories.delete(id)
        return Result.success(Unit)
    }
}
