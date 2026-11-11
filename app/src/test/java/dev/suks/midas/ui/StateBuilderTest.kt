package dev.suks.midas.ui

import dev.suks.midas.data.Bill
import dev.suks.midas.data.Category
import dev.suks.midas.data.GroceryWeek
import dev.suks.midas.data.Settings
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.Frequency
import dev.suks.midas.domain.GroceryLine
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.bills.BillsViewModel
import dev.suks.midas.ui.home.HomeViewModel
import dev.suks.midas.ui.insights.InsightsViewModel
import dev.suks.midas.ui.transactions.TransactionsViewModel
import dev.suks.midas.ui.transactions.TxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The ViewModels compute their screen state in pure functions; these pin that logic down. */
class StateBuilderTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val food = Category(1, "Food", "restaurant", 0xFFEF6C00)
    private val rent = Category(2, "Rent", "home", 0xFF5E35B1)
    private val salary = Category(3, "Salary", "work", 0xFF2E7D32, type = TxType.INCOME)
    private val cats = listOf(food, rent, salary)
    private var nextId = 1L
    private fun t(day: LocalDate, amount: Long, cat: Category, title: String = cat.name, note: String = "") =
        Transaction(nextId++, amount, cat.type, cat.id, title, note, day)

    private val txs = listOf(
        t(today, 45_000, food, "Lunch"),
        t(today.minusDays(1), 1_800_000, rent),
        t(today.minusDays(1), 30_000, food, "Pizza", note = "friday treat"),
        t(LocalDate.of(2026, 11, 1), 9_000_000, salary),
        t(LocalDate.of(2026, 10, 15), 200_000, food, "October dinner"),
    )

    @Test fun `home state`() {
        val bills = listOf(
            Bill(1, "Netflix", 64_900, nextDue = today.plusDays(9)),
            Bill(2, "Rent", 1_800_000, nextDue = today.plusDays(20)),
            Bill(3, "Phone", 59_900, nextDue = today.minusDays(1)),
            Bill(4, "Old", 1, nextDue = today, active = false),
            Bill(5, "Water", 30_000, nextDue = today.plusDays(30)),
        )
        val s = HomeViewModel.build(
            today, Settings(currency = "INR", monthlyBudgetMinor = 3_000_000), txs, cats, bills,
            listOf(GroceryLine(2.0, 6_000, true), GroceryLine(1.0, 8_400, false)),
        )
        assertEquals(1_875_000L, s.spentMinor)
        assertEquals(9_000_000L, s.incomeMinor)
        assertEquals(1_125_000L, s.budget.remainingMinor)
        assertEquals(1_125_000L / 20, s.dailyAllowanceMinor) // 20 days left incl. today
        assertEquals(listOf(rent.id, food.id), s.shares.map { it.first.categoryId })
        assertEquals(listOf("Phone", "Netflix", "Rent"), s.upcomingBills.map { it.name })
        assertEquals(7, s.last7Days.size)
        assertEquals(45_000L, s.last7Days.last().second)
        assertEquals(20_400L, s.grocery.estimatedMinor)
        assertEquals(5, s.recent.size)
    }

    @Test fun `transactions are filtered, searched and grouped by day`() {
        val all = TransactionsViewModel.build(TxFilter(month = YearMonth.of(2026, 11)), txs, cats, "INR", today)
        assertEquals(listOf(today, today.minusDays(1), LocalDate.of(2026, 11, 1)), all.groups.map { it.date })
        assertEquals(1_830_000L, all.groups[1].expenseMinor)
        assertEquals(listOf(food, rent, salary).sortedBy { it.name }, all.usedCategories)

        val search = TransactionsViewModel.build(TxFilter(query = "treat", month = YearMonth.of(2026, 11)), txs, cats, "INR", today)
        assertEquals(listOf("Pizza"), search.groups.flatMap { it.items }.map { it.title })

        val byCategoryName = TransactionsViewModel.build(TxFilter(query = "rent", month = YearMonth.of(2026, 11)), txs, cats, "INR", today)
        assertEquals(1, byCategoryName.groups.flatMap { it.items }.size)

        val income = TransactionsViewModel.build(TxFilter(type = TxType.INCOME, month = YearMonth.of(2026, 11)), txs, cats, "INR", today)
        assertEquals(9_000_000L, income.totalIncomeMinor)
        assertEquals(0L, income.totalExpenseMinor)

        val october = TransactionsViewModel.build(TxFilter(month = YearMonth.of(2026, 10)), txs, cats, "INR", today)
        assertEquals(listOf("October dinner"), october.groups.flatMap { it.items }.map { it.title })
    }

    @Test fun `bills are bucketed and totalled`() {
        val bills = listOf(
            Bill(1, "Phone", 59_900, nextDue = today.minusDays(2)),
            Bill(2, "Netflix", 64_900, nextDue = today.plusDays(3)),
            Bill(3, "Rent", 1_800_000, nextDue = today.plusDays(15)),
            Bill(4, "Insurance", 1_200_000, frequency = Frequency.YEARLY, nextDue = LocalDate.of(2027, 3, 1)),
            Bill(5, "Gym", 150_000, nextDue = today, active = false),
        )
        val s = BillsViewModel.build(bills, cats, "INR", today)
        assertEquals(listOf("Phone"), s.overdue.map { it.name })
        assertEquals(listOf("Netflix"), s.soon.map { it.name })
        assertEquals(listOf("Rent", "Insurance"), s.later.map { it.name })
        assertEquals(listOf("Gym"), s.paused.map { it.name })
        assertEquals(59_900L + 64_900L + 1_800_000L, s.dueThisMonthMinor)
        assertEquals(59_900L + 64_900L + 1_800_000L + 100_000L, s.monthlyFixedMinor)
        assertEquals(listOf(food, rent), s.categories)
    }

    @Test fun `insights for the current month`() {
        val weeks = listOf(GroceryWeek(1, today.minusWeeks(1), 100, 120, 3), GroceryWeek(2, today.minusWeeks(2), 100, 90, 3))
        val s = InsightsViewModel.build(YearMonth.of(2026, 11), today, txs, cats, "INR", 3_000_000, weeks)
        assertEquals(1_875_000L, s.summary.expenseMinor)
        assertEquals(200_000L, s.previousExpenseMinor)
        assertEquals((1_875_000f - 200_000f) / 200_000f, s.change!!, 0.0001f)
        assertEquals(30, s.daily.size)
        assertEquals(11, s.cumulative.size) // only drawn up to today
        assertEquals(1_875_000L, s.cumulative.last())
        assertEquals(31, s.previousCumulative.size)
        assertEquals(6, s.months.size)
        assertEquals(100_000L, s.dailyBudgetMinor)
        assertEquals(listOf(90L, 120L), s.groceryWeeks.map { it.actualMinor }) // oldest first
        assertEquals("Rent", s.biggest.first().title)
        assertTrue(s.biggest.none { it.type == TxType.INCOME })
    }

    @Test fun `insights for a month with no data`() {
        val s = InsightsViewModel.build(YearMonth.of(2025, 1), today, txs, cats, "INR", 0, emptyList())
        assertEquals(0L, s.summary.expenseMinor)
        assertNull(s.change)
        assertNull(s.dailyBudgetMinor)
        assertTrue(s.shares.isEmpty())
    }
}
