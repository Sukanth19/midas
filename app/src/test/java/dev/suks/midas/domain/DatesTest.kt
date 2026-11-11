package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DatesTest {
    private val today = LocalDate.of(2026, 11, 11) // a Wednesday

    @Test fun `month range`() {
        val r = Dates.month(YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 1), r.start)
        assertEquals(LocalDate.of(2028, 2, 29), r.endInclusive)
        assertEquals(29, r.days)
        assertTrue(LocalDate.of(2028, 2, 29) in r)
        assertFalse(LocalDate.of(2028, 3, 1) in r)
    }

    @Test fun `week starts on monday by default`() {
        assertEquals(LocalDate.of(2026, 11, 9), Dates.weekStart(today))
        assertEquals(LocalDate.of(2026, 11, 8), Dates.weekStart(today, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 9), Dates.weekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(7, Dates.week(today).days)
    }

    @Test fun `relative labels`() {
        assertEquals("Today", Dates.relative(today, today))
        assertEquals("Tomorrow", Dates.relative(today.plusDays(1), today))
        assertEquals("Yesterday", Dates.relative(today.minusDays(1), today))
        assertEquals("In 3 days", Dates.relative(today.plusDays(3), today))
        assertEquals("3 days ago", Dates.relative(today.minusDays(3), today))
        assertEquals("25 Nov", Dates.relative(today.plusDays(14), today))
        assertEquals("5 Jan 2027", Dates.relative(LocalDate.of(2027, 1, 5), today))
    }

    @Test fun `inline relative keeps month names capitalised`() {
        assertEquals("in 3 days", Dates.relativeInline(today.plusDays(3), today))
        assertEquals("tomorrow", Dates.relativeInline(today.plusDays(1), today))
        assertEquals("25 Nov", Dates.relativeInline(today.plusDays(14), today))
        assertEquals("3 days ago", Dates.relativeInline(today.minusDays(3), today))
    }

    @Test fun `day headers`() {
        assertEquals("Today", Dates.dayHeader(today, today))
        assertEquals("Yesterday", Dates.dayHeader(today.minusDays(1), today))
        assertEquals("Sunday, 8 Nov", Dates.dayHeader(LocalDate.of(2026, 11, 8), today))
    }
}
