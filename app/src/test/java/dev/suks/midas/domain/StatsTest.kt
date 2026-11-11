package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {
    private fun d(day: Int, month: Int = 11) = LocalDate.of(2026, month, day)
    private fun exp(day: Int, amount: Long, cat: Long? = 1, month: Int = 11) = TxPoint(d(day, month), amount, TxType.EXPENSE, cat)
    private fun inc(day: Int, amount: Long, month: Int = 11) = TxPoint(d(day, month), amount, TxType.INCOME, 99)

    private val nov = Dates.month(YearMonth.of(2026, 11))
    private val points = listOf(
        exp(1, 1_000, 1), exp(1, 500, 2), exp(3, 2_000, 1), exp(10, 1_500, 3), inc(1, 50_000),
        exp(28, 9_999, 1, month = 10), // previous month, must be ignored
    )

    @Test fun `summary of the current month averages over elapsed days only`() {
        val s = Stats.summary(points, nov, today = d(10))
        assertEquals(5_000L, s.expenseMinor)
        assertEquals(50_000L, s.incomeMinor)
        assertEquals(45_000L, s.netMinor)
        assertEquals(5, s.transactionCount)
        assertEquals(500L, s.averageDailyExpenseMinor) // 5000 / 10 days
        assertEquals(2_000L, s.largestExpenseMinor)
        assertEquals(0.9f, s.savingsRate!!, 0.0001f)
    }

    @Test fun `summary of a past month uses the whole month`() {
        val s = Stats.summary(points, nov, today = d(5, 12))
        assertEquals(166L, s.averageDailyExpenseMinor) // 5000 / 30
    }

    @Test fun `savings rate is null without income`() {
        assertNull(Stats.summary(listOf(exp(1, 100)), nov, d(5)).savingsRate)
    }

    @Test fun `category split is sorted and sums to one`() {
        val shares = Stats.byCategory(points, nov)
        assertEquals(listOf(1L, 3L, 2L), shares.map { it.categoryId })
        assertEquals(3_000L, shares[0].totalMinor)
        assertEquals(2, shares[0].count)
        assertEquals(1f, shares.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
        assertEquals(emptyList<CategoryShare>(), Stats.byCategory(emptyList(), nov))
    }

    @Test fun `daily series is zero filled`() {
        val daily = Stats.dailyExpense(points, DateRange(d(1), d(4)))
        assertEquals(listOf(1_500L, 0L, 2_000L, 0L), daily.map { it.second })
        assertEquals(listOf(1_500L, 1_500L, 3_500L, 3_500L), Stats.cumulative(daily))
    }

    @Test fun `monthly totals go oldest first`() {
        val months = Stats.monthlyTotals(points, YearMonth.of(2026, 11), 3)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months.map { it.month })
        assertEquals(listOf(0L, 9_999L, 5_000L), months.map { it.expenseMinor })
        assertEquals(45_000L, months.last().netMinor)
    }

    @Test fun `percent change`() {
        assertEquals(0.5f, Stats.change(1_000, 1_500)!!, 0.0001f)
        assertEquals(-0.25f, Stats.change(1_000, 750)!!, 0.0001f)
        assertNull(Stats.change(0, 750))
    }
}
