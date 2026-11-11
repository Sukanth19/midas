package dev.suks.midas.widget

import android.content.Context
import dev.suks.midas.AppContainer
import dev.suks.midas.MidasApplication
import dev.suks.midas.data.Bill
import dev.suks.midas.data.GroceryItem
import dev.suks.midas.domain.BudgetProgress
import dev.suks.midas.domain.DateRange
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.GroceryTotals
import dev.suks.midas.domain.Stats
import dev.suks.midas.domain.Frequency
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/** Everything the widgets show, loaded in one go. */
data class WidgetData(
    val currency: String,
    val today: LocalDate,
    val monthLabel: String,
    val budget: BudgetProgress,
    val incomeMinor: Long,
    val last7Days: List<Long>,
    val grocery: List<GroceryItem>,
    val groceryTotals: GroceryTotals,
    val bills: List<Bill>,
    val dynamicColor: Boolean,
) {
    companion object {
        suspend fun load(context: Context): WidgetData = load((context.applicationContext as MidasApplication).container)

        suspend fun load(c: AppContainer): WidgetData {
            val today = c.today()
            val month = YearMonth.from(today)
            val s = c.settings.current()
            val points = c.transactions.getAll().map { it.toPoint() }
            val summary = Stats.summary(points, Dates.month(month), today)
            val grocery = c.grocery.getItems()
            return WidgetData(
                currency = s.currency,
                today = today,
                monthLabel = month.month.name.lowercase().replaceFirstChar(Char::uppercase),
                budget = BudgetProgress(s.monthlyBudgetMinor, summary.expenseMinor),
                incomeMinor = summary.incomeMinor,
                last7Days = Stats.dailyExpense(points, DateRange(today.minusDays(6), today)).map { it.second },
                grocery = grocery.sortedWith(compareBy({ it.checked }, { it.section }, { it.name })),
                groceryTotals = GroceryMath.totals(grocery.map { it.toLine() }),
                bills = c.bills.getActive().take(4),
                dynamicColor = s.dynamicColor,
            )
        }

        /** Realistic sample used for widget-picker previews, so the preview shows the real design. */
        fun sample(today: LocalDate = LocalDate.now()): WidgetData {
            val grocery = listOf(
                GroceryItem(1, "Milk", 2.0, "l", 6000, "Dairy & Eggs", checked = true),
                GroceryItem(2, "Eggs", 1.0, "dozen", 8400, "Dairy & Eggs", checked = true),
                GroceryItem(3, "Tomatoes", 1.0, "kg", 4000, "Produce"),
                GroceryItem(4, "Bananas", 1.0, "dozen", 6000, "Produce"),
                GroceryItem(5, "Rice", 5.0, "kg", 7000, "Pantry"),
                GroceryItem(6, "Bread", 1.0, "pcs", 5000, "Bakery"),
                GroceryItem(7, "Coffee", 1.0, "pack", 32000, "Drinks"),
            )
            return WidgetData(
                currency = "INR",
                today = today,
                monthLabel = today.month.name.lowercase().replaceFirstChar(Char::uppercase),
                budget = BudgetProgress(4_000_000, 2_684_000),
                incomeMinor = 9_500_000,
                last7Days = listOf(84000, 23000, 156000, 41000, 0, 212000, 67000),
                grocery = grocery.sortedBy { it.checked },
                groceryTotals = GroceryMath.totals(grocery.map { it.toLine() }),
                bills = listOf(
                    Bill(1, "Rent", 1_800_000, icon = "home", nextDue = today.plusDays(2)),
                    Bill(2, "Electricity", 240_000, icon = "bolt", nextDue = today.plusDays(5)),
                    Bill(3, "Internet", 79_900, icon = "wifi", nextDue = today.plusDays(9)),
                    Bill(4, "Netflix", 64_900, icon = "subscriptions", nextDue = today.plusDays(14), frequency = Frequency.MONTHLY),
                ),
                dynamicColor = false,
            )
        }
    }
}
