package dev.suks.midas.ui.split

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Person
import dev.suks.midas.data.Settings
import dev.suks.midas.data.Settlement
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.MemberId
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.Payment
import dev.suks.midas.domain.SharedExpense
import dev.suks.midas.domain.Split
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.components.SectionHeader
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import dev.suks.midas.ui.theme.money
import dev.suks.midas.ui.workspaces.rememberWorkspaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SplitState(
    val loaded: Boolean = false,
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val myName: String = "Me",
    val people: List<Person> = emptyList(),
    /** Everyone's balance, you first: positive = owed money. */
    val balances: Map<MemberId, Long> = emptyMap(),
    /** The fewest payments that settle everything. */
    val payments: List<Payment> = emptyList(),
    val sharedExpenses: List<SharedExpense> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
) {
    val members: List<MemberId> get() = listOf<MemberId>(null) + people.map { it.id }
    val names: Map<MemberId, String> get() = mapOf<MemberId, String>(null to myName) + people.associate { it.id to it.name }
    val yourBalance: Long get() = balances[null] ?: 0
    val totalSharedMinor: Long get() = sharedExpenses.sumOf { it.amountMinor }

    fun name(m: MemberId) = names[m] ?: "Someone"

    fun summary(title: String) = Split.summary(title, members, names, sharedExpenses, payments, currency)
}

class SplitViewModel(private val c: AppContainer) : ViewModel() {
    val state = stateOf(
        combine(c.split.people, c.split.sharedExpenses, c.split.settlements, c.settings.settings) { people, shared, settlements, s ->
            build(people, shared, settlements, s, c.today())
        },
        SplitState(),
    )
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun addPerson(name: String) = viewModelScope.launch { c.split.addPerson(name) }
    fun removePerson(p: Person) = viewModelScope.launch {
        if (!c.split.removePerson(p.id)) _message.value = "${p.name} is in some shared expenses or settle-ups, so they can't be removed"
    }
    fun settle(p: Payment) = viewModelScope.launch { c.split.settle(p.from, p.to, p.amountMinor, c.today()) }
    fun deleteSettlement(id: Long) = viewModelScope.launch { c.split.deleteSettlement(id) }
    fun setMyName(name: String) = viewModelScope.launch { c.settings.setMyName(name) }
    fun messageShown() { _message.value = null }

    companion object {
        fun build(people: List<Person>, shared: List<Transaction>, settlements: List<Settlement>, s: Settings, today: LocalDate): SplitState {
            val members = listOf<MemberId>(null) + people.map { it.id }
            val known = members.toSet()
            // Someone removed from the list is treated as you, so their share isn't lost.
            val expenses = shared.map { SharedExpense(it.amountMinor, it.paidById?.takeIf { id -> id in known }) }
            val balances = Split.balances(members, expenses, settlements.map { it.toPayment() })
            return SplitState(
                loaded = true, currency = s.currency, today = today, myName = s.myName, people = people,
                balances = balances, payments = Split.settleUp(balances), sharedExpenses = expenses, settlements = settlements,
            )
        }
    }
}

@Composable
fun SplitScreen(onBack: () -> Unit) {
    val vm = midasViewModel { SplitViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val workspaces = rememberWorkspaces()
    val list by workspaces.workspaces.collectAsStateWithLifecycle()
    val activeId by workspaces.activeId.collectAsStateWithLifecycle()
    val title = list.firstOrNull { it.id == activeId }?.name ?: "Midas"
    val context = LocalContext.current
    SplitContent(
        state, onBack, onAddPerson = vm::addPerson, onRemovePerson = vm::removePerson, onSettle = vm::settle,
        onDeleteSettlement = vm::deleteSettlement, onRename = vm::setMyName,
        onShareWhatsApp = { shareText(context, state.summary(title), whatsApp = true) },
        onShare = { shareText(context, state.summary(title), whatsApp = false) },
        message = message, onMessageShown = vm::messageShown,
    )
}

/** Opens WhatsApp with the text ready to send, or the share sheet if WhatsApp isn't installed. */
private fun shareText(context: Context, text: String, whatsApp: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (whatsApp) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(Intent(send).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next one, then fall back to the share sheet.
            }
        }
    }
    context.startActivity(Intent.createChooser(send, "Share settle-up"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitContent(
    state: SplitState,
    onBack: () -> Unit = {},
    onAddPerson: (String) -> Unit = {},
    onRemovePerson: (Person) -> Unit = {},
    onSettle: (Payment) -> Unit = {},
    onDeleteSettlement: (Long) -> Unit = {},
    onRename: (String) -> Unit = {},
    onShareWhatsApp: () -> Unit = {},
    onShare: () -> Unit = {},
    message: String? = null,
    onMessageShown: () -> Unit = {},
) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<Payment?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onMessageShown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split & settle up") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { if (state.people.isNotEmpty()) IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loaded && state.people.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Groups, "Share costs with people",
                        "Add your roommates or friends. Mark expenses as split and Midas works out who owes whom.",
                    ) { Button(onClick = { adding = true }, modifier = Modifier.testTag("add_person")) { Text("Add a person") } }
                }
                return@LazyColumn
            }
            item { PositionCard(state, onShareWhatsApp) }
            if (state.payments.isNotEmpty()) {
                item { SectionHeader("To settle up") }
                items(state.payments) { p ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.name(p.from)} pays ${state.name(p.to)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(Money.format(p.amountMinor, state.currency), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(onClick = { confirming = p }, modifier = Modifier.testTag("settle_${p.from}_${p.to}")) { Text("Paid") }
                        }
                    }
                }
            }
            item {
                SectionHeader("People") {
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                }
            }
            item {
                PersonRow(state.myName + " (you)", state.yourBalance, state.currency) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Change your name") }
                }
            }
            items(state.people, key = { it.id }) { p ->
                PersonRow(p.name, state.balances[p.id] ?: 0, state.currency) {
                    IconButton(onClick = { onRemovePerson(p) }) { Icon(Icons.Rounded.Close, "Remove ${p.name}") }
                }
            }
            if (state.settlements.isNotEmpty()) {
                item { SectionHeader("Settled") }
                items(state.settlements.take(10), key = { "s${it.id}" }) { s ->
                    ListItem(
                        headlineContent = { Text("${state.name(s.fromId)} paid ${state.name(s.toId)} ${Money.format(s.amountMinor, state.currency)}") },
                        supportingContent = { Text(Dates.relative(s.date, state.today)) },
                        trailingContent = { IconButton(onClick = { onDeleteSettlement(s.id) }) { Icon(Icons.Rounded.Close, "Undo") } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    if (adding) NameDialog("Add a person", "", "Add", onDismiss = { adding = false }) { onAddPerson(it); adding = false }
    if (renaming) NameDialog("Your name", state.myName, "Save", onDismiss = { renaming = false }) { onRename(it); renaming = false }
    confirming?.let { p ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Mark as paid?") },
            text = { Text("${state.name(p.from)} paid ${state.name(p.to)} ${Money.format(p.amountMinor, state.currency)}.") },
            confirmButton = { TextButton(onClick = { onSettle(p); confirming = null }, modifier = Modifier.testTag("confirm_settle")) { Text("Mark paid") } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PositionCard(state: SplitState, onShareWhatsApp: () -> Unit) {
    MidasCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        val on = MaterialTheme.colorScheme.onPrimaryContainer
        val b = state.yourBalance
        Text(
            when {
                b > 0 -> "You're owed"
                b < 0 -> "You owe"
                else -> "All settled up"
            },
            style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f),
        )
        if (b != 0L) Text(Money.format(kotlin.math.abs(b), state.currency), style = MaterialTheme.typography.headlineMedium, color = on, modifier = Modifier.testTag("split_balance"))
        Text(
            "Shared spending ${Money.format(state.totalSharedMinor, state.currency)} between ${state.members.size} people",
            style = MaterialTheme.typography.bodySmall, color = on,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onShareWhatsApp, modifier = Modifier.testTag("share_whatsapp")) { Text("Send on WhatsApp") }
    }
}

@Composable
private fun PersonRow(name: String, balance: Long, currency: String, action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        balance > 0 -> "is owed ${Money.format(balance, currency)}"
                        balance < 0 -> "owes ${Money.format(-balance, currency)}"
                        else -> "settled"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        balance > 0 -> MaterialTheme.money.positive
                        balance < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            action()
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true, label = { Text("Name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().testTag("person_name"),
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }, modifier = Modifier.testTag("person_save")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small home card for shared workspaces. */
@Composable
fun SplitHomeCard(yourBalance: Long, currency: String, onOpen: () -> Unit) {
    MidasCard(Modifier.testTag("split_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Split", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        yourBalance > 0 -> "You're owed ${Money.format(yourBalance, currency)}"
                        yourBalance < 0 -> "You owe ${Money.format(-yourBalance, currency)}"
                        else -> "All settled up"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (yourBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpen) { Text("Settle up") }
        }
    }
}
