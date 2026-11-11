package dev.suks.midas.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Category
import dev.suks.midas.data.GroceryWeek
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.CategoryShare
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.MonthTotal
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.PeriodSummary
import dev.suks.midas.domain.Stats
import dev.suks.midas.ui.components.BarChart
import dev.suks.midas.ui.components.DonutChart
import dev.suks.midas.ui.components.DonutSlice
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.components.LegendRow
import dev.suks.midas.ui.components.LineChart
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.components.StatTile
import dev.suks.midas.ui.components.asPercent
import dev.suks.midas.ui.components.signedPercent
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import dev.suks.midas.ui.theme.money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class InsightsState(
    val loaded: Boolean = false,
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val summary: PeriodSummary = PeriodSummary(0, 0, 0, 0, 0),
    val previousExpenseMinor: Long = 0,
    val shares: List<Pair<CategoryShare, Category?>> = emptyList(),
    val daily: List<Pair<LocalDate, Long>> = emptyList(),
    val cumulative: List<Long> = emptyList(),
    val previousCumulative: List<Long> = emptyList(),
    val months: List<MonthTotal> = emptyList(),
    val dailyBudgetMinor: Long? = null,
    val groceryWeeks: List<GroceryWeek> = emptyList(),
    val biggest: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
) {
    val change: Float? get() = Stats.change(previousExpenseMinor, summary.expenseMinor)
}

class InsightsViewModel(private val c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(c.today()))

    val state = stateOf(
        combine(month, c.transactions.all, c.categories.categories, c.settings.settings, c.grocery.weeks) { m, txs, cats, s, weeks ->
            build(m, c.today(), txs, cats, s.currency, s.monthlyBudgetMinor, weeks)
        },
        InsightsState(),
    )

    fun shift(by: Long) = month.update { it.plusMonths(by) }

    companion object {
        fun build(m: YearMonth, today: LocalDate, txs: List<Transaction>, cats: List<Category>, currency: String, budget: Long, weeks: List<GroceryWeek>): InsightsState {
            val points = txs.map { it.toPoint() }
            val range = Dates.month(m)
            val prevRange = Dates.month(m.minusMonths(1))
            val byId = cats.associateBy { it.id }
            val daily = Stats.dailyExpense(points, range)
            // Only draw the current month's line up to today.
            val shownDaily = if (m == YearMonth.from(today)) daily.take(today.dayOfMonth) else daily
            return InsightsState(
                loaded = true, currency = currency, today = today, month = m,
                summary = Stats.summary(points, range, today),
                previousExpenseMinor = Stats.summary(points, prevRange, today).expenseMinor,
                shares = Stats.byCategory(points, range).map { it to it.categoryId?.let(byId::get) },
                daily = daily,
                cumulative = Stats.cumulative(shownDaily),
                previousCumulative = Stats.cumulative(Stats.dailyExpense(points, prevRange)),
                months = Stats.monthlyTotals(points, m, 6),
                dailyBudgetMinor = if (budget > 0) budget / m.lengthOfMonth() else null,
                groceryWeeks = weeks.take(12).reversed(),
                biggest = txs.filter { it.date in range && it.type == dev.suks.midas.domain.TxType.EXPENSE }.sortedByDescending { it.amountMinor }.take(5),
                categories = byId,
            )
        }
    }
}

@Composable
fun InsightsScreen(onBudgets: () -> Unit) {
    val vm = midasViewModel { InsightsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    InsightsContent(state, vm::shift, onBudgets)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsContent(state: InsightsState, onShift: (Long) -> Unit = {}, onBudgets: () -> Unit = {}) {
    val cur = state.currency
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                actions = {
                    IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous month") }
                    Text(Dates.monthTitle(state.month), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { onShift(1) }, enabled = state.month < YearMonth.from(state.today)) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next month") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ch = state.change
                    StatTile(
                        "Spent", Money.formatCompact(state.summary.expenseMinor, cur), Modifier.weight(1f).testTag("stat_spent"),
                        supporting = ch?.let { signedPercent(it) + " vs last month" } ?: "No data last month",
                        icon = if ((ch ?: 0f) > 0f) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                        accent = if ((ch ?: 0f) > 0f) MaterialTheme.colorScheme.error else MaterialTheme.money.positive,
                    )
                    StatTile(
                        "Saved", Money.formatCompact(state.summary.netMinor, cur), Modifier.weight(1f),
                        supporting = state.summary.savingsRate?.let { "${it.asPercent()} of income" } ?: "No income logged",
                        icon = Icons.Rounded.Savings, accent = MaterialTheme.money.positive,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Daily average", Money.formatCompact(state.summary.averageDailyExpenseMinor, cur), Modifier.weight(1f), icon = Icons.Rounded.CalendarToday)
                    StatTile("Biggest expense", Money.formatCompact(state.summary.largestExpenseMinor, cur), Modifier.weight(1f), supporting = "${state.summary.transactionCount} transactions", icon = Icons.Rounded.Speed)
                }
            }
            if (state.loaded && state.summary.expenseMinor == 0L && state.summary.incomeMinor == 0L) {
                item { EmptyState(Icons.Rounded.Insights, "No data for ${Dates.monthTitle(state.month)}", "Charts appear as soon as you record some spending.") }
            }
            if (state.shares.isNotEmpty()) item { CategoryCard(state) }
            if (state.summary.expenseMinor > 0) {
                item {
                    MidasCard {
                        Text("Daily spending", style = MaterialTheme.typography.titleMedium)
                        if (state.dailyBudgetMinor != null) {
                            Text("Dashed line = daily budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        val todayIdx = state.daily.indexOfFirst { it.first == state.today }.takeIf { it >= 0 }
                        BarChart(
                            values = state.daily.map { it.second.toFloat() },
                            labels = state.daily.map { "${it.first.dayOfMonth}" },
                            detailLabels = state.daily.map { Dates.dayHeader(it.first, state.today) },
                            highlight = todayIdx ?: state.daily.indexOfLast { it.second > 0 },
                            reference = state.dailyBudgetMinor?.toFloat(),
                            labelEvery = 5,
                            valueLabel = { Money.format(it.toLong(), cur) },
                        )
                    }
                }
                item {
                    MidasCard {
                        Text("Spending pace", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Running total this month against last month (dashed)",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        LineChart(state.cumulative.map { it.toFloat() }, comparison = state.previousCumulative.map { it.toFloat() })
                    }
                }
            }
            if (state.months.any { it.expenseMinor > 0 || it.incomeMinor > 0 }) {
                item {
                    MidasCard {
                        Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        BarChart(
                            values = state.months.map { it.expenseMinor.toFloat() },
                            labels = state.months.map { it.month.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase) },
                            valueLabel = { Money.format(it.toLong(), cur) },
                        )
                        Spacer(Modifier.height(8.dp))
                        state.months.takeLast(3).reversed().forEach { m ->
                            LegendRow(
                                if (m.netMinor >= 0) MaterialTheme.money.positive else MaterialTheme.colorScheme.error,
                                Dates.monthTitle(m.month), Money.format(m.expenseMinor, cur),
                                supporting = "net " + (if (m.netMinor >= 0) "+" else "−") + Money.formatCompact(kotlin.math.abs(m.netMinor), cur),
                            )
                        }
                    }
                }
            }
            if (state.groceryWeeks.size >= 2) {
                item {
                    MidasCard {
                        Text("Grocery cost trend", style = MaterialTheme.typography.titleMedium)
                        val avg = state.groceryWeeks.map { it.actualMinor }.average().toLong()
                        Text("Average week ${Money.format(avg, cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        LineChart(state.groceryWeeks.map { it.actualMinor.toFloat() }, color = MaterialTheme.colorScheme.tertiary, height = 110.dp)
                    }
                }
            }
            if (state.biggest.isNotEmpty()) {
                item {
                    MidasCard {
                        Text("Biggest expenses", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        state.biggest.forEach { tx ->
                            val cat = tx.categoryId?.let(state.categories::get)
                            LegendRow(Color(cat?.color ?: 0xFF546E7A), tx.title, Money.format(tx.amountMinor, cur), supporting = Dates.shortDate(tx.date, state.today))
                        }
                    }
                }
            }
            item {
                TextButton(onClick = onBudgets, modifier = Modifier.fillMaxWidth()) { Text("Manage budgets") }
            }
        }
    }
}

@Composable
private fun CategoryCard(state: InsightsState) {
    var selected by remember(state.month) { mutableStateOf<Int?>(null) }
    val top = state.shares.take(7)
    val rest = state.shares.drop(7)
    val restTotal = rest.sumOf { it.first.totalMinor }
    val slices = top.map { (s, c) -> DonutSlice(s.totalMinor.toFloat(), Color(c?.color ?: 0xFF546E7A), c?.name ?: "Uncategorised") } +
        (if (restTotal > 0) listOf(DonutSlice(restTotal.toFloat(), Color(0xFF9E9E9E), "Everything else")) else emptyList())
    MidasCard {
        Text("By category", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DonutChart(slices, size = 210.dp, selected = selected, onSelect = { selected = it }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val sel = selected?.let { slices.getOrNull(it) }
                    Text(sel?.label ?: "Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Money.formatCompact(sel?.value?.toLong() ?: state.summary.expenseMinor, state.currency),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        state.shares.forEach { (s, c) ->
            LegendRow(
                Color(c?.color ?: 0xFF546E7A), c?.name ?: "Uncategorised", Money.format(s.totalMinor, state.currency),
                supporting = s.fraction.asPercent(), modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
