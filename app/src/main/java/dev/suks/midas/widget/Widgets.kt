package dev.suks.midas.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.suks.midas.MainActivity
import dev.suks.midas.MidasApplication
import dev.suks.midas.R
import dev.suks.midas.domain.BudgetStatus
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.Money
import dev.suks.midas.ui.theme.DarkColors
import dev.suks.midas.ui.theme.LightColors
import kotlin.math.roundToInt

private val MidasWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)
private val WarningColor = ColorProvider(day = Color(0xFF8F4E00), night = Color(0xFFFFB870))
private val PositiveColor = ColorProvider(day = Color(0xFF1B6D2F), night = Color(0xFF8DD99A))

@Composable
private fun MidasWidgetTheme(data: WidgetData, content: @Composable () -> Unit) {
    // Material You colours follow the in-app setting; otherwise widgets wear Midas gold.
    if (data.dynamicColor) GlanceTheme(content = content) else GlanceTheme(colors = MidasWidgetColors, content = content)
}

private fun openApp(context: Context, action: String? = null) =
    actionStartActivity(Intent(context, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))

@Composable
private fun label(size: Int = 12) = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = size.sp, fontWeight = FontWeight.Medium)

@Composable
private fun WidgetSurface(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(16.dp),
        content = content,
    )
}

/* ─────────────────────────── Spending summary ─────────────────────────── */

class SpendingWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(120.dp, 120.dp)
        val WIDE = DpSize(250.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent { MidasWidgetTheme(data) { SpendingContent(context, data) } }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { MidasWidgetTheme(WidgetData.sample()) { SpendingContent(context, WidgetData.sample()) } }
    }
}

@Composable
fun SpendingContent(context: Context, d: WidgetData) {
    val wide = LocalSize.current.width >= SpendingWidget.WIDE.width
    val b = d.budget
    WidgetSurface(GlanceModifier.clickable(openApp(context))) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("Spent in ${d.monthLabel}", style = label(), maxLines = 1)
                Text(
                    Money.formatCompact(b.spentMinor, d.currency),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (wide) 30.sp else 26.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(8.dp))
                if (b.limitMinor > 0) {
                    LinearProgressIndicator(
                        progress = b.fraction.coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                        color = when (b.status) {
                            BudgetStatus.OVER -> GlanceTheme.colors.error
                            BudgetStatus.WARNING -> WarningColor
                            else -> GlanceTheme.colors.primary
                        },
                        backgroundColor = GlanceTheme.colors.surfaceVariant,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        if (b.remainingMinor >= 0) "${Money.formatCompact(b.remainingMinor, d.currency)} left" else "${Money.formatCompact(-b.remainingMinor, d.currency)} over",
                        style = TextStyle(
                            color = if (b.remainingMinor >= 0) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.error,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                } else {
                    Text("+${Money.formatCompact(d.incomeMinor, d.currency)} income", style = TextStyle(color = PositiveColor, fontSize = 12.sp), maxLines = 1)
                }
            }
            if (wide) {
                Spacer(GlanceModifier.width(16.dp))
                WeekBars(d)
            }
        }
    }
}

/** Seven tiny bars for the last week, built from rounded boxes since widgets can't draw. */
@Composable
private fun WeekBars(d: WidgetData) {
    val max = (d.last7Days.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val maxH = 56
    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Row(GlanceModifier.height(maxH.dp), verticalAlignment = Alignment.Bottom) {
            d.last7Days.forEachIndexed { i, v ->
                val h = if (v <= 0) 4 else (v.toFloat() / max * maxH).roundToInt().coerceAtLeast(6)
                Box(
                    GlanceModifier.width(10.dp).height(h.dp).cornerRadius(5.dp)
                        .background(if (i == d.last7Days.lastIndex) GlanceTheme.colors.primary else GlanceTheme.colors.primaryContainer),
                ) {}
                if (i < d.last7Days.lastIndex) Spacer(GlanceModifier.width(5.dp))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Text("Last 7 days", style = label(10))
    }
}

/* ─────────────────────────── Grocery checklist ─────────────────────────── */

class GroceryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent { MidasWidgetTheme(data) { GroceryContent(context, data, interactive = true) } }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { MidasWidgetTheme(WidgetData.sample()) { GroceryContent(context, WidgetData.sample(), interactive = false) } }
    }
}

val GroceryItemIdKey = ActionParameters.Key<Long>("grocery_item_id")

@Composable
fun GroceryContent(context: Context, d: WidgetData, interactive: Boolean) {
    val t = d.groceryTotals
    WidgetSurface {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, MainActivity.ACTION_OPEN_GROCERIES)), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(32.dp).cornerRadius(16.dp).background(GlanceTheme.colors.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_widget_cart), "Groceries", GlanceModifier.size(18.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onTertiaryContainer))
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Groceries", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text("${t.checkedCount}/${t.itemCount} in cart · est. ${Money.formatCompact(t.estimatedMinor, d.currency)}", style = label(), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (d.grocery.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your weekly list is empty.\nTap to add items.", style = label(13))
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(d.grocery, itemId = { it.id }) { item ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckBox(
                                checked = item.checked,
                                onCheckedChange = if (interactive) actionRunCallback<ToggleGroceryAction>(actionParametersOf(GroceryItemIdKey to item.id)) else null,
                                text = item.name,
                                style = TextStyle(color = if (item.checked) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface, fontSize = 14.sp),
                                colors = CheckboxDefaults.colors(checkedColor = GlanceTheme.colors.primary, uncheckedColor = GlanceTheme.colors.outline),
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1,
                            )
                            Text("${GroceryMath.formatQuantity(item.quantity)} ${item.unit}", style = label(12), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class ToggleGroceryAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[GroceryItemIdKey] ?: return
        // The repository's change listener refreshes every widget afterwards.
        (context.applicationContext as MidasApplication).container.grocery.toggle(id)
    }
}

/* ─────────────────────────── Upcoming bills ─────────────────────────── */

class BillsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent { MidasWidgetTheme(data) { BillsContent(context, data) } }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { MidasWidgetTheme(WidgetData.sample()) { BillsContent(context, WidgetData.sample()) } }
    }
}

@Composable
fun BillsContent(context: Context, d: WidgetData) {
    WidgetSurface(GlanceModifier.clickable(openApp(context, MainActivity.ACTION_OPEN_BILLS))) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(32.dp).cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_widget_receipt), "Bills", GlanceModifier.size(18.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
                }
                Spacer(GlanceModifier.width(10.dp))
                Text("Upcoming bills", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (d.bills.isEmpty()) {
                Text("No bills due. Add rent and bills in Midas.", style = label(13))
            }
            d.bills.take(if (LocalSize.current.height < 150.dp) 2 else 4).forEach { b ->
                val overdue = b.nextDue.isBefore(d.today)
                val soon = !overdue && Dates.daysUntil(d.today, b.nextDue) <= 2
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(b.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(
                            if (overdue) "Overdue" else Dates.relative(b.nextDue, d.today),
                            style = TextStyle(
                                color = when {
                                    overdue -> GlanceTheme.colors.error
                                    soon -> WarningColor
                                    else -> GlanceTheme.colors.onSurfaceVariant
                                },
                                fontSize = 12.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                    Text(Money.format(b.amountMinor, d.currency), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

/* ─────────────────────────── Quick add ─────────────────────────── */

class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dynamic = runCatching { WidgetData.load(context).dynamicColor }.getOrDefault(false)
        provideContent { QuickAddTheme(dynamic) { QuickAddContent(context) } }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { QuickAddTheme(false) { QuickAddContent(context) } }
    }
}

@Composable
private fun QuickAddTheme(dynamic: Boolean, content: @Composable () -> Unit) {
    if (dynamic) GlanceTheme(content = content) else GlanceTheme(colors = MidasWidgetColors, content = content)
}

@Composable
fun QuickAddContent(context: Context) {
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).cornerRadius(28.dp)
            .clickable(openApp(context, MainActivity.ACTION_ADD_EXPENSE)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Image(ImageProvider(R.drawable.ic_widget_add), "Add expense", GlanceModifier.size(32.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
            Text("Add", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/* ─────────────────────────── Receivers ─────────────────────────── */

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SpendingWidget() }
class GroceryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GroceryWidget() }
class BillsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BillsWidget() }
class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickAddWidget() }

