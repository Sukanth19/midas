package dev.suks.midas.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Category
import dev.suks.midas.domain.BudgetProgress
import dev.suks.midas.domain.BudgetStatus
import dev.suks.midas.domain.Budgets
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.Stats
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.components.AmountField
import dev.suks.midas.ui.components.BudgetBar
import dev.suks.midas.ui.components.CategoryBadge
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.components.SectionHeader
import dev.suks.midas.ui.components.budgetColor
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CategoryBudget(val category: Category, val progress: BudgetProgress)

data class BudgetsState(
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val overall: BudgetProgress = BudgetProgress(0, 0),
    val dailyAllowanceMinor: Long? = null,
    val projectedMinor: Long = 0,
    val withBudget: List<CategoryBudget> = emptyList(),
    val withoutBudget: List<CategoryBudget> = emptyList(),
) {
    val categoryBudgetTotalMinor: Long get() = withBudget.sumOf { it.progress.limitMinor }
}

class BudgetsViewModel(private val c: AppContainer) : ViewModel() {
    val state = stateOf(
        combine(c.settings.settings, c.categories.categories, c.transactions.all) { s, cats, txs ->
            val today = c.today()
            val month = YearMonth.from(today)
            val range = Dates.month(month)
            val shares = Stats.byCategory(txs.map { it.toPoint() }, range).associateBy { it.categoryId }
            val spent = Stats.summary(txs.map { it.toPoint() }, range, today).expenseMinor
            val overall = BudgetProgress(s.monthlyBudgetMinor, spent)
            val rows = cats.filter { it.type == TxType.EXPENSE }.map { CategoryBudget(it, BudgetProgress(it.monthlyBudgetMinor, shares[it.id]?.totalMinor ?: 0)) }
            BudgetsState(
                currency = s.currency, today = today, overall = overall,
                dailyAllowanceMinor = Budgets.dailyAllowance(overall, month, today),
                projectedMinor = Budgets.projectedSpend(spent, month, today),
                withBudget = rows.filter { it.progress.limitMinor > 0 }.sortedByDescending { it.progress.fraction },
                withoutBudget = rows.filter { it.progress.limitMinor <= 0 }.sortedByDescending { it.progress.spentMinor },
            )
        },
        BudgetsState(),
    )

    fun setOverall(minor: Long) = viewModelScope.launch { c.settings.setMonthlyBudget(minor) }
    fun setCategory(id: Long, minor: Long) = viewModelScope.launch { c.categories.setBudget(id, minor) }
}

@Composable
fun BudgetsScreen(onBack: () -> Unit) {
    val vm = midasViewModel { BudgetsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    BudgetsContent(state, onBack, vm::setOverall, vm::setCategory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsContent(state: BudgetsState, onBack: () -> Unit = {}, onSetOverall: (Long) -> Unit = {}, onSetCategory: (Long, Long) -> Unit = { _, _ -> }) {
    var editOverall by remember { mutableStateOf(false) }
    var editCat by remember { mutableStateOf<Category?>(null) }
    val cur = state.currency
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MidasCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    val on = MaterialTheme.colorScheme.onPrimaryContainer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Monthly budget · ${Dates.monthTitle(YearMonth.from(state.today))}", style = MaterialTheme.typography.labelLarge, color = on)
                            Text(
                                if (state.overall.limitMinor > 0) Money.format(state.overall.limitMinor, cur) else "Not set",
                                style = MaterialTheme.typography.headlineMedium, color = on,
                            )
                        }
                        TextButton(onClick = { editOverall = true }) { Text(if (state.overall.limitMinor > 0) "Edit" else "Set") }
                    }
                    if (state.overall.limitMinor > 0) {
                        Spacer(Modifier.height(12.dp))
                        BudgetBar(state.overall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${Money.format(state.overall.spentMinor, cur)} spent · " +
                                if (state.overall.remainingMinor >= 0) "${Money.format(state.overall.remainingMinor, cur)} left" else "${Money.format(-state.overall.remainingMinor, cur)} over",
                            style = MaterialTheme.typography.bodyMedium, color = on,
                        )
                        val allowance = state.dailyAllowanceMinor
                        Text(
                            buildString {
                                if (allowance != null) append("${Money.format(allowance, cur)} a day for the rest of the month · ")
                                append("on pace for ${Money.format(state.projectedMinor, cur)}")
                            },
                            style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            if (state.withBudget.isNotEmpty()) {
                item {
                    SectionHeader("Category budgets") {
                        Text("${Money.formatCompact(state.categoryBudgetTotalMinor, cur)} planned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.withBudget, key = { it.category.id }) { row -> BudgetRow(row, cur) { editCat = row.category } }
            }
            item { SectionHeader("No budget yet") }
            items(state.withoutBudget, key = { it.category.id }) { row -> BudgetRow(row, cur) { editCat = row.category } }
        }
    }

    if (editOverall) {
        BudgetDialog("Monthly budget", "Total you want to spend each month.", state.overall.limitMinor, cur, onDismiss = { editOverall = false }) { onSetOverall(it); editOverall = false }
    }
    editCat?.let { cat ->
        BudgetDialog("${cat.name} budget", "Leave empty to remove the budget.", cat.monthlyBudgetMinor, cur, onDismiss = { editCat = null }) { onSetCategory(cat.id, it); editCat = null }
    }
}

@Composable
private fun BudgetRow(row: CategoryBudget, cur: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(row.category.icon, row.category.color, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val p = row.progress
                    Text(
                        when (p.status) {
                            BudgetStatus.NONE -> "${Money.format(p.spentMinor, cur)} spent · tap to set a budget"
                            BudgetStatus.OVER -> "${Money.format(-p.remainingMinor, cur)} over"
                            else -> "${Money.format(p.remainingMinor, cur)} left"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.status == BudgetStatus.NONE) MaterialTheme.colorScheme.onSurfaceVariant else budgetColor(p.status),
                    )
                }
                if (row.progress.limitMinor > 0) {
                    Text("${Money.formatCompact(row.progress.spentMinor, cur)} / ${Money.formatCompact(row.progress.limitMinor, cur)}", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (row.progress.limitMinor > 0) {
                Spacer(Modifier.height(10.dp))
                BudgetBar(row.progress, height = 8.dp)
            }
        }
    }
}

@Composable
private fun BudgetDialog(title: String, hint: String, current: Long, cur: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(Money.toInput(current, cur)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                AmountField(text, { text = it }, cur, "Per month")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Money.parse(text, cur) ?: 0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
