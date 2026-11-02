package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.FakeCategoryRepository
import dev.mathieu.youcandoit.domain.fakes.MutableClock
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class CreateCategoryTest {
    private val categories = FakeCategoryRepository()
    private val createCategory = CreateCategory(categories, MutableClock(NOW), SequentialIds("cat"))

    @Test
    fun `creates a category with a trimmed name`() = runTest {
        val category = createCategory("  Groceries ").getOrThrow()

        assertEquals(CategoryId("cat-1"), category.id)
        assertEquals("Groceries", category.name)
        assertEquals(category, categories.findById(category.id))
    }

    @Test
    fun `rejects a blank name`() = runTest {
        assertIs<DomainError.BlankCategoryName>(createCategory(" ").exceptionOrNull())
    }

    @Test
    fun `rejects a duplicate name regardless of case`() = runTest {
        createCategory("Work").getOrThrow()

        val result = createCategory("work")

        assertIs<DomainError.DuplicateCategoryName>(result.exceptionOrNull())
        assertEquals(1, categories.all.size)
    }

    @Test
    fun `duplicates are detected across accented capitals too`() = runTest {
        createCategory("Épicerie").getOrThrow()

        assertIs<DomainError.DuplicateCategoryName>(createCategory("épicerie").exceptionOrNull())
    }
}
