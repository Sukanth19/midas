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
import dev.suks.midas.data.Person
import androidx.compose.material3.Switch
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.PaymentParser
import dev.suks.midas.domain.TxSource
import dev.suks.midas.domain.TxType
import java.time.Instant
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
    /** Set when reviewing a captured payment: where it was read from. */
    val fromCapture: String? = null,
    /** Opened from text shared by another app. */
    val fromShare: Boolean = false,
    /** People this workspace is shared with; empty for a personal one. */
    val people: List<Person> = emptyList(),
    /** Who paid; null = you. */
    val paidById: Long? = null,
    /** Split evenly between you and everyone in [people]. */
    val shared: Boolean = false,
) {
    val canSplit: Boolean get() = people.isNotEmpty() && type == TxType.EXPENSE

    /** Each person's share when split, or null. */
    val shareMinor: Long? get() = amountMinor?.takeIf { canSplit && shared }?.let { it / (people.size + 1) }

    val amountMinor: Long? get() = Money.parse(amountText, currency)?.takeIf { it > 0 }
    val visibleCategories: List<Category> get() = categories.filter { it.type == type }
    val canSave: Boolean get() = amountMinor != null && categoryId != null
}

class EditTransactionViewModel(
    private val c: AppContainer, private val id: Long, startAsIncome: Boolean,
    /** A captured payment being reviewed; saving marks it added. */
    private val captureId: Long = 0,
    private val sharedText: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(EditTxState(type = if (startAsIncome) TxType.INCOME else TxType.EXPENSE))
    val state = _state.asStateFlow()
    private var original: Transaction? = null

    init {
        viewModelScope.launch {
            val settings = c.settings.current()
            val cats = c.categories.categories.first()
            val people = c.split.getPeople()
            val tx = if (id != 0L) c.transactions.get(id) else null
            val capture = if (tx == null && captureId != 0L) c.captures.get(captureId) else null
            val guessedCategory = capture?.merchant?.let { c.transactions.lastCategoryFor(it, capture.type) }
            original = tx
            _state.update { s ->
                val base = s.copy(
                    loading = false, currency = settings.currency, today = c.today(), date = c.today(), categories = cats,
                    people = people, shared = people.isNotEmpty(),
                )
                when {
                    tx != null -> base.copy(
                        isNew = false, type = tx.type, amountText = Money.toInput(tx.amountMinor, settings.currency), title = tx.title,
                        note = tx.note, categoryId = tx.categoryId, date = tx.date, paymentMethod = tx.paymentMethod,
                        shared = tx.shared, paidById = tx.paidById,
                    )
                    capture != null -> base.copy(
                        type = capture.type, amountText = Money.toInput(capture.amountMinor, settings.currency),
                        title = capture.merchant.orEmpty(), categoryId = guessedCategory, paymentMethod = capture.method,
                        date = Instant.ofEpochMilli(capture.receivedAt).atZone(c.clock.zone).toLocalDate(),
                        fromCapture = capture.source,
                    )
                    sharedText.isNotBlank() -> prefillFromShare(base, sharedText)
                    else -> base
                }
            }
        }
    }

    private suspend fun prefillFromShare(base: EditTxState, text: String): EditTxState {
        val p = PaymentParser.parse(text, base.currency, lenient = true)
            ?: return base.copy(note = text.trim().take(MAX_NOTE), fromShare = true)
        return base.copy(
            type = p.type, amountText = Money.toInput(p.amountMinor, base.currency), title = p.merchant.orEmpty(),
            paymentMethod = p.method, categoryId = p.merchant?.let { c.transactions.lastCategoryFor(it, p.type) },
            // Keep the original text when no payee was found, so nothing is lost.
            note = if (p.merchant == null) text.trim().take(MAX_NOTE) else "",
            fromShare = true,
        )
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
    fun setPaidBy(id: Long?) = _state.update { it.copy(paidById = id) }
    fun setShared(on: Boolean) = _state.update { it.copy(shared = on) }

    fun save() {
        val s = _state.value
        val amount = s.amountMinor
        if (amount == null || s.categoryId == null) { _state.update { it.copy(showErrors = true) }; return }
        val catName = s.categories.firstOrNull { it.id == s.categoryId }?.name ?: "Expense"
        viewModelScope.launch {
            val base = original ?: Transaction(
                amountMinor = amount, type = s.type, categoryId = s.categoryId, title = "", date = s.date,
                source = if (captureId != 0L) TxSource.CAPTURED else TxSource.MANUAL,
            )
            val txId = c.transactions.save(
                base.copy(
                    amountMinor = amount, type = s.type, categoryId = s.categoryId, title = s.title.trim().ifBlank { catName },
                    note = s.note.trim(), date = s.date, paymentMethod = s.paymentMethod,
                    shared = s.canSplit && s.shared, paidById = s.paidById.takeIf { s.canSplit },
                ),
            )
            if (captureId != 0L && original == null) c.captures.markAdded(captureId, txId)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val tx = original ?: return
        viewModelScope.launch { c.transactions.delete(tx.id); _state.update { it.copy(done = true) } }
    }
}

@Composable
fun EditTransactionScreen(id: Long, startAsIncome: Boolean, onDone: () -> Unit, captureId: Long = 0, sharedText: String = "") {
    val vm = midasViewModel(key = "tx-$id-$captureId-${sharedText.hashCode()}") { EditTransactionViewModel(it, id, startAsIncome, captureId, sharedText) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    EditTransactionContent(
        state, onClose = onDone, onType = vm::setType, onAmount = vm::setAmount, onTitle = vm::setTitle, onNote = vm::setNote,
        onCategory = vm::setCategory, onDate = vm::setDate, onPayment = vm::setPayment, onSave = vm::save, onDelete = vm::delete,
        onPaidBy = vm::setPaidBy, onShared = vm::setShared,
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
    onPaidBy: (Long?) -> Unit = {},
    onShared: (Boolean) -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading && state.isNew) runCatching { amountFocus.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                title = {
                    Text(
                        when {
                            state.fromCapture != null -> "Review payment"
                            !state.isNew -> "Edit transaction"
                            state.type == TxType.INCOME -> "New income"
                            else -> "New expense"
                        },
                    )
                },
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
            val readFrom = state.fromCapture ?: "shared text".takeIf { state.fromShare }
            readFrom?.let { source ->
                Text(
                    "Read from $source. Check the details, pick a category and save.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("read_from"),
                )
            }
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

            if (state.canSplit) {
                Text("Paid by", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.paidById == null, { onPaidBy(null) }, { Text("You") })
                    state.people.forEach { p -> FilterChip(state.paidById == p.id, { onPaidBy(p.id) }, { Text(p.name) }, Modifier.testTag("paid_by_${p.name}")) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Split evenly with everyone", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            state.shareMinor?.let { "${Money.format(it, state.currency)} each for ${state.people.size + 1} people" } ?: "Only counts for whoever paid",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(state.shared, onShared, modifier = Modifier.testTag("split_switch"))
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

private const val MAX_NOTE = 200
