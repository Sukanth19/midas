package dev.suks.midas.reminders

import dev.suks.midas.data.Bill
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillReminderTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun bill(name: String, inDays: Long, remind: Int = 2, active: Boolean = true) =
        Bill(id = name.hashCode().toLong(), name = name, amountMinor = 100_000, nextDue = today.plusDays(inDays), remindDaysBefore = remind, active = active)

    @Test fun `reminds within the window and for overdue bills`() {
        val bills = listOf(
            bill("later", 5),
            bill("edge", 2),
            bill("tomorrow", 1),
            bill("overdue", -3),
            bill("paused", 0, active = false),
            bill("same-day-only", 1, remind = 0),
            bill("today-same-day", 0, remind = 0),
        )
        assertEquals(listOf("overdue", "today-same-day", "tomorrow", "edge"), BillReminderWorker.dueForReminder(bills, today).map { it.name })
    }

    @Test fun `messages read naturally`() {
        assertEquals("₹1,000 is due today", BillReminderWorker.message(bill("a", 0), today, "INR"))
        assertEquals("₹1,000 is due tomorrow", BillReminderWorker.message(bill("a", 1), today, "INR"))
        assertEquals("₹1,000 is due in 3 days", BillReminderWorker.message(bill("a", 3), today, "INR"))
        assertEquals("₹1,000 was due 8 Nov", BillReminderWorker.message(bill("a", -3), today, "INR"))
    }
}
