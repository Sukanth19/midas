package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun `monthly bill on the 31st clamps then snaps back`() {
        val jan31 = d("2026-01-31")
        val feb = Recurrence.next(jan31, Frequency.MONTHLY, 1, anchorDay = 31)
        assertEquals(d("2026-02-28"), feb)
        val mar = Recurrence.next(feb, Frequency.MONTHLY, 1, anchorDay = 31)
        assertEquals(d("2026-03-31"), mar)
        assertEquals(d("2026-04-30"), Recurrence.next(mar, Frequency.MONTHLY, 1, anchorDay = 31))
    }

    @Test fun `leap year february`() {
        assertEquals(d("2028-02-29"), Recurrence.next(d("2028-01-30"), Frequency.MONTHLY, 1, anchorDay = 30))
    }

    @Test fun `weekly and every-two-weeks`() {
        assertEquals(d("2026-11-18"), Recurrence.next(d("2026-11-11"), Frequency.WEEKLY))
        assertEquals(d("2026-11-25"), Recurrence.next(d("2026-11-11"), Frequency.WEEKLY, 2))
    }

    @Test fun `quarterly and yearly`() {
        assertEquals(d("2027-02-11"), Recurrence.next(d("2026-11-11"), Frequency.MONTHLY, 3))
        assertEquals(d("2027-02-28"), Recurrence.next(d("2026-02-28"), Frequency.YEARLY, 1, anchorDay = 28))
        assertEquals(d("2029-02-28"), Recurrence.next(d("2028-02-29"), Frequency.YEARLY, 1, anchorDay = 29))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interval must be positive`() {
        Recurrence.next(d("2026-01-01"), Frequency.MONTHLY, 0)
    }

    @Test fun `occurrences inside a month`() {
        val nov = Dates.month(java.time.YearMonth.of(2026, 11))
        val weekly = Recurrence.occurrences(d("2026-10-28"), Frequency.WEEKLY, 1, 28, nov)
        assertEquals(listOf("2026-11-04", "2026-11-11", "2026-11-18", "2026-11-25").map(::d), weekly)
        assertEquals(listOf(d("2026-11-05")), Recurrence.occurrences(d("2026-11-05"), Frequency.MONTHLY, 1, 5, nov))
        assertEquals(emptyList<LocalDate>(), Recurrence.occurrences(d("2026-12-05"), Frequency.MONTHLY, 1, 5, nov))
    }

    @Test fun `monthly equivalent of recurring costs`() {
        assertEquals(1_200L, Recurrence.monthlyEquivalent(1_200, Frequency.MONTHLY, 1))
        assertEquals(1_000L, Recurrence.monthlyEquivalent(12_000, Frequency.YEARLY, 1))
        assertEquals(4_333L, Recurrence.monthlyEquivalent(1_000, Frequency.WEEKLY, 1))
        assertEquals(500L, Recurrence.monthlyEquivalent(1_500, Frequency.MONTHLY, 3))
    }
}
