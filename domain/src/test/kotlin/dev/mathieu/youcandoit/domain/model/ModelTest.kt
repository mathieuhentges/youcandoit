package dev.mathieu.youcandoit.domain.model

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.aTask
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun `there are exactly five priority levels from 1 to 5`() {
        assertEquals(listOf(1, 2, 3, 4, 5), Priority.entries.map { it.level })
    }

    @Test
    fun `priority can be resolved from its level`() {
        assertEquals(Priority.URGENT, Priority.fromLevel(5))
        assertEquals(Priority.MINIMAL, Priority.fromLevel(1))
    }

    @Test
    fun `out of range priority levels are rejected`() {
        assertFailsWith<IllegalArgumentException> { Priority.fromLevel(0) }
        assertFailsWith<IllegalArgumentException> { Priority.fromLevel(6) }
    }

    @Test
    fun `before-deadline reminder needs a positive offset`() {
        assertFailsWith<IllegalArgumentException> { Reminder.BeforeDeadline(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { Reminder.BeforeDeadline(Duration.ofMinutes(-5)) }
    }

    @Test
    fun `an open task past its deadline is overdue`() {
        val task = aTask("a", deadline = NOW)
        assertTrue(task.isOverdue(NOW.plusSeconds(1)))
        assertFalse(task.isOverdue(NOW))
    }

    @Test
    fun `a done task is never overdue`() {
        val task = aTask("a", deadline = NOW).markDone(NOW)
        assertFalse(task.isOverdue(NOW.plus(Duration.ofDays(1))))
    }

    @Test
    fun `the lifecycle keeps the done time through archive and restore`() {
        val later = NOW.plus(Duration.ofDays(1))
        val done = aTask("a").markDone(NOW)
        val archived = done.archive(later)

        assertEquals(TaskStatus.Archived(doneAt = NOW, archivedAt = later), archived.status)
        assertEquals(NOW, archived.doneAt)
        assertEquals(TaskStatus.Done(NOW), archived.restore().status)
        assertEquals(TaskStatus.Open, done.markNotDone().status)
        assertNull(aTask("b").doneAt)
    }

    @Test
    fun `invalid transitions are programming errors`() {
        val open = aTask("a")
        assertFailsWith<IllegalStateException> { open.archive(NOW) }
        assertFailsWith<IllegalStateException> { open.markNotDone() }
        assertFailsWith<IllegalStateException> { open.restore() }
        assertFailsWith<IllegalStateException> { open.markDone(NOW).markDone(NOW) }
    }

    @Test
    fun `task lists select the right tasks`() {
        val work = CategoryId("work")
        val inWork = aTask("a", categoryId = work)
        val loose = aTask("b")

        assertTrue(TaskList.All.contains(inWork) && TaskList.All.contains(loose))
        assertTrue(TaskList.InCategory(work).contains(inWork))
        assertFalse(TaskList.InCategory(work).contains(loose))
        assertTrue(TaskList.Uncategorized.contains(loose))
        assertFalse(TaskList.Uncategorized.contains(inWork))
    }
}
