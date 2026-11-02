package dev.mathieu.youcandoit.domain.usecase

import app.cash.turbine.test
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class QueriesTest {
    private val env = TestEnv()

    @Test
    fun `categories are alphabetical ignoring case`() = runTest {
        env.categories.upsert(aCategory("w", "Work"))
        env.categories.upsert(aCategory("b", "bills"))
        env.categories.upsert(aCategory("h", "Home"))
        env.categories.upsert(aCategory("e", "Épicerie"))

        ObserveCategories(env.categories)().test {
            // An accented capital sorts with its letter, not after "Z".
            assertEquals(listOf("bills", "Épicerie", "Home", "Work"), awaitItem().map { it.name })
        }
    }

    @Test
    fun `get task returns the task or a not-found error`() = runTest {
        val task = aTask("a")
        env.tasks.upsert(task)
        val getTask = GetTask(env.tasks)

        assertEquals(task, getTask(TaskId("a")).getOrThrow())
        assertIs<DomainError.TaskNotFound>(getTask(TaskId("ghost")).exceptionOrNull())
    }
}
