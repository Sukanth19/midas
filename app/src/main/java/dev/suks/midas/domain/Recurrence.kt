package dev.suks.midas.domain

import java.time.LocalDate

object Recurrence {

    /**
     * The next due date after [current] for a bill repeating every [interval] [frequency].
     *
     * [anchorDay] is the day of month the bill was set up on. Monthly and yearly bills snap back
     * to it, so a bill due on the 31st goes 31 Jan -> 28 Feb -> 31 Mar rather than drifting to the 28th.
     */
    fun next(current: LocalDate, frequency: Frequency, interval: Int = 1, anchorDay: Int = current.dayOfMonth): LocalDate {
        require(interval >= 1) { "interval must be at least 1" }
        return when (frequency) {
            Frequency.WEEKLY -> current.plusWeeks(interval.toLong())
            Frequency.MONTHLY -> clamp(current.plusMonths(interval.toLong()), anchorDay)
            Frequency.YEARLY -> clamp(current.plusYears(interval.toLong()), anchorDay)
        }
    }

    private fun clamp(date: LocalDate, anchorDay: Int): LocalDate =
        date.withDayOfMonth(anchorDay.coerceIn(1, date.lengthOfMonth()))

    /** Every occurrence from [first] that falls within [range], used for "bills this month". */
    fun occurrences(first: LocalDate, frequency: Frequency, interval: Int, anchorDay: Int, range: DateRange): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = first
        var guard = 0
        while (d.isBefore(range.start) && guard++ < 10_000) d = next(d, frequency, interval, anchorDay)
        while (!d.isAfter(range.endInclusive) && guard++ < 10_000) {
            out += d
            d = next(d, frequency, interval, anchorDay)
        }
        return out
    }

    /**
     * What a bill still needs paying from [today] to the end of the month: its overdue occurrences
     * plus those left this month. Paid occurrences are already behind [nextDue].
     */
    fun dueRestOfMonth(nextDue: LocalDate, frequency: Frequency, interval: Int, anchorDay: Int, amountMinor: Long, today: LocalDate): Long {
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val from = minOf(nextDue, today.withDayOfMonth(1))
        return occurrences(nextDue, frequency, interval, anchorDay, DateRange(from, monthEnd)).size * amountMinor
    }

    /**
     * The iCalendar repeat rule for a bill, for adding it to the phone's calendar.
     * Monthly bills on the 29th–31st land on the last day of shorter months, matching [next].
     */
    fun rrule(frequency: Frequency, interval: Int, anchorDay: Int): String {
        val every = if (interval > 1) ";INTERVAL=$interval" else ""
        return when (frequency) {
            Frequency.WEEKLY -> "FREQ=WEEKLY$every"
            Frequency.YEARLY -> "FREQ=YEARLY$every"
            Frequency.MONTHLY ->
                if (anchorDay <= 28) "FREQ=MONTHLY$every;BYMONTHDAY=$anchorDay"
                else "FREQ=MONTHLY$every;BYMONTHDAY=${(28..anchorDay).joinToString(",")};BYSETPOS=-1"
        }
    }

    /** What a recurring amount costs per month on average, for the "fixed costs" figure. */
    fun monthlyEquivalent(amountMinor: Long, frequency: Frequency, interval: Int): Long = when (frequency) {
        Frequency.WEEKLY -> Math.round(amountMinor * 52.0 / 12.0 / interval)
        Frequency.MONTHLY -> Math.round(amountMinor.toDouble() / interval)
        Frequency.YEARLY -> Math.round(amountMinor / 12.0 / interval)
    }
}
