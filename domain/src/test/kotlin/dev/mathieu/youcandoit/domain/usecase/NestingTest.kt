package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class NestingTest {
    private val env = TestEnv()
    private val makeSubTask = MakeSubTask(env.tasks)
    private val moveOut = MoveOutOfParent(env.tasks)
    private val parentChoices = ParentChoices(env.tasks, env.categories)
    private val observeList = ObserveTaskList(env.tasks, env.categories)

    private val work = aCategory("work")

    private fun ids(vararg id: String) = id.toList()

    @Test
    fun `a task goes under another as its last open sub task, in the parent's category`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("p", categoryId = work.id))
        env.tasks.upsert(aTask("c1", categoryId = work.id, parentId = TaskId("p"), position = 0))
        env.tasks.upsert(aTask("t", position = 3))

        makeSubTask(TaskId("t"), TaskId("p")).getOrThrow()

        val view = observeList().first()
        assertEquals(ids("p"), view.open.map { it.id.value })
        assertEquals(ids("c1", "t"), view.subTasks.getValue(TaskId("p")).map { it.id.value })
        assertEquals(work.id, env.tasks.findById(TaskId("t"))?.categoryId)
    }

    @Test
    fun `one level only - a sub task can't take one, nor can a task with its own become one`() = runTest {
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))
        env.tasks.upsert(aTask("q"))

        assertIs<DomainError.SubTaskNotAllowed>(makeSubTask(TaskId("q"), TaskId("c")).exceptionOrNull())
        assertIs<DomainError.HasSubTasks>(makeSubTask(TaskId("p"), TaskId("q")).exceptionOrNull())
        assertIs<DomainError.SubTaskNotAllowed>(makeSubTask(TaskId("q"), TaskId("q")).exceptionOrNull())
        assertNull(env.tasks.findById(TaskId("q"))?.parentId)
    }

    @Test
    fun `a done task can't take a sub task`() = runTest {
        env.tasks.upsert(aTask("done").markDone(NOW))
        env.tasks.upsert(aTask("t"))

        assertIs<DomainError.SubTaskNotAllowed>(makeSubTask(TaskId("t"), TaskId("done")).exceptionOrNull())
    }

    @Test
    fun `moved out, a sub task lands just below its parent, in the parent's category`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a", categoryId = work.id, position = 0))
        env.tasks.upsert(aTask("p", categoryId = work.id, position = 1))
        env.tasks.upsert(aTask("b", categoryId = work.id, position = 2))
        env.tasks.upsert(aTask("c", categoryId = work.id, parentId = TaskId("p")))

        moveOut(TaskId("c")).getOrThrow()

        val view = observeList(TaskList.InCategory(work.id)).first()
        assertEquals(ids("a", "p", "c", "b"), view.open.map { it.id.value })
        assertEquals(emptyMap(), view.subTasks)
    }

    @Test
    fun `a sub task can move to another parent, taking its category`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("p"))
        env.tasks.upsert(aTask("q", categoryId = work.id))
        env.tasks.upsert(aTask("c", parentId = TaskId("p")))

        makeSubTask(TaskId("c"), TaskId("q")).getOrThrow()

        val moved = env.tasks.findById(TaskId("c"))
        assertEquals(TaskId("q"), moved?.parentId)
        assertEquals(work.id, moved?.categoryId)
    }

    @Test
    fun `the parent choices are the other open top-level tasks, by category then my order`() = runTest {
        env.categories.upsert(aCategory("z", "Zoo"))
        env.categories.upsert(aCategory("a", "Admin"))
        env.tasks.upsert(aTask("loose"))
        env.tasks.upsert(aTask("zoo", categoryId = aCategory("z", "Zoo").id))
        env.tasks.upsert(aTask("admin2", categoryId = aCategory("a", "Admin").id, position = 2))
        env.tasks.upsert(aTask("admin1", categoryId = aCategory("a", "Admin").id, position = 1))
        env.tasks.upsert(aTask("self"))
        env.tasks.upsert(aTask("child", parentId = TaskId("loose")))
        env.tasks.upsert(aTask("done").markDone(NOW))

        assertEquals(ids("admin1", "admin2", "zoo", "loose"), parentChoices(TaskId("self")).map { it.id.value })
    }
}
