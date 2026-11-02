package dev.mathieu.youcandoit.domain.usecase

import app.cash.turbine.test
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.Priority.HIGH
import dev.mathieu.youcandoit.domain.model.Priority.LOW
import dev.mathieu.youcandoit.domain.model.Priority.URGENT
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskOrder
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ObserveTasksTest {
    private val env = TestEnv()
    private val observeList = ObserveTaskList(env.tasks)
    private val observeArchived = ObserveArchivedTasks(env.tasks, env.categories)
    private val observeOverview = ObserveListsOverview(env.tasks, env.categories, env.clock)

    private fun List<Task>.ids() = map { it.id.value }

    @Test
    fun `open tasks are in drag order, whatever their priority or deadline`() = runTest {
        listOf(
            aTask("c", priority = URGENT, position = 2),
            aTask("a", priority = LOW, position = 0),
            aTask("b", priority = HIGH, deadline = NOW.plus(Duration.ofDays(1)), position = 1),
        ).forEach { env.tasks.upsert(it) }

        observeList().test {
            assertEquals(listOf("a", "b", "c"), awaitItem().open.ids())
        }
    }

    private suspend fun seedMixed() = listOf(
        aTask("f", priority = LOW, position = 0),
        aTask("a", priority = HIGH, position = 1),
        aTask("e", priority = LOW, createdAt = NOW.minus(Duration.ofHours(1)), position = 2),
        aTask("b", priority = HIGH, deadline = NOW.plus(Duration.ofDays(3)), position = 3),
        aTask("d", priority = URGENT, position = 4),
        aTask("c", priority = HIGH, deadline = NOW.plus(Duration.ofDays(1)), position = 5),
        aTask("g", priority = LOW, deadline = NOW.plus(Duration.ofDays(1)), position = 6),
    ).forEach { env.tasks.upsert(it) }

    @Test
    fun `by priority - most important first, then nearest deadline, then oldest`() = runTest {
        seedMixed()
        observeList(order = TaskOrder.PRIORITY).test {
            assertEquals(listOf("d", "c", "b", "a", "g", "e", "f"), awaitItem().open.ids())
        }
    }

    @Test
    fun `by deadline - nearest first, no deadline last, ties by priority then age`() = runTest {
        seedMixed()
        observeList(order = TaskOrder.DEADLINE).test {
            assertEquals(listOf("c", "g", "b", "d", "a", "e", "f"), awaitItem().open.ids())
        }
    }

    @Test
    fun `by date added - newest first, ties by priority`() = runTest {
        listOf(
            aTask("old", createdAt = NOW.minus(Duration.ofDays(2)), position = 0),
            aTask("new-low", priority = LOW, createdAt = NOW, position = 1),
            aTask("new-high", priority = HIGH, createdAt = NOW, position = 2),
            aTask("mid", createdAt = NOW.minus(Duration.ofDays(1)), position = 3),
        ).forEach { env.tasks.upsert(it) }

        observeList(order = TaskOrder.DATE_ADDED).test {
            assertEquals(listOf("new-high", "new-low", "mid", "old"), awaitItem().open.ids())
        }
    }

    @Test
    fun `done tasks get their own section, most recently done first, archived ones hidden`() = runTest {
        env.tasks.upsert(aTask("open"))
        env.tasks.upsert(aTask("early").markDone(NOW))
        env.tasks.upsert(aTask("late").markDone(NOW.plusSeconds(60)))
        env.tasks.upsert(aTask("gone").markDone(NOW).archive(NOW))

        observeList().test {
            val view = awaitItem()
            assertEquals(listOf("open"), view.open.ids())
            assertEquals(listOf("late", "early"), view.done.ids())
        }
    }

    @Test
    fun `a category list and the uncategorized list only show their own tasks`() = runTest {
        val work = CategoryId("work")
        env.tasks.upsert(aTask("a", categoryId = work))
        env.tasks.upsert(aTask("b"))

        observeList(TaskList.InCategory(work)).test {
            assertEquals(listOf("a"), awaitItem().open.ids())
        }
        observeList(TaskList.Uncategorized).test {
            assertEquals(listOf("b"), awaitItem().open.ids())
        }
    }

    @Test
    fun `ticking a task moves it to the done section`() = runTest {
        env.tasks.upsert(aTask("a"))
        env.tasks.upsert(aTask("b"))

        observeList().test {
            assertEquals(2, awaitItem().open.size)

            env.tasks.upsert(aTask("a").markDone(NOW))

            val view = awaitItem()
            assertEquals(listOf("b"), view.open.ids())
            assertEquals(listOf("a"), view.done.ids())
        }
    }

    @Test
    fun `archived tasks are listed most recently archived first`() = runTest {
        env.tasks.upsert(aTask("old").markDone(NOW).archive(NOW))
        env.tasks.upsert(aTask("new").markDone(NOW).archive(NOW.plus(Duration.ofDays(1))))
        env.tasks.upsert(aTask("done").markDone(NOW))

        observeArchived().test {
            assertEquals(listOf("new", "old"), awaitItem().ids())
        }
    }

    @Test
    fun `archived tasks can be ordered by done date, priority or category`() = runTest {
        val work = aCategory("work", "Work")
        val bills = aCategory("bills", "bills")
        env.categories.upsert(work)
        env.categories.upsert(bills)
        env.tasks.upsert(aTask("a", categoryId = work.id, priority = LOW).markDone(NOW).archive(NOW.plus(Duration.ofDays(3))))
        env.tasks.upsert(aTask("b", priority = URGENT).markDone(NOW.plus(Duration.ofDays(2))).archive(NOW.plus(Duration.ofDays(2))))
        env.tasks.upsert(aTask("c", categoryId = bills.id, priority = LOW).markDone(NOW.plus(Duration.ofDays(1))).archive(NOW.plus(Duration.ofDays(1))))

        observeArchived(HistoryOrder.RECENTLY_DONE).test { assertEquals(listOf("b", "c", "a"), awaitItem().ids()) }
        observeArchived(HistoryOrder.PRIORITY).test { assertEquals(listOf("b", "a", "c"), awaitItem().ids()) }
        observeArchived(HistoryOrder.CATEGORY).test { assertEquals(listOf("c", "a", "b"), awaitItem().ids()) }
    }

    @Test
    fun `lists overview counts open, overdue and done per category`() = runTest {
        val work = aCategory("work", "Work")
        val bills = aCategory("bills", "bills")
        env.categories.upsert(work)
        env.categories.upsert(bills)
        env.tasks.upsert(aTask("a", categoryId = work.id, deadline = NOW.minus(Duration.ofHours(1))))
        env.tasks.upsert(aTask("b", categoryId = work.id).markDone(NOW))
        env.tasks.upsert(aTask("c"))
        env.tasks.upsert(aTask("d").markDone(NOW).archive(NOW))

        observeOverview().test {
            val overview = awaitItem()
            assertEquals(ListCounts(open = 2, overdue = 1, done = 1), overview.all)
            assertEquals(listOf("bills", "Work"), overview.categories.map { it.category.name })
            assertEquals(ListCounts.EMPTY, overview.categories[0].counts)
            assertEquals(ListCounts(open = 1, overdue = 1, done = 1), overview.categories[1].counts)
            assertEquals(ListCounts(open = 1, overdue = 0, done = 0), overview.uncategorized)
        }
    }
}
