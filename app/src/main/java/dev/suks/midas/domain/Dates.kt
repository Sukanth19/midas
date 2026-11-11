package dev.suks.midas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)
    val days: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()
}

object Dates {
    fun month(ym: YearMonth) = DateRange(ym.atDay(1), ym.atEndOfMonth())

    fun weekStart(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun week(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): DateRange {
        val s = weekStart(date, firstDay)
        return DateRange(s, s.plusDays(6))
    }

    fun daysUntil(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** "Today", "Tomorrow", "In 3 days", "2 days ago", "Yesterday". */
    fun relative(date: LocalDate, today: LocalDate): String {
        val d = daysUntil(today, date)
        return when {
            d == 0L -> "Today"
            d == 1L -> "Tomorrow"
            d == -1L -> "Yesterday"
            d in 2..6 -> "In $d days"
            d < -1 && d > -7 -> "${-d} days ago"
            else -> shortDate(date, today)
        }
    }

    /** [relative] for use mid-sentence: "due in 3 days", "due tomorrow", but "due 23 Nov". */
    fun relativeInline(date: LocalDate, today: LocalDate): String {
        val r = relative(date, today)
        return if (r.startsWith("In ") || r in setOf("Today", "Tomorrow", "Yesterday")) r.lowercase() else r
    }

    private val sameYear = DateTimeFormatter.ofPattern("d MMM")
    private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE, d MMM")

    fun shortDate(date: LocalDate, today: LocalDate): String =
        date.format(if (date.year == today.year) sameYear else otherYear)

    fun monthTitle(ym: YearMonth): String = ym.format(monthYear)

    fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayHeader)
    }
}
