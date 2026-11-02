package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import javax.inject.Inject

class RenameCategory @Inject constructor(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(id: CategoryId, newName: String): Result<Category> {
        val category = categories.findById(id) ?: return Result.failure(DomainError.CategoryNotFound(id))
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(DomainError.BlankCategoryName())

        val clash = categories.findByName(trimmed)
        if (clash != null && clash.id != id) return Result.failure(DomainError.DuplicateCategoryName(trimmed))

        val renamed = category.copy(name = trimmed)
        categories.upsert(renamed)
        return Result.success(renamed)
    }
}
