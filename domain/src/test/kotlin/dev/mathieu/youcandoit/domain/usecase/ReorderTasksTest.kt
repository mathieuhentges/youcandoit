package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ReorderTasksTest {
    private val env = TestEnv()
    private val reorder = ReorderTasks(env.tasks, env.categories)
    private val observeList = ObserveTaskList(env.tasks, env.categories)
    private val addTask = AddTask(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("new"))
    private val updateTask = UpdateTask(env.tasks, env.categories, env.reminders, env.clock)

    private val work = aCategory("work")

    private suspend fun openIds(list: TaskList = TaskList.All) = observeList(list).first().open.map { it.id.value }

    private fun ids(vararg id: String) = id.map { TaskId(it) }

    @Test
    fun `the list follows the new drag order`() = runTest {
        env.tasks.upsert(aTask("a", position = 0))
        env.tasks.upsert(aTask("b", position = 1))
        env.tasks.upsert(aTask("c", position = 2))

        reorder(null, ids("c", "a", "b")).getOrThrow()

        assertEquals(listOf("c", "a", "b"), openIds())
    }

    @Test
    fun `a task dragged under another header joins that category`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a", position = 0))
        env.tasks.upsert(aTask("w", categoryId = work.id, position = 0))

        reorder(work.id, ids("w", "a")).getOrThrow()

        assertEquals(work.id, env.tasks.findById(TaskId("a"))?.categoryId)
        assertEquals(listOf("w", "a"), openIds(TaskList.InCategory(work.id)))
    }

    @Test
    fun `a task ticked or deleted during the drag is left alone`() = runTest {
        env.tasks.upsert(aTask("a", position = 0))
        env.tasks.upsert(aTask("done", position = 1).markDone(NOW))

        reorder(null, ids("gone", "done", "a")).getOrThrow()

        assertEquals(1, env.tasks.findById(TaskId("done"))?.position)
        assertEquals(0, env.tasks.findById(TaskId("a"))?.position)
    }

    @Test
    fun `dropping into a deleted category fails`() = runTest {
        env.tasks.upsert(aTask("a"))

        val error = reorder(CategoryId("gone"), ids("a")).exceptionOrNull()

        assertIs<DomainError.CategoryNotFound>(error)
        assertEquals(null, env.tasks.findById(TaskId("a"))?.categoryId)
    }

    @Test
    fun `a new task goes to the top of its category`() = runTest {
        env.tasks.upsert(aTask("a", position = 0))
        env.tasks.upsert(aTask("b", position = 1))

        addTask(AddTask.Params(title = "Fresh")).getOrThrow()

        assertEquals(listOf("new-1", "a", "b"), openIds())
    }

    @Test
    fun `moving a task to another category in the editor puts it at the top there`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("w", categoryId = work.id, position = 0))
        val moving = aTask("a", position = 5)
        env.tasks.upsert(moving)

        updateTask(UpdateTask.Params.from(moving).copy(categoryId = work.id)).getOrThrow()

        assertEquals(listOf("a", "w"), openIds(TaskList.InCategory(work.id)))
    }

    @Test
    fun `editing a task without changing its category keeps its place`() = runTest {
        env.tasks.upsert(aTask("a", position = 0))
        val edited = aTask("b", position = 1)
        env.tasks.upsert(edited)

        updateTask(UpdateTask.Params.from(edited).copy(title = "Renamed")).getOrThrow()

        assertEquals(listOf("a", "b"), openIds())
    }
}
