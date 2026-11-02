package dev.mathieu.youcandoit.data

import dev.mathieu.youcandoit.data.local.ReminderColumns
import dev.mathieu.youcandoit.data.local.reminderFrom
import dev.mathieu.youcandoit.data.local.statusFrom
import dev.mathieu.youcandoit.data.local.toDomain
import dev.mathieu.youcandoit.data.local.toEntity
import dev.mathieu.youcandoit.domain.model.Reminder
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class MappersTest {
    @Test
    fun `before-deadline offset is stored in seconds and read back`() {
        val task = aTask("a").copy(
            deadline = NOW.plus(Duration.ofDays(1)),
            reminder = Reminder.BeforeDeadline(Duration.ofMinutes(90)),
        )

        val entity = task.toEntity()

        assertEquals(ReminderColumns.BEFORE_DEADLINE, entity.reminderType)
        assertEquals(5400L, entity.reminderOffsetSeconds)
        assertEquals(task, entity.toDomain())
    }

    @Test
    fun `a reminder at a chosen time is stored as epoch millis and read back`() {
        val task = aTask("a").copy(reminder = Reminder.At(NOW.plus(Duration.ofHours(5))))

        val entity = task.toEntity()

        assertEquals(ReminderColumns.AT_TIME, entity.reminderType)
        assertEquals(NOW.plus(Duration.ofHours(5)).toEpochMilli(), entity.reminderAt)
        assertEquals(task, entity.toDomain())
        assertFailsWith<IllegalArgumentException> { reminderFrom(ReminderColumns.AT_TIME, null, null) }
    }

    @Test
    fun `unknown reminder types fail loudly instead of being guessed`() {
        assertFailsWith<IllegalStateException> { reminderFrom("SOMETIMES", null) }
    }

    @Test
    fun `before-deadline without an offset is rejected`() {
        assertFailsWith<IllegalArgumentException> { reminderFrom(ReminderColumns.BEFORE_DEADLINE, null) }
    }

    @Test
    fun `an archived row without a done time is rejected`() {
        assertFailsWith<IllegalArgumentException> { statusFrom(doneAt = null, archivedAt = 1L) }
    }
}
