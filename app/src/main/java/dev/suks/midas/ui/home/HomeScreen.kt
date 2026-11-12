package dev.suks.midas.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Bill
import dev.suks.midas.data.Category
import dev.suks.midas.data.Settings
import dev.suks.midas.data.Transaction
import dev.suks.midas.data.Workspace
import dev.suks.midas.domain.BudgetProgress
import dev.suks.midas.domain.Budgets
import dev.suks.midas.domain.CategoryShare
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.GroceryTotals
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.Stats
import dev.suks.midas.ui.components.BarChart
import dev.suks.midas.ui.components.BudgetBar
import dev.suks.midas.ui.components.CategoryBadge
import dev.suks.midas.ui.components.DonutChart
import dev.suks.midas.ui.components.DonutSlice
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.components.LegendRow
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.components.SectionHeader
import dev.suks.midas.ui.components.asPercent
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import dev.suks.midas.ui.theme.HeroAmount
import dev.suks.midas.ui.theme.money
import dev.suks.midas.ui.transactions.TransactionRow
import dev.suks.midas.ui.workspaces.WorkspaceChip
import dev.suks.midas.ui.workspaces.WorkspaceSheet
import dev.suks.midas.ui.workspaces.rememberWorkspaces
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class HomeState(
    val loaded: Boolean = false,
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val spentMinor: Long = 0,
    val incomeMinor: Long = 0,
    val budget: BudgetProgress = BudgetProgress(0, 0),
    val dailyAllowanceMinor: Long? = null,
    val projectedMinor: Long = 0,
    val shares: List<Pair<CategoryShare, Category?>> = emptyList(),
    val last7Days: List<Pair<LocalDate, Long>> = emptyList(),
    val upcomingBills: List<Bill> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val grocery: GroceryTotals = GroceryTotals(0, 0, 0, 0),
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val state = stateOf(
        combine(c.settings.settings, c.transactions.all, c.categories.categories, c.bills.bills, c.grocery.items) { s: Settings, txs, cats, bills, groceries ->
            build(c.today(), s, txs, cats, bills, groceries.map { it.toLine() })
        },
        HomeState(),
    )

    companion object {
        fun build(
            today: LocalDate, s: Settings, txs: List<Transaction>, cats: List<Category>, bills: List<Bill>,
            grocery: List<dev.suks.midas.domain.GroceryLine>,
        ): HomeState {
            val month = YearMonth.from(today)
            val range = Dates.month(month)
            val points = txs.map { it.toPoint() }
            val summary = Stats.summary(points, range, today)
            val budget = BudgetProgress(s.monthlyBudgetMinor, summary.expenseMinor)
            val byId = cats.associateBy { it.id }
            return HomeState(
                loaded = true,
                currency = s.currency,
                today = today,
                spentMinor = summary.expenseMinor,
                incomeMinor = summary.incomeMinor,
                budget = budget,
                dailyAllowanceMinor = Budgets.dailyAllowance(budget, month, today),
                projectedMinor = Budgets.projectedSpend(summary.expenseMinor, month, today),
                shares = Stats.byCategory(points, range).take(4).map { it to it.categoryId?.let(byId::get) },
                last7Days = Stats.dailyExpense(points, dev.suks.midas.domain.DateRange(today.minusDays(6), today)),
                upcomingBills = bills.filter { it.active }.sortedBy { it.nextDue }.take(3),
                recent = txs.take(6),
                categories = byId,
                grocery = GroceryMath.totals(grocery),
            )
        }
    }
}

@Composable
fun HomeScreen(
    onAdd: () -> Unit, onOpenTransaction: (Long) -> Unit, onSeeAll: () -> Unit, onBudgets: () -> Unit,
    onBills: () -> Unit, onGroceries: () -> Unit, onSettings: () -> Unit,
) {
    val vm = midasViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val workspacesVm = rememberWorkspaces()
    val workspaces by workspacesVm.workspaces.collectAsStateWithLifecycle()
    val activeId by workspacesVm.activeId.collectAsStateWithLifecycle()
    var switching by remember { mutableStateOf(false) }
    HomeContent(
        state, onAdd, onOpenTransaction, onSeeAll, onBudgets, onBills, onGroceries, onSettings,
        workspace = workspaces.firstOrNull { it.id == activeId },
        onWorkspaces = { switching = true },
    )
    if (switching) WorkspaceSheet(workspacesVm, onDismiss = { switching = false })
}

private fun greeting(now: LocalTime = LocalTime.now()) = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeState,
    onAdd: () -> Unit = {}, onOpenTransaction: (Long) -> Unit = {}, onSeeAll: () -> Unit = {}, onBudgets: () -> Unit = {},
    onBills: () -> Unit = {}, onGroceries: () -> Unit = {}, onSettings: () -> Unit = {},
    workspace: Workspace? = null, onWorkspaces: () -> Unit = {},
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting(), style = MaterialTheme.typography.titleLarge)
                        Text(Dates.monthTitle(YearMonth.from(state.today)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (workspace != null) WorkspaceChip(workspace, onWorkspaces)
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add") }, modifier = Modifier.testTag("add_fab"))
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroCard(state, onBudgets) }
            item { QuickActions(onAdd, onGroceries, onBills, onSeeAll) }
            if (state.shares.isNotEmpty()) item { WhereItWentCard(state) }
            if (state.last7Days.any { it.second > 0 }) item { WeekCard(state) }
            if (state.upcomingBills.isNotEmpty()) item { BillsCard(state, onBills) }
            if (state.grocery.itemCount > 0) item { GroceryCard(state, onGroceries) }
            item {
                SectionHeader("Recent") {
                    if (state.recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
            if (state.loaded && state.recent.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Wallet, "Nothing tracked yet",
                        "Add your first expense and Midas will start building your stats.",
                    )
                }
            }
            items(state.recent, key = { it.id }) { tx ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TransactionRow(
                        tx, tx.categoryId?.let(state.categories::get), state.currency, onClick = { onOpenTransaction(tx.id) },
                        subtitle = Dates.relative(tx.date, state.today),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: HomeState, onBudgets: () -> Unit) {
    MidasCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        val on = MaterialTheme.colorScheme.onPrimaryContainer
        Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
        Text(Money.format(state.spentMinor, state.currency), style = HeroAmount, color = on, modifier = Modifier.testTag("hero_spent"))
        Spacer(Modifier.height(12.dp))
        if (state.budget.limitMinor > 0) {
            BudgetBar(state.budget)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val left = state.budget.remainingMinor
                Text(
                    if (left >= 0) "${Money.format(left, state.currency)} left of ${Money.format(state.budget.limitMinor, state.currency)}"
                    else "${Money.format(-left, state.currency)} over budget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (left >= 0) on else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                state.dailyAllowanceMinor?.let {
                    Text("${Money.formatCompact(it, state.currency)}/day", style = MaterialTheme.typography.labelLarge, color = on)
                }
            }
        } else {
            FilledTonalButton(onClick = onBudgets) { Text("Set a monthly budget") }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroChip(Icons.Rounded.ArrowDownward, "Income", Money.formatCompact(state.incomeMinor, state.currency), MaterialTheme.money.positive, Modifier.weight(1f))
            HeroChip(Icons.Rounded.ArrowUpward, "Projected", Money.formatCompact(state.projectedMinor, state.currency), on, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun QuickActions(onAdd: () -> Unit, onGroceries: () -> Unit, onBills: () -> Unit, onActivity: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(Icons.Rounded.Add, "Expense", onAdd, Modifier.weight(1f))
        QuickAction(Icons.Rounded.ShoppingCart, "Groceries", onGroceries, Modifier.weight(1f))
        QuickAction(Icons.Rounded.ReceiptLong, "Bills", onBills, Modifier.weight(1f))
        QuickAction(Icons.Rounded.Wallet, "Activity", onActivity, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
        }
    }
}

@Composable
private fun WhereItWentCard(state: HomeState) {
    MidasCard {
        Text("Where it went", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val slices = state.shares.map { (s, c) -> DonutSlice(s.totalMinor.toFloat(), Color(c?.color ?: 0xFF546E7A), c?.name ?: "Other") }
            DonutChart(slices, size = 120.dp, thickness = 16.dp) {
                Text(state.shares.first().first.fraction.asPercent(), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                state.shares.forEach { (s, c) ->
                    LegendRow(Color(c?.color ?: 0xFF546E7A), c?.name ?: "Uncategorised", Money.formatCompact(s.totalMinor, state.currency))
                }
            }
        }
    }
}

@Composable
private fun WeekCard(state: HomeState) {
    MidasCard {
        Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        BarChart(
            values = state.last7Days.map { it.second.toFloat() },
            labels = state.last7Days.map { it.first.dayOfWeek.name.take(1) },
            detailLabels = state.last7Days.map { Dates.dayHeader(it.first, state.today) },
            height = 110.dp,
            reference = state.dailyAllowanceMinor?.toFloat(),
            valueLabel = { Money.format(it.toLong(), state.currency) },
        )
    }
}

@Composable
private fun BillsCard(state: HomeState, onBills: () -> Unit) {
    MidasCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onBills), verticalAlignment = Alignment.CenterVertically) {
            Text("Upcoming bills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null)
        }
        state.upcomingBills.forEach { b ->
            val overdue = b.nextDue.isBefore(state.today)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val cat = b.categoryId?.let(state.categories::get)
                CategoryBadge(b.icon, cat?.color, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (overdue) "Overdue · ${Dates.relative(b.nextDue, state.today)}" else Dates.relative(b.nextDue, state.today),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(b.amountMinor, state.currency), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun GroceryCard(state: HomeState, onGroceries: () -> Unit) {
    MidasCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onGroceries), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Groceries this week", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.grocery.checkedCount} of ${state.grocery.itemCount} in cart · est. ${Money.format(state.grocery.estimatedMinor, state.currency)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.grocery.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}
