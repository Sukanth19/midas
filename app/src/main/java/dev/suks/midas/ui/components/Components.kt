package dev.suks.midas.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.suks.midas.domain.BudgetProgress
import dev.suks.midas.domain.BudgetStatus
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.theme.money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Tinted circle with the category glyph: the visual anchor of every list row. */
@Composable
fun CategoryBadge(icon: String?, color: Long?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val tint = color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CategoryIcons.get(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun MoneyText(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    type: TxType? = null,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
) {
    val text = when (type) {
        TxType.INCOME -> "+" + Money.format(minor, currency)
        TxType.EXPENSE -> "−" + Money.format(minor, currency)
        null -> Money.format(minor, currency)
    }
    val c = when {
        color != Color.Unspecified -> color
        type == TxType.INCOME -> MaterialTheme.money.positive
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(text, modifier = modifier, style = style, color = c, maxLines = 1)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Standard rounded surface used for every dashboard block. */
@Composable
fun MidasCard(modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null, icon: ImageVector? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun budgetColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.OVER -> MaterialTheme.colorScheme.error
    BudgetStatus.WARNING -> MaterialTheme.money.warning
    else -> MaterialTheme.colorScheme.primary
}

/** Animated progress bar coloured by budget health. */
@Composable
fun BudgetBar(progress: BudgetProgress, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val animated by animateFloatAsState(progress.fraction.coerceIn(0f, 1f), tween(700), label = "budget")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(height).clip(CircleShape)
            .semantics { contentDescription = "${(progress.fraction * 100).toInt()} percent of budget used" },
        color = budgetColor(progress.status),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Text field that only accepts amounts valid for the currency. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val cleaned = new.filter { it.isDigit() || it == '.' }
            if (cleaned.isEmpty() || cleaned == "." || Money.parse(cleaned, currency) != null) onValueChange(cleaned)
        },
        label = { Text(label) },
        prefix = { Text(Money.symbol(currency) + " ") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onDate: (LocalDate?) -> Unit, today: LocalDate, modifier: Modifier = Modifier, clearable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    // A read-only text field doesn't fire onClick, so open the picker on the press-release interaction.
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = date?.let { Dates.relative(it, today) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("None") },
        trailingIcon = { Icon(Icons.Rounded.CalendarMonth, "Pick date") },
        modifier = modifier.fillMaxWidth(),
        interactionSource = interaction,
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                if (clearable) TextButton(onClick = { onDate(null); open = false }) { Text("Clear") }
                else TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) { DatePicker(state) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun Float.asPercent(): String = "${Math.round(this * 100)}%"

fun signedPercent(change: Float): String = (if (change >= 0) "+" else "−") + "${Math.round(kotlin.math.abs(change) * 100)}%"
