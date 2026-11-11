package dev.suks.midas.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Category
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.components.CategoryIcons
import dev.suks.midas.ui.components.ConfirmDialog
import dev.suks.midas.ui.components.DateField
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.theme.HeroAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditTxState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val type: TxType = TxType.EXPENSE,
    val amountText: String = "",
    val title: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val categories: List<Category> = emptyList(),
    val showErrors: Boolean = false,
    val done: Boolean = false,
) {
    val amountMinor: Long? get() = Money.parse(amountText, currency)?.takeIf { it > 0 }
    val visibleCategories: List<Category> get() = categories.filter { it.type == type }
    val canSave: Boolean get() = amountMinor != null && categoryId != null
}

class EditTransactionViewModel(private val c: AppContainer, private val id: Long, startAsIncome: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(EditTxState(type = if (startAsIncome) TxType.INCOME else TxType.EXPENSE))
    val state = _state.asStateFlow()
    private var original: Transaction? = null

    init {
        viewModelScope.launch {
            val settings = c.settings.current()
            val cats = c.categories.categories.first()
            val tx = if (id != 0L) c.transactions.get(id) else null
            original = tx
            _state.update { s ->
                val base = s.copy(loading = false, currency = settings.currency, today = c.today(), date = c.today(), categories = cats)
                if (tx == null) base
                else base.copy(
                    isNew = false, type = tx.type, amountText = Money.toInput(tx.amountMinor, settings.currency), title = tx.title,
                    note = tx.note, categoryId = tx.categoryId, date = tx.date, paymentMethod = tx.paymentMethod,
                )
            }
        }
    }

    fun setType(t: TxType) = _state.update { s ->
        s.copy(type = t, categoryId = s.categoryId?.takeIf { id -> s.categories.any { it.id == id && it.type == t } })
    }
    fun setAmount(v: String) = _state.update { it.copy(amountText = v) }
    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun setPayment(p: PaymentMethod) = _state.update { it.copy(paymentMethod = p) }

    fun save() {
        val s = _state.value
        val amount = s.amountMinor
        if (amount == null || s.categoryId == null) { _state.update { it.copy(showErrors = true) }; return }
        val catName = s.categories.firstOrNull { it.id == s.categoryId }?.name ?: "Expense"
        viewModelScope.launch {
            val base = original ?: Transaction(amountMinor = amount, type = s.type, categoryId = s.categoryId, title = "", date = s.date)
            c.transactions.save(
                base.copy(
                    amountMinor = amount, type = s.type, categoryId = s.categoryId, title = s.title.trim().ifBlank { catName },
                    note = s.note.trim(), date = s.date, paymentMethod = s.paymentMethod,
                ),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val tx = original ?: return
        viewModelScope.launch { c.transactions.delete(tx.id); _state.update { it.copy(done = true) } }
    }
}

@Composable
fun EditTransactionScreen(id: Long, startAsIncome: Boolean, onDone: () -> Unit) {
    val vm = midasViewModel(key = "tx-$id") { EditTransactionViewModel(it, id, startAsIncome) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    EditTransactionContent(
        state, onClose = onDone, onType = vm::setType, onAmount = vm::setAmount, onTitle = vm::setTitle, onNote = vm::setNote,
        onCategory = vm::setCategory, onDate = vm::setDate, onPayment = vm::setPayment, onSave = vm::save, onDelete = vm::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionContent(
    state: EditTxState,
    onClose: () -> Unit = {},
    onType: (TxType) -> Unit = {},
    onAmount: (String) -> Unit = {},
    onTitle: (String) -> Unit = {},
    onNote: (String) -> Unit = {},
    onCategory: (Long) -> Unit = {},
    onDate: (LocalDate) -> Unit = {},
    onPayment: (PaymentMethod) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading && state.isNew) runCatching { amountFocus.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                title = { Text(if (state.isNew) (if (state.type == TxType.INCOME) "New income" else "New expense") else "Edit transaction") },
                actions = {
                    if (!state.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding().height(56.dp).testTag("save"),
            ) { Text(if (state.isNew) "Save" else "Save changes", style = MaterialTheme.typography.titleMedium) }
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = state.type == t, onClick = { onType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, TxType.entries.size),
                    ) { Text(if (t == TxType.EXPENSE) "Expense" else "Income") }
                }
            }

            // Big centred amount: the one thing you must enter.
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Money.symbol(state.currency), style = HeroAmount, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                    BasicTextField(
                        value = state.amountText,
                        onValueChange = { v ->
                            val cleaned = v.filter { it.isDigit() || it == '.' }
                            if (cleaned.isEmpty() || cleaned == "." || Money.parse(cleaned, state.currency) != null) onAmount(cleaned)
                        },
                        textStyle = HeroAmount.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.focusRequester(amountFocus).testTag("amount"),
                        decorationBox = { inner ->
                            if (state.amountText.isEmpty()) Text("0", style = HeroAmount, color = MaterialTheme.colorScheme.outlineVariant)
                            inner()
                        },
                    )
                }
                if (state.showErrors && state.amountMinor == null) {
                    Text("Enter an amount", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.title, onValueChange = onTitle, label = { Text("What was it for?") },
                placeholder = { Text("e.g. Dinner with friends") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("title"),
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            if (state.showErrors && state.categoryId == null) {
                Text("Pick a category", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.visibleCategories.forEach { cat ->
                    val selected = state.categoryId == cat.id
                    FilterChip(
                        selected = selected,
                        onClick = { onCategory(cat.id) },
                        label = { Text(cat.name) },
                        leadingIcon = { Icon(CategoryIcons.get(cat.icon), null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color(cat.color)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }

            DateField("Date", state.date, { it?.let(onDate) }, today = state.today)

            Text("Paid with", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { p ->
                    FilterChip(state.paymentMethod == p, { onPayment(p) }, { Text(p.label) })
                }
            }

            OutlinedTextField(
                value = state.note, onValueChange = onNote, label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (!state.isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete transaction") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete transaction?", "This can't be undone.", "Delete", onDelete) { confirmDelete = false }
    }
}
