package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BackupTest {
    private val from = TestEnv()
    private val to = TestEnv()

    @Test
    fun `everything exported from one phone comes back the same on another, with its reminders`() = runTest {
        val work = aCategory("work", "Work")
        from.categories.upsert(work)
        val soon = NOW.plus(Duration.ofDays(1))
        from.tasks.upsert(aTask("p", categoryId = work.id, deadline = soon, reminder = Reminder.AtDeadline))
        from.tasks.upsert(aTask("c", categoryId = work.id, parentId = TaskId("p"), position = 3))
        from.tasks.upsert(aTask("old").markDone(NOW).archive(NOW))

        val backup = ExportBackup(from.tasks, from.categories)()
        val result = ImportBackup(to.tasks, to.categories, to.reminders)(backup)

        assertEquals(ImportResult(tasks = 3, newCategories = 1), result)
        assertEquals(from.tasks.all.toSet(), to.tasks.all.toSet())
        assertEquals(listOf(work), to.categories.all)
        assertEquals(soon, to.scheduler.scheduled[TaskId("p")])
    }

    @Test
    fun `importing adds to what's here, reusing a category of the same name`() = runTest {
        to.categories.upsert(aCategory("mine", "Work"))
        to.tasks.upsert(aTask("here"))
        val backup = Backup(
            categories = listOf(aCategory("theirs", "work")),
            tasks = listOf(aTask("t", categoryId = CategoryId("theirs"))),
        )

        val result = ImportBackup(to.tasks, to.categories, to.reminders)(backup)

        assertEquals(ImportResult(tasks = 1, newCategories = 0), result)
        assertEquals(setOf("here", "t"), to.tasks.all.map { it.id.value }.toSet())
        assertEquals(CategoryId("mine"), to.tasks.findById(TaskId("t"))?.categoryId)
    }

    @Test
    fun `a sub task without its parent becomes a main task, and a missing category means Uncategorized`() = runTest {
        val backup = Backup(
            categories = emptyList(),
            tasks = listOf(aTask("c", parentId = TaskId("gone"), categoryId = CategoryId("gone"))),
        )

        ImportBackup(to.tasks, to.categories, to.reminders)(backup)

        val task = to.tasks.findById(TaskId("c"))!!
        assertNull(task.parentId)
        assertNull(task.categoryId)
        assertTrue(to.scheduler.scheduled.isEmpty())
    }
}
