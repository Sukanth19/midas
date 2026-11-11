package dev.suks.midas.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Category
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import dev.suks.midas.ui.theme.money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class TxFilter(val query: String = "", val type: TxType? = null, val categoryId: Long? = null, val month: YearMonth = YearMonth.now())

data class DayGroup(val date: LocalDate, val items: List<Transaction>, val expenseMinor: Long, val incomeMinor: Long)

data class TransactionsState(
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val filter: TxFilter = TxFilter(),
    val groups: List<DayGroup> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val usedCategories: List<Category> = emptyList(),
    val totalExpenseMinor: Long = 0,
    val totalIncomeMinor: Long = 0,
    val loaded: Boolean = false,
)

class TransactionsViewModel(private val c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(TxFilter(month = YearMonth.from(c.today())))

    val state = stateOf(
        combine(filter, c.transactions.all, c.categories.categories, c.settings.settings) { f, txs, cats, s ->
            build(f, txs, cats, s.currency, c.today())
        },
        TransactionsState(),
    )

    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setType(t: TxType?) = filter.update { it.copy(type = t) }
    fun setCategory(id: Long?) = filter.update { it.copy(categoryId = id) }
    fun shiftMonth(by: Long) = filter.update { it.copy(month = it.month.plusMonths(by)) }

    private var lastDeleted: Transaction? = null
    fun delete(tx: Transaction) = viewModelScope.launch { lastDeleted = tx; c.transactions.delete(tx.id) }
    fun undoDelete() = viewModelScope.launch { lastDeleted?.let { c.transactions.save(it.copy(id = 0)) }; lastDeleted = null }

    companion object {
        fun build(f: TxFilter, txs: List<Transaction>, cats: List<Category>, currency: String, today: LocalDate): TransactionsState {
            val byId = cats.associateBy { it.id }
            val range = Dates.month(f.month)
            val q = f.query.trim().lowercase()
            val inMonth = txs.filter { it.date in range }
            val filtered = inMonth.filter { tx ->
                (f.type == null || tx.type == f.type) &&
                    (f.categoryId == null || tx.categoryId == f.categoryId) &&
                    (q.isEmpty() || tx.title.lowercase().contains(q) || tx.note.lowercase().contains(q) ||
                        (tx.categoryId?.let(byId::get)?.name?.lowercase()?.contains(q) == true))
            }
            val groups = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { (d, list) ->
                DayGroup(d, list, list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }, list.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor })
            }
            val used = inMonth.mapNotNull { it.categoryId }.distinct().mapNotNull(byId::get).sortedBy { it.name }
            return TransactionsState(
                currency = currency, today = today, filter = f, groups = groups, categories = byId, usedCategories = used,
                totalExpenseMinor = filtered.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
                totalIncomeMinor = filtered.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
                loaded = true,
            )
        }
    }
}

@Composable
fun TransactionsScreen(onAdd: (income: Boolean) -> Unit, onOpen: (Long) -> Unit) {
    val vm = midasViewModel { TransactionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    TransactionsContent(state, vm::setQuery, vm::setType, vm::setCategory, vm::shiftMonth, onAdd, onOpen, vm::delete, vm::undoDelete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsContent(
    state: TransactionsState,
    onQuery: (String) -> Unit = {},
    onType: (TxType?) -> Unit = {},
    onCategory: (Long?) -> Unit = {},
    onShiftMonth: (Long) -> Unit = {},
    onAdd: (Boolean) -> Unit = {},
    onOpen: (Long) -> Unit = {},
    onDelete: (Transaction) -> Unit = {},
    onUndo: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                actions = {
                    IconButton(onClick = { onShiftMonth(-1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous month") }
                    Text(Dates.monthTitle(state.filter.month), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { onShiftMonth(1) }, enabled = state.filter.month < YearMonth.from(state.today)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next month")
                    }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { onAdd(false) }) { Icon(Icons.Rounded.Add, "Add transaction") } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = onQuery,
                    placeholder = { Text("Search title, note or category") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (state.filter.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search"),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(state.filter.type == null, { onType(null) }, { Text("All") }) }
                    item { FilterChip(state.filter.type == TxType.EXPENSE, { onType(TxType.EXPENSE) }, { Text("Expenses") }) }
                    item { FilterChip(state.filter.type == TxType.INCOME, { onType(TxType.INCOME) }, { Text("Income") }) }
                    items(state.usedCategories, key = { it.id }) { cat ->
                        FilterChip(state.filter.categoryId == cat.id, { onCategory(if (state.filter.categoryId == cat.id) null else cat.id) }, { Text(cat.name) })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryPill("Spent", Money.format(state.totalExpenseMinor, state.currency), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    SummaryPill("Earned", Money.format(state.totalIncomeMinor, state.currency), MaterialTheme.money.positive, Modifier.weight(1f))
                }
            }
            if (state.loaded && state.groups.isEmpty()) {
                item {
                    val filtering = state.filter.query.isNotBlank() || state.filter.type != null || state.filter.categoryId != null
                    if (filtering) EmptyState(Icons.Rounded.SearchOff, "No matches", "Try a different search or clear the filters.")
                    else EmptyState(Icons.Rounded.Wallet, "No transactions", "Nothing recorded in ${Dates.monthTitle(state.filter.month)} yet.")
                }
            }
            state.groups.forEach { g ->
                item(key = "h${g.date}") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                        Text(Dates.dayHeader(g.date, state.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (g.expenseMinor > 0) Text("−" + Money.format(g.expenseMinor, state.currency), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(g.items, key = { it.id }) { tx ->
                    val dismiss = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        onDismiss = {
                            onDelete(tx)
                            scope.launch {
                                val r = snackbar.showSnackbar("Deleted “${tx.title}”", actionLabel = "Undo", withDismissAction = true)
                                if (r == SnackbarResult.ActionPerformed) onUndo()
                            }
                        },
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
                        },
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            TransactionRow(tx, tx.categoryId?.let(state.categories::get), state.currency, onClick = { onOpen(tx.id) }, subtitle = tx.note.takeIf { it.isNotBlank() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        }
    }
}
