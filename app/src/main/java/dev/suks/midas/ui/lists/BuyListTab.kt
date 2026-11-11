package dev.suks.midas.ui.lists

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.BuyItem
import dev.suks.midas.data.Category
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.Priority
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.components.AmountField
import dev.suks.midas.ui.components.CategoryBadge
import dev.suks.midas.ui.components.ConfirmDialog
import dev.suks.midas.ui.components.DateField
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import dev.suks.midas.ui.theme.money
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BuyListState(
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val open: List<BuyItem> = emptyList(),
    val purchased: List<BuyItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loaded: Boolean = false,
) {
    val openTotalMinor: Long get() = open.sumOf { it.estimatedPriceMinor }
    val highPriorityMinor: Long get() = open.filter { it.priority == Priority.HIGH }.sumOf { it.estimatedPriceMinor }
}

class BuyListViewModel(private val c: AppContainer) : ViewModel() {
    val state = stateOf(
        combine(c.buyList.items, c.categories.categories, c.settings.settings) { items, cats, s ->
            BuyListState(
                currency = s.currency, today = c.today(),
                open = items.filter { !it.purchased }, purchased = items.filter { it.purchased },
                categories = cats.filter { it.type == TxType.EXPENSE }, loaded = true,
            )
        },
        BuyListState(),
    )

    fun save(item: BuyItem) = viewModelScope.launch { c.buyList.save(item) }
    fun delete(id: Long) = viewModelScope.launch { c.buyList.delete(id) }
    fun purchase(id: Long, priceMinor: Long) = viewModelScope.launch { c.buyList.markPurchased(id, priceMinor, c.today()) }
    fun undo(id: Long) = viewModelScope.launch { c.buyList.undoPurchase(id) }
    fun clearPurchased() = viewModelScope.launch { c.buyList.clearPurchased() }
}

@Composable
fun BuyListTab() {
    val vm = midasViewModel { BuyListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    BuyListContent(state, vm::save, vm::delete, vm::purchase, vm::undo, vm::clearPurchased)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyListContent(
    state: BuyListState,
    onSave: (BuyItem) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onPurchase: (Long, Long) -> Unit = { _, _ -> },
    onUndo: (Long) -> Unit = {},
    onClearPurchased: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<BuyItem?>(null) }
    var buying by remember { mutableStateOf<BuyItem?>(null) }
    var showPurchased by rememberSaveable { mutableStateOf(false) }
    val catById = remember(state.categories) { state.categories.associateBy { it.id } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                MidasCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Planned purchases", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(Money.format(state.openTotalMinor, state.currency), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "${state.open.size} item${if (state.open.size == 1) "" else "s"} · ${Money.format(state.highPriorityMinor, state.currency)} high priority",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (state.loaded && state.open.isEmpty()) {
                item { EmptyState(Icons.Rounded.ShoppingBag, "Your buy list is empty", "Add things you plan to buy. Tick them off and Midas records the spend for you.") }
            }
            items(state.open, key = { it.id }) { item ->
                BuyRow(item, catById[item.categoryId], state, onToggle = { buying = item }, onClick = { editing = item })
            }
            if (state.purchased.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { showPurchased = !showPurchased }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bought (${state.purchased.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (showPurchased) TextButton(onClick = onClearPurchased) { Text("Clear") }
                        Icon(if (showPurchased) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                }
                if (showPurchased) items(state.purchased, key = { it.id }) { item ->
                    BuyRow(item, catById[item.categoryId], state, onToggle = { onUndo(item.id) }, onClick = { editing = item })
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = BuyItem(name = "", estimatedPriceMinor = 0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_buy"),
        ) { Icon(Icons.Rounded.Add, "Add item") }
    }

    editing?.let { item ->
        BuyItemSheet(item, state, onDismiss = { editing = null }, onSave = { onSave(it); editing = null }, onDelete = { onDelete(it); editing = null })
    }
    buying?.let { item ->
        var price by remember(item.id) { mutableStateOf(Money.toInput(item.estimatedPriceMinor, state.currency)) }
        AlertDialog(
            onDismissRequest = { buying = null },
            title = { Text("Bought ${item.name}?") },
            text = {
                Column {
                    Text("It'll be recorded as an expense for today.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    AmountField(price, { price = it }, state.currency, "Price paid")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (Money.parse(price, state.currency) ?: 0) > 0,
                    onClick = { onPurchase(item.id, Money.parse(price, state.currency)!!); buying = null },
                ) { Text("Mark bought") }
            },
            dismissButton = { TextButton(onClick = { buying = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BuyRow(item: BuyItem, category: Category?, state: BuyListState, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.animateContentSize()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (item.purchased) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (item.purchased) "Mark not bought" else "Mark bought",
                    tint = if (item.purchased) MaterialTheme.money.positive else MaterialTheme.colorScheme.outline,
                )
            }
            CategoryBadge(category?.icon ?: "bag", category?.color, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.purchased) TextDecoration.LineThrough else null,
                    color = if (item.purchased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    if (!item.purchased) add(item.priority.label + " priority")
                    item.targetDate?.let { if (!item.purchased) add("by " + Dates.shortDate(it, state.today)) }
                    item.purchasedOn?.let { add("bought " + Dates.relativeInline(it, state.today)) }
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = priorityColor(item), maxLines = 1)
            }
            Text(
                Money.format(item.actualPriceMinor ?: item.estimatedPriceMinor, state.currency),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun priorityColor(item: BuyItem): Color = when {
    item.purchased -> MaterialTheme.colorScheme.onSurfaceVariant
    item.priority == Priority.HIGH -> MaterialTheme.colorScheme.error
    item.priority == Priority.MEDIUM -> MaterialTheme.money.warning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BuyItemSheet(item: BuyItem, state: BuyListState, onDismiss: () -> Unit, onSave: (BuyItem) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var price by remember { mutableStateOf(Money.toInput(item.estimatedPriceMinor, state.currency)) }
    var priority by remember { mutableStateOf(item.priority) }
    var categoryId by remember { mutableStateOf(item.categoryId) }
    var target by remember { mutableStateOf(item.targetDate) }
    var note by remember { mutableStateOf(item.note) }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val priceMinor = Money.parse(price, state.currency)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (item.id == 0L) "Add to buy list" else "Edit item", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                name, { name = it }, label = { Text("Item") }, singleLine = true, isError = tried && name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("buy_name"),
            )
            AmountField(price, { price = it }, state.currency, "Estimated price", isError = tried && (priceMinor ?: 0) <= 0)
            Text("Priority", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { i, p ->
                    SegmentedButton(priority == p, { priority = p }, SegmentedButtonDefaults.itemShape(i, Priority.entries.size)) { Text(p.label) }
                }
            }
            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c ->
                    FilterChip(categoryId == c.id, { categoryId = if (categoryId == c.id) null else c.id }, { Text(c.name) })
                }
            }
            DateField("Buy by (optional)", target, { target = it }, today = state.today, clearable = true)
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.id != 0L) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
                Button(
                    onClick = {
                        tried = true
                        if (name.isNotBlank() && (priceMinor ?: 0) > 0) {
                            onSave(item.copy(name = name.trim(), estimatedPriceMinor = priceMinor!!, priority = priority, categoryId = categoryId, targetDate = target, note = note.trim()))
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("buy_save"),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${item.name}?", "It will be removed from your buy list.", "Delete", { onDelete(item.id) }) { confirmDelete = false }
}

