package dev.mathieu.youcandoit.ui

import dev.mathieu.youcandoit.ui.common.RelativeDay
import dev.mathieu.youcandoit.ui.common.relativeDay
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

class RelativeDayTest {
    private val today = LocalDate.of(2026, 11, 1) // a Sunday

    @Test
    fun `today, tomorrow and yesterday have their own words`() {
        assertEquals(RelativeDay.Today, relativeDay(today, today))
        assertEquals(RelativeDay.Tomorrow, relativeDay(today.plusDays(1), today))
        assertEquals(RelativeDay.Yesterday, relativeDay(today.minusDays(1), today))
    }

    @Test
    fun `the coming week uses the weekday`() {
        assertEquals(RelativeDay.ThisWeek(DayOfWeek.WEDNESDAY), relativeDay(today.plusDays(3), today))
        assertEquals(RelativeDay.ThisWeek(DayOfWeek.SATURDAY), relativeDay(today.plusDays(6), today))
    }

    @Test
    fun `further dates are shown as dates, with the year only when it differs`() {
        assertEquals(RelativeDay.Other(today.plusDays(7), sameYear = true), relativeDay(today.plusDays(7), today))
        assertEquals(RelativeDay.Other(today.minusDays(5), sameYear = true), relativeDay(today.minusDays(5), today))
        val nextYear = LocalDate.of(2027, 1, 15)
        assertEquals(RelativeDay.Other(nextYear, sameYear = false), relativeDay(nextYear, today))
    }
}
