package dev.mathieu.youcandoit.data

import android.app.Application
import app.cash.turbine.test
import dev.mathieu.youcandoit.data.local.TaskDao
import dev.mathieu.youcandoit.data.local.TodoDatabase
import dev.mathieu.youcandoit.data.repository.RoomCategoryRepository
import dev.mathieu.youcandoit.data.repository.RoomTaskRepository
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomTaskRepositoryTest {
    private lateinit var db: TodoDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var categories: RoomCategoryRepository
    private val work = aCategory("work", "Work")
    private val home = aCategory("home", "Home")

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tasks = RoomTaskRepository(db.taskDao())
        categories = RoomCategoryRepository(db.categoryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `round-trips a fully populated task`() = runTest {
        categories.upsert(work)
        val task = Task(
            id = TaskId("t1"),
            title = "Quarterly report",
            description = "Numbers for Q4",
            priority = Priority.URGENT,
            categoryId = work.id,
            deadline = NOW.plus(Duration.ofDays(2)),
            reminder = Reminder.BeforeDeadline(Duration.ofHours(3)),
            createdAt = NOW,
            status = TaskStatus.Done(NOW.plus(Duration.ofDays(1))),
        )

        tasks.upsert(task)

        assertEquals(task, tasks.findById(task.id))
    }

    @Test
    fun `round-trips every reminder type and priority`() = runTest {
        val variants = listOf(Reminder.None, Reminder.AtDeadline, Reminder.BeforeDeadline(Duration.ofMinutes(15)))
            .flatMap { reminder -> Priority.entries.map { reminder to it } }
            .mapIndexed { i, (reminder, priority) ->
                aTask("t$i", priority = priority).copy(deadline = NOW.plus(Duration.ofDays(1)), reminder = reminder)
            }

        variants.forEach { tasks.upsert(it) }

        variants.forEach { assertEquals(it, tasks.findById(it.id)) }
    }

    @Test
    fun `round-trips every status`() = runTest {
        val statuses = listOf(
            TaskStatus.Open,
            TaskStatus.Done(NOW),
            TaskStatus.Archived(doneAt = NOW, archivedAt = NOW.plus(Duration.ofDays(1))),
        )
        val variants = statuses.mapIndexed { i, status -> aTask("s$i").copy(status = status) }

        variants.forEach { tasks.upsert(it) }

        variants.forEach { assertEquals(it, tasks.findById(it.id)) }
    }

    @Test
    fun `upsertAll writes every task`() = runTest {
        tasks.upsert(aTask("a"))
        val archived = listOf(
            aTask("a").copy(status = TaskStatus.Archived(NOW, NOW)),
            aTask("b").copy(status = TaskStatus.Archived(NOW, NOW)),
        )

        tasks.upsertAll(archived)

        archived.forEach { assertEquals(it, tasks.findById(it.id)) }
    }

    @Test
    fun `upsert updates an existing task in place`() = runTest {
        tasks.upsert(aTask("a"))

        tasks.upsert(aTask("a").copy(title = "Renamed"))

        assertEquals("Renamed", tasks.findById(TaskId("a"))?.title)
        assertEquals(1, db.taskDao().observeAllOnce().size)
    }

    @Test
    fun `delete removes only that task`() = runTest {
        tasks.upsert(aTask("a"))
        tasks.upsert(aTask("b"))

        tasks.delete(TaskId("a"))

        assertNull(tasks.findById(TaskId("a")))
        assertEquals("Task b", tasks.findById(TaskId("b"))?.title)
    }

    @Test
    fun `unassignCategory only touches tasks in that category`() = runTest {
        categories.upsert(work)
        categories.upsert(home)
        tasks.upsert(aTask("a", categoryId = work.id))
        tasks.upsert(aTask("b", categoryId = home.id))

        tasks.unassignCategory(work.id)

        assertNull(tasks.findById(TaskId("a"))?.categoryId)
        assertEquals(home.id, tasks.findById(TaskId("b"))?.categoryId)
    }

    @Test
    fun `observeAll emits again when data changes`() = runTest {
        tasks.observeAll().test {
            assertEquals(emptyList<Task>(), awaitItem())

            tasks.upsert(aTask("a"))

            assertEquals(listOf(TaskId("a")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun TaskDao.observeAllOnce() = observeAll().first()
