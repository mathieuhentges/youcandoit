package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.IdGenerator
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import java.time.Clock
import javax.inject.Inject

class CreateCategory @Inject constructor(
    private val categories: CategoryRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(name: String): Result<Category> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(DomainError.BlankCategoryName())
        if (categories.findByName(trimmed) != null) {
            return Result.failure(DomainError.DuplicateCategoryName(trimmed))
        }

        val category = Category(CategoryId(ids.newId()), trimmed, clock.instant())
        categories.upsert(category)
        return Result.success(category)
    }
}
