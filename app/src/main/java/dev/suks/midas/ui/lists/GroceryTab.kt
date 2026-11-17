package dev.suks.midas.ui.lists

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.GroceryItem
import dev.suks.midas.data.GroceryPrice
import dev.suks.midas.data.GroceryWeek
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.GroceryTotals
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.PriceTrend
import dev.suks.midas.ui.components.asPercent
import dev.suks.midas.ui.theme.money
import kotlin.math.abs
import dev.suks.midas.ui.components.AmountField
import dev.suks.midas.ui.components.BarChart
import dev.suks.midas.ui.components.ConfirmDialog
import dev.suks.midas.ui.components.EmptyState
import dev.suks.midas.ui.components.MidasCard
import dev.suks.midas.ui.components.SectionHeader
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GroceryState(
    val currency: String = "INR",
    val today: LocalDate = LocalDate.now(),
    val sections: List<Pair<String, List<GroceryItem>>> = emptyList(),
    val totals: GroceryTotals = GroceryTotals(0, 0, 0, 0),
    val weeks: List<GroceryWeek> = emptyList(),
    /** Price history per [GroceryMath.priceKey], oldest first. */
    val prices: Map<String, List<GroceryPrice>> = emptyMap(),
    val loaded: Boolean = false,
) {
    /** The item's price history in its current unit. */
    fun trendFor(item: GroceryItem): PriceTrend =
        PriceTrend(prices[GroceryMath.priceKey(item.name)].orEmpty().filter { it.unit == item.unit }.map { it.toPoint() })
}

class GroceryViewModel(private val c: AppContainer) : ViewModel() {
    val state = stateOf(
        combine(c.grocery.items, c.grocery.weeks, c.grocery.prices, c.settings.settings) { items, weeks, prices, s ->
            GroceryState(
                currency = s.currency, today = c.today(),
                // Sections in the store's walking order, not alphabetical.
                sections = items.groupBy { it.section }.toList()
                    .sortedBy { (sec, _) -> GroceryMath.sections.indexOf(sec).let { if (it < 0) 999 else it } }
                    .map { (sec, list) -> sec to list.sortedWith(compareBy({ it.checked }, { it.name.lowercase() })) },
                totals = GroceryMath.totals(items.map { it.toLine() }),
                weeks = weeks, prices = prices.groupBy { it.name }, loaded = true,
            )
        },
        GroceryState(),
    )

    fun toggle(id: Long, checked: Boolean) = viewModelScope.launch { c.grocery.setChecked(id, checked) }
    fun adjust(id: Long, dir: Int) = viewModelScope.launch { c.grocery.adjustQuantity(id, dir) }
    fun save(item: GroceryItem) = viewModelScope.launch { c.grocery.save(item, c.today()) }
    fun delete(id: Long) = viewModelScope.launch { c.grocery.delete(id) }
    fun finishWeek(actual: Long?) = viewModelScope.launch {
        val groceriesCat = c.categories.getAll().firstOrNull { it.name.equals("Groceries", true) }?.id
        c.grocery.finishWeek(c.today(), actual, groceriesCat)
    }
}

@Composable
fun GroceryTab() {
    val vm = midasViewModel { GroceryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    GroceryContent(state, vm::toggle, vm::adjust, vm::save, vm::delete, vm::finishWeek)
}

@Composable
fun GroceryContent(
    state: GroceryState,
    onToggle: (Long, Boolean) -> Unit = { _, _ -> },
    onAdjust: (Long, Int) -> Unit = { _, _ -> },
    onSave: (GroceryItem) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onFinishWeek: (Long?) -> Unit = {},
) {
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    var finishing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { WeekSummary(state, onFinish = { finishing = true }) }
            if (state.loaded && state.totals.itemCount == 0) {
                item {
                    EmptyState(
                        Icons.Rounded.LocalGroceryStore, "Plan your weekly shop",
                        "Add the things you buy every week with how much you need. Midas works out what the shop will cost.",
                    )
                }
            }
            state.sections.forEach { (section, items) ->
                item(key = "s-$section") { SectionHeader(section) }
                items(items, key = { it.id }) { item ->
                    GroceryRow(item, state.currency, state.trendFor(item).lastChange, onToggle = { onToggle(item.id, it) }, onAdjust = { onAdjust(item.id, it) }, onClick = { editing = item })
                }
            }
            if (state.weeks.isNotEmpty()) item { HistoryCard(state) }
        }
        FloatingActionButton(
            onClick = { editing = GroceryItem(name = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_grocery"),
        ) { Icon(Icons.Rounded.Add, "Add grocery item") }
    }

    editing?.let { item ->
        GroceryItemSheet(item, state.currency, state.trendFor(item), state.today, onDismiss = { editing = null }, onSave = { onSave(it); editing = null }, onDelete = { onDelete(it); editing = null })
    }
    if (finishing) FinishWeekDialog(state, onDismiss = { finishing = false }, onConfirm = { onFinishWeek(it); finishing = false })
}

@Composable
private fun WeekSummary(state: GroceryState, onFinish: () -> Unit) {
    val t = state.totals
    MidasCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        val on = MaterialTheme.colorScheme.onTertiaryContainer
        Text("This week · from ${Dates.shortDate(Dates.weekStart(state.today), state.today)}", style = MaterialTheme.typography.labelLarge, color = on)
        Text(Money.format(t.estimatedMinor, state.currency), style = MaterialTheme.typography.headlineMedium, color = on, modifier = Modifier.testTag("grocery_estimate"))
        Text("estimated for ${t.itemCount} item${if (t.itemCount == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = on)
        Spacer(Modifier.height(14.dp))
        val p by animateFloatAsState(t.progress, label = "cart")
        LinearProgressIndicator(
            progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = on, trackColor = on.copy(alpha = 0.15f), drawStopIndicator = {},
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${t.checkedCount}/${t.itemCount} in cart · ${Money.format(t.inCartMinor, state.currency)}",
                style = MaterialTheme.typography.bodySmall, color = on, modifier = Modifier.weight(1f),
            )
            if (t.itemCount > 0) FilledTonalButton(onClick = onFinish) { Text("Finish week") }
        }
    }
}

@Composable
private fun GroceryRow(item: GroceryItem, currency: String, priceChange: Float?, onToggle: (Boolean) -> Unit, onAdjust: (Int) -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.checked, onToggle, modifier = Modifier.testTag("check_${item.name}"))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val line = GroceryMath.lineTotal(item.quantity, item.unitPriceMinor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.unitPriceMinor > 0) "${Money.format(item.unitPriceMinor, currency)}/${item.unit} · ${Money.format(line, currency)}" else "No price set",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (priceChange != null && priceChange != 0f) {
                        Spacer(Modifier.width(6.dp))
                        PriceChangeLabel(priceChange, Modifier.testTag("price_change_${item.name}"))
                    }
                }
            }
            val small = IconButtonDefaults.filledTonalIconButtonColors()
            FilledTonalIconButton(onClick = { onAdjust(-1) }, modifier = Modifier.size(32.dp), colors = small) { Icon(Icons.Rounded.Remove, "Less", Modifier.size(16.dp)) }
            Text(
                "${GroceryMath.formatQuantity(item.quantity)} ${item.unit}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp).width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
            FilledTonalIconButton(onClick = { onAdjust(1) }, modifier = Modifier.size(32.dp), colors = small) { Icon(Icons.Rounded.Add, "More", Modifier.size(16.dp)) }
        }
    }
}

/** "▲ 11%" in red when a price went up, "▼ 5%" in green when it came down. */
@Composable
private fun PriceChangeLabel(change: Float, modifier: Modifier = Modifier) {
    Text(
        (if (change > 0) "▲ " else "▼ ") + abs(change).asPercent(),
        style = MaterialTheme.typography.labelSmall,
        color = if (change > 0) MaterialTheme.colorScheme.error else MaterialTheme.money.positive,
        modifier = modifier,
    )
}

@Composable
private fun HistoryCard(state: GroceryState) {
    val recent = state.weeks.take(8).reversed()
    Spacer(Modifier.height(8.dp))
    MidasCard {
        Text("Weekly grocery spend", style = MaterialTheme.typography.titleMedium)
        val avg = recent.map { it.actualMinor }.average().toLong()
        Text("Average ${Money.format(avg, state.currency)} over ${recent.size} week${if (recent.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BarChart(
            values = recent.map { it.actualMinor.toFloat() },
            labels = recent.map { "${it.weekStart.dayOfMonth}/${it.weekStart.monthValue}" },
            height = 110.dp,
            highlightColor = MaterialTheme.colorScheme.tertiary,
            barColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
            valueLabel = { Money.format(it.toLong(), state.currency) },
        )
        recent.reversed().take(4).forEach { w ->
            ListItem(
                headlineContent = { Text("Week of ${Dates.shortDate(w.weekStart, state.today)}") },
                supportingContent = { Text("${w.itemCount} items · est. ${Money.format(w.estimatedMinor, state.currency)}") },
                trailingContent = { Text(Money.format(w.actualMinor, state.currency), style = MaterialTheme.typography.titleSmall) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }
}

@Composable
private fun FinishWeekDialog(state: GroceryState, onDismiss: () -> Unit, onConfirm: (Long?) -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(state.totals.inCartMinor.takeIf { it > 0 } ?: state.totals.estimatedMinor, state.currency)) }
    var log by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish this week?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Staples stay on the list and get unchecked. One-off items are removed.", style = MaterialTheme.typography.bodyMedium)
                AmountField(amount, { amount = it }, state.currency, "Actually spent")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Record as a Groceries expense", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(log, { log = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (log) Money.parse(amount, state.currency) else null) }) { Text("Finish week") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GroceryItemSheet(item: GroceryItem, currency: String, trend: PriceTrend, today: LocalDate, onDismiss: () -> Unit, onSave: (GroceryItem) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var qty by remember { mutableStateOf(GroceryMath.formatQuantity(item.quantity)) }
    var unit by remember { mutableStateOf(item.unit) }
    var price by remember { mutableStateOf(Money.toInput(item.unitPriceMinor, currency)) }
    var section by remember { mutableStateOf(item.section) }
    var staple by remember { mutableStateOf(item.staple) }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val qtyValue = qty.toDoubleOrNull()?.takeIf { it > 0 }
    val estimate = GroceryMath.lineTotal(qtyValue ?: 0.0, Money.parse(price, currency) ?: 0)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (item.id == 0L) "Add grocery item" else "Edit ${item.name}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                name, { name = it }, label = { Text("Item") }, singleLine = true, isError = tried && name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("grocery_name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    qty, { v -> if (v.isEmpty() || v.matches(Regex("""\d*\.?\d{0,3}"""))) qty = v }, label = { Text("How much") },
                    singleLine = true, isError = tried && qtyValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("grocery_qty"),
                )
                Box(Modifier.weight(1f)) { AmountField(price, { price = it }, currency, "Price per $unit") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroceryMath.units.forEach { u -> FilterChip(unit == u, { unit = u }, { Text(u) }) }
            }
            if (estimate > 0) Text("Costs about ${Money.format(estimate, currency)} a week", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (trend.points.size >= 2) PriceHistory(trend, currency, item.unit, today)
            Text("Aisle", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroceryMath.sections.forEach { s -> FilterChip(section == s, { section = s }, { Text(s) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weekly staple", style = MaterialTheme.typography.bodyLarge)
                    Text("Keep it on the list every week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(staple, { staple = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.id != 0L) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
                Button(
                    onClick = {
                        tried = true
                        if (name.isNotBlank() && qtyValue != null) {
                            onSave(item.copy(name = name.trim(), quantity = qtyValue, unit = unit, unitPriceMinor = Money.parse(price, currency) ?: 0, section = section, staple = staple))
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("grocery_save"),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) ConfirmDialog("Remove ${item.name}?", "It will be taken off your weekly list.", "Remove", { onDelete(item.id) }) { confirmDelete = false }
}

@Composable
private fun PriceHistory(trend: PriceTrend, currency: String, unit: String, today: LocalDate) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.testTag("price_history")) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Price history", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                trend.overallChange?.let { change ->
                    val first = trend.points.first()
                    Text("since ${Dates.shortDate(first.date, today)} ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (change == 0f) Text("no change", style = MaterialTheme.typography.labelSmall) else PriceChangeLabel(change)
                }
            }
            trend.points.takeLast(5).reversed().forEach { p ->
                Row {
                    Text(Dates.shortDate(p.date, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("${Money.format(p.unitPriceMinor, currency)}/$unit", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
