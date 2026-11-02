package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskList
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AddTasksTest {
    private val env = TestEnv()
    private val addTasks = AddTasks(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("t"))
    private val observeList = ObserveTaskList(env.tasks)

    private suspend fun openTitles(list: TaskList = TaskList.All) = observeList(list).first().open.map { it.title }

    @Test
    fun `one task per line, trimmed, blank lines skipped, in the typed order above existing tasks`() = runTest {
        env.tasks.upsert(aTask("old", position = 0).copy(title = "Old"))

        val added = addTasks(AddTasks.Params(titles = listOf(" Milk ", "", "Eggs", "   ", "Bread"))).getOrThrow()

        assertEquals(3, added.size)
        assertEquals(listOf("Milk", "Eggs", "Bread", "Old"), openTitles())
    }

    @Test
    fun `every task gets the shared priority, category, deadline and reminder`() = runTest {
        val work = aCategory("work")
        env.categories.upsert(work)
        val deadline = NOW.plus(Duration.ofDays(1))

        addTasks(AddTasks.Params(listOf("A", "B"), Priority.HIGH, work.id, deadline, Reminder.AtDeadline)).getOrThrow()

        env.tasks.all.forEach {
            assertEquals(Priority.HIGH, it.priority)
            assertEquals(work.id, it.categoryId)
            assertEquals(deadline, it.deadline)
        }
        assertEquals(setOf(deadline), env.scheduler.scheduled.values.toSet())
        assertEquals(2, env.scheduler.scheduled.size)
    }

    @Test
    fun `nothing but blank lines is a blank title`() = runTest {
        assertIs<DomainError.BlankTitle>(addTasks(AddTasks.Params(listOf("", "  "))).exceptionOrNull())
    }

    @Test
    fun `an invalid shared field adds none of them`() = runTest {
        val error = addTasks(AddTasks.Params(listOf("A", "B"), categoryId = CategoryId("gone"))).exceptionOrNull()

        assertIs<DomainError.CategoryNotFound>(error)
        assertTrue(env.tasks.all.isEmpty())
    }
}
