package dev.suks.midas.domain

import java.time.LocalDate
import java.time.YearMonth

/** Minimal shape of a transaction for aggregation; independent of Room. */
data class TxPoint(val date: LocalDate, val amountMinor: Long, val type: TxType, val categoryId: Long?)

data class CategoryShare(val categoryId: Long?, val totalMinor: Long, val fraction: Float, val count: Int)

data class MonthTotal(val month: YearMonth, val expenseMinor: Long, val incomeMinor: Long) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

data class PeriodSummary(
    val expenseMinor: Long,
    val incomeMinor: Long,
    val transactionCount: Int,
    val averageDailyExpenseMinor: Long,
    val largestExpenseMinor: Long,
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
    /** Share of income kept, 0..1; null without income. */
    val savingsRate: Float? get() = if (incomeMinor <= 0) null else (netMinor.toFloat() / incomeMinor)
}

object Stats {

    fun summary(points: List<TxPoint>, range: DateRange, today: LocalDate): PeriodSummary {
        val inRange = points.filter { it.date in range }
        val expenses = inRange.filter { it.type == TxType.EXPENSE }
        val expense = expenses.sumOf { it.amountMinor }
        val income = inRange.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        // For the current period, average over the days that have actually happened.
        val elapsedEnd = if (today in range) today else range.endInclusive
        val days = if (today.isBefore(range.start)) 1 else DateRange(range.start, elapsedEnd).days.coerceAtLeast(1)
        return PeriodSummary(
            expenseMinor = expense,
            incomeMinor = income,
            transactionCount = inRange.size,
            averageDailyExpenseMinor = expense / days,
            largestExpenseMinor = expenses.maxOfOrNull { it.amountMinor } ?: 0,
        )
    }

    /** Expense split by category, largest first. Fractions sum to 1 (or the list is empty). */
    fun byCategory(points: List<TxPoint>, range: DateRange): List<CategoryShare> {
        val expenses = points.filter { it.type == TxType.EXPENSE && it.date in range }
        val total = expenses.sumOf { it.amountMinor }
        if (total == 0L) return emptyList()
        return expenses.groupBy { it.categoryId }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amountMinor }
                CategoryShare(cat, sum, sum.toFloat() / total, list.size)
            }
            .sortedByDescending { it.totalMinor }
    }

    /** One expense total per day of [range], zero-filled, for the daily bar chart. */
    fun dailyExpense(points: List<TxPoint>, range: DateRange): List<Pair<LocalDate, Long>> {
        val byDay = points.asSequence()
            .filter { it.type == TxType.EXPENSE && it.date in range }
            .groupBy({ it.date }, { it.amountMinor })
            .mapValues { it.value.sum() }
        return (0 until range.days).map { i ->
            val d = range.start.plusDays(i.toLong())
            d to (byDay[d] ?: 0L)
        }
    }

    /** Running total of expense through [range], for the cumulative "pace" line. */
    fun cumulative(daily: List<Pair<LocalDate, Long>>): List<Long> {
        var acc = 0L
        return daily.map { acc += it.second; acc }
    }

    /** The last [count] months ending at [endMonth], oldest first, zero-filled. */
    fun monthlyTotals(points: List<TxPoint>, endMonth: YearMonth, count: Int): List<MonthTotal> {
        val grouped = points.groupBy { YearMonth.from(it.date) }
        return (count - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            val list = grouped[m].orEmpty()
            MonthTotal(
                month = m,
                expenseMinor = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
                incomeMinor = list.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
            )
        }
    }

    /** Relative change from [previous] to [current]; null when there is nothing to compare with. */
    fun change(previous: Long, current: Long): Float? =
        if (previous == 0L) null else (current - previous).toFloat() / previous
}
