package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class CategoryManagementTest {
    private val env = TestEnv()
    private val rename = RenameCategory(env.categories)
    private val delete = DeleteCategory(env.categories, env.tasks)
    private val work = aCategory("work", "Work")

    @Test
    fun `renames a category with a trimmed name`() = runTest {
        env.categories.upsert(work)

        val renamed = rename(work.id, "  Job ").getOrThrow()

        assertEquals("Job", renamed.name)
        assertEquals(renamed, env.categories.findById(work.id))
    }

    @Test
    fun `a category can change the case of its own name`() = runTest {
        env.categories.upsert(work)
        assertEquals("WORK", rename(work.id, "WORK").getOrThrow().name)
    }

    @Test
    fun `rename rejects a name used by another category`() = runTest {
        env.categories.upsert(work)
        env.categories.upsert(aCategory("home", "Home"))

        assertIs<DomainError.DuplicateCategoryName>(rename(work.id, "home").exceptionOrNull())
    }

    @Test
    fun `rename rejects a blank name and unknown categories`() = runTest {
        env.categories.upsert(work)

        assertIs<DomainError.BlankCategoryName>(rename(work.id, " ").exceptionOrNull())
        assertIs<DomainError.CategoryNotFound>(rename(CategoryId("ghost"), "x").exceptionOrNull())
    }

    @Test
    fun `deleting a category keeps its tasks as uncategorized`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a", categoryId = work.id))
        env.tasks.upsert(aTask("b", categoryId = CategoryId("other")))

        delete(work.id).getOrThrow()

        assertNull(env.categories.findById(work.id))
        assertNull(env.tasks.findById(TaskId("a"))?.categoryId)
        assertEquals(CategoryId("other"), env.tasks.findById(TaskId("b"))?.categoryId)
    }

    @Test
    fun `deleting an unknown category fails`() = runTest {
        assertIs<DomainError.CategoryNotFound>(delete(CategoryId("ghost")).exceptionOrNull())
    }
}
