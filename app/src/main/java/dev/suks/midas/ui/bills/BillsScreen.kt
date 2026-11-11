package dev.suks.midas.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Bill
import dev.suks.midas.data.Category
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.Frequency
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.Recurrence
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.components.AmountField
import dev.suks.midas.ui.components.CategoryBadge
import dev.suks.midas.ui.components.CategoryIcons
import dev.suks.midas.ui.components.ConfirmDialog
import dev.suks.midas.ui.components.DateField
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.components.SectionHeader
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import dev.suks.midas.ui.theme.money
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class BillsState(
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val overdue: List<Bill> = emptyList(),
    val soon: List<Bill> = emptyList(),
    val later: List<Bill> = emptyList(),
    val paused: List<Bill> = emptyList(),
    val categories: List<Category> = emptyList(),
    val dueThisMonthMinor: Long = 0,
    val monthlyFixedMinor: Long = 0,
    val loaded: Boolean = false,
) {
    val isEmpty get() = overdue.isEmpty() && soon.isEmpty() && later.isEmpty() && paused.isEmpty()
}

class BillsViewModel(private val c: AppContainer) : ViewModel() {
    val state = stateOf(
        combine(c.bills.bills, c.categories.categories, c.settings.settings) { bills, cats, s -> build(bills, cats, s.currency, c.today()) },
        BillsState(),
    )

    fun save(b: Bill) = viewModelScope.launch { c.bills.save(b) }
    fun delete(id: Long) = viewModelScope.launch { c.bills.delete(id) }
    fun pay(id: Long, amount: Long) = viewModelScope.launch { c.bills.markPaid(id, c.today(), amount) }
    fun skip(id: Long) = viewModelScope.launch { c.bills.skip(id) }

    companion object {
        fun build(bills: List<Bill>, cats: List<Category>, currency: String, today: LocalDate): BillsState {
            val active = bills.filter { it.active }
            val month = Dates.month(YearMonth.from(today))
            return BillsState(
                currency = currency, today = today,
                overdue = active.filter { it.nextDue.isBefore(today) },
                soon = active.filter { !it.nextDue.isBefore(today) && Dates.daysUntil(today, it.nextDue) <= 7 },
                later = active.filter { Dates.daysUntil(today, it.nextDue) > 7 },
                paused = bills.filter { !it.active },
                categories = cats.filter { it.type == TxType.EXPENSE },
                // Unpaid occurrences still to come this month, plus anything overdue.
                dueThisMonthMinor = active.sumOf { b ->
                    val from = if (b.nextDue.isBefore(month.start)) b.nextDue else month.start
                    Recurrence.occurrences(b.nextDue, b.frequency, b.interval, b.anchorDay, dev.suks.midas.domain.DateRange(from, month.endInclusive)).size * b.amountMinor
                },
                monthlyFixedMinor = active.sumOf { Recurrence.monthlyEquivalent(it.amountMinor, it.frequency, it.interval) },
                loaded = true,
            )
        }
    }
}

@Composable
fun BillsScreen() {
    val vm = midasViewModel { BillsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    BillsContent(state, vm::save, vm::delete, vm::pay, vm::skip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsContent(
    state: BillsState,
    onSave: (Bill) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onPay: (Long, Long) -> Unit = { _, _ -> },
    onSkip: (Long) -> Unit = {},
) {
    var editing by remember { mutableStateOf<Bill?>(null) }
    var paying by remember { mutableStateOf<Bill?>(null) }
    val catById = remember(state.categories) { state.categories.associateBy { it.id } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bills & rent") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Bill(name = "", amountMinor = 0, nextDue = state.today, categoryId = state.categories.firstOrNull { it.name == "Rent" }?.id)
            }, modifier = Modifier.testTag("add_bill")) { Icon(Icons.Rounded.Add, "Add bill") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MidasCard(Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Still due this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(Money.format(state.dueThisMonthMinor, state.currency), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                    }
                    MidasCard(Modifier.weight(1f)) {
                        Text("Fixed costs / month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(Money.roundToMajor(state.monthlyFixedMinor, state.currency), state.currency), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    }
                }
            }
            if (state.loaded && state.isEmpty) {
                item { EmptyState(Icons.Rounded.ReceiptLong, "No bills yet", "Add rent, electricity, phone and subscriptions. Midas reminds you before they're due.") }
            }
            fun section(title: String, list: List<Bill>) {
                if (list.isEmpty()) return
                item(key = "h-$title") { SectionHeader(title) }
                items(list, key = { it.id }) { b -> BillRow(b, catById[b.categoryId], state, onPay = { paying = b }, onClick = { editing = b }) }
            }
            section("Overdue", state.overdue)
            section("Due in the next 7 days", state.soon)
            section("Later", state.later)
            section("Paused", state.paused)
        }
    }

    editing?.let { b ->
        BillSheet(b, state, onDismiss = { editing = null }, onSave = { onSave(it); editing = null }, onDelete = { onDelete(it); editing = null }, onSkip = { onSkip(it); editing = null })
    }
    paying?.let { b ->
        var amount by remember(b.id) { mutableStateOf(Money.toInput(b.amountMinor, state.currency)) }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("Pay ${b.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Records the payment for today and moves the bill to ${Dates.shortDate(Recurrence.next(b.nextDue, b.frequency, b.interval, b.anchorDay), state.today)}.")
                    AmountField(amount, { amount = it }, state.currency, "Amount paid")
                }
            },
            confirmButton = {
                TextButton(enabled = (Money.parse(amount, state.currency) ?: 0) > 0, onClick = { onPay(b.id, Money.parse(amount, state.currency)!!); paying = null }) { Text("Mark paid") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BillRow(b: Bill, category: Category?, state: BillsState, onPay: () -> Unit, onClick: () -> Unit) {
    val overdue = b.active && b.nextDue.isBefore(state.today)
    val dueToday = b.nextDue == state.today
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(b.icon, category?.color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val every = if (b.interval == 1) b.frequency.label else "Every ${b.interval} " + when (b.frequency) {
                    Frequency.WEEKLY -> "weeks"
                    Frequency.MONTHLY -> "months"
                    Frequency.YEARLY -> "years"
                }
                Text(
                    "$every · ${if (overdue) "overdue since " + Dates.shortDate(b.nextDue, state.today) else "due " + Dates.relativeInline(b.nextDue, state.today)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        overdue -> MaterialTheme.colorScheme.error
                        dueToday -> MaterialTheme.money.warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(b.amountMinor, state.currency), style = MaterialTheme.typography.titleSmall)
                if (b.active) FilledTonalButton(onClick = onPay, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) { Text("Pay") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BillSheet(b: Bill, state: BillsState, onDismiss: () -> Unit, onSave: (Bill) -> Unit, onDelete: (Long) -> Unit, onSkip: (Long) -> Unit) {
    var name by remember { mutableStateOf(b.name) }
    var amount by remember { mutableStateOf(Money.toInput(b.amountMinor, state.currency)) }
    var categoryId by remember { mutableStateOf(b.categoryId) }
    var frequency by remember { mutableStateOf(b.frequency) }
    var interval by remember { mutableStateOf(b.interval) }
    var nextDue by remember { mutableStateOf(b.nextDue) }
    var remind by remember { mutableStateOf(b.remindDaysBefore) }
    var icon by remember { mutableStateOf(b.icon) }
    var method by remember { mutableStateOf(b.paymentMethod) }
    var active by remember { mutableStateOf(b.active) }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountMinor = Money.parse(amount, state.currency)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (b.id == 0L) "New bill" else "Edit bill", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                name, { name = it }, label = { Text("Name") }, placeholder = { Text("e.g. Rent, Electricity, Netflix") },
                singleLine = true, isError = tried && name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth().testTag("bill_name"),
            )
            AmountField(amount, { amount = it }, state.currency, "Amount", isError = tried && (amountMinor ?: 0) <= 0)
            Text("Repeats", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Frequency.entries.forEachIndexed { i, f ->
                    SegmentedButton(frequency == f, { frequency = f }, SegmentedButtonDefaults.itemShape(i, Frequency.entries.size)) { Text(f.label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", Modifier.weight(1f))
                listOf(1, 2, 3, 6).forEach { n -> FilterChip(interval == n, { interval = n }, { Text("$n") }, Modifier.padding(start = 6.dp)) }
            }
            DateField("Next due", nextDue, { it?.let { d -> nextDue = d } }, today = state.today)
            Text("Remind me", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "On the day", 1 to "1 day before", 2 to "2 days before", 3 to "3 days before", 7 to "A week before").forEach { (d, l) ->
                    FilterChip(remind == d, { remind = d }, { Text(l) })
                }
            }
            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c -> FilterChip(categoryId == c.id, { categoryId = c.id }, { Text(c.name) }) }
            }
            Text("Icon", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("home", "bolt", "water", "wifi", "phone", "subscriptions", "tv", "music", "car", "health", "school", "card", "receipt").forEach { k ->
                    FilterChip(icon == k, { icon = k }, { Icon(CategoryIcons.get(k), k, Modifier.size(18.dp)) })
                }
            }
            Text("Paid with", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { p -> FilterChip(method == p, { method = p }, { Text(p.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(active, { active = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (b.id != 0L) {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
                    OutlinedButton(onClick = { onSkip(b.id) }, modifier = Modifier.weight(1f)) { Text("Skip once") }
                }
                Button(
                    onClick = {
                        tried = true
                        if (name.isNotBlank() && (amountMinor ?: 0) > 0) {
                            onSave(
                                b.copy(
                                    name = name.trim(), amountMinor = amountMinor!!, categoryId = categoryId, frequency = frequency, interval = interval,
                                    nextDue = nextDue, anchorDay = if (nextDue != b.nextDue || b.id == 0L) nextDue.dayOfMonth else b.anchorDay,
                                    remindDaysBefore = remind, icon = icon, paymentMethod = method, active = active,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("bill_save"),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${b.name}?", "Past payments stay in your activity.", "Delete", { onDelete(b.id) }) { confirmDelete = false }
}
