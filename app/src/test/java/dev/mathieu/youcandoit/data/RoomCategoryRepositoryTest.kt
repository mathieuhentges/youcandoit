package dev.mathieu.youcandoit.data

import android.app.Application
import dev.mathieu.youcandoit.data.local.TodoDatabase
import dev.mathieu.youcandoit.data.repository.RoomCategoryRepository
import dev.mathieu.youcandoit.data.repository.RoomTaskRepository
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.usecase.DeleteCategory
import dev.mathieu.youcandoit.domain.usecase.RenameCategory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomCategoryRepositoryTest {
    private lateinit var db: TodoDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var categories: RoomCategoryRepository
    private val work = aCategory("work", "Work")

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tasks = RoomTaskRepository(db.taskDao())
        categories = RoomCategoryRepository(db.categoryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `round-trips a category`() = runTest {
        categories.upsert(work)
        assertEquals(work, categories.findById(work.id))
    }

    @Test
    fun `findByName ignores case`() = runTest {
        categories.upsert(work)

        assertEquals(work, categories.findByName("wOrK"))
        assertNull(categories.findByName("home"))
    }

    @Test
    fun `findByName also ignores the case of accented letters`() = runTest {
        categories.upsert(aCategory("e", "Épicerie"))

        assertEquals("Épicerie", categories.findByName("épicerie")?.name)
        assertEquals("Épicerie", categories.findByName("ÉPICERIE")?.name)
    }

    // The two tests below run the real domain use cases against Room, guarding the
    // database behaviour those use cases rely on.

    @Test
    fun `renaming a category keeps its tasks attached`() = runTest {
        categories.upsert(work)
        tasks.upsert(aTask("a", categoryId = work.id))

        RenameCategory(categories)(work.id, "Job").getOrThrow()

        assertEquals("Job", categories.findById(work.id)?.name)
        assertEquals(work.id, tasks.findById(TaskId("a"))?.categoryId)
    }

    @Test
    fun `deleting a category keeps its tasks as uncategorized`() = runTest {
        categories.upsert(work)
        tasks.upsert(aTask("a", categoryId = work.id))

        DeleteCategory(categories, tasks)(work.id).getOrThrow()

        assertNull(categories.findById(work.id))
        assertNull(tasks.findById(TaskId("a"))?.categoryId)
    }
}
