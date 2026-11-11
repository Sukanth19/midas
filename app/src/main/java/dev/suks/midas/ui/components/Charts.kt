package dev.suks.midas.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DonutSlice(val value: Float, val color: Color, val label: String)

/**
 * Donut with rounded, gapped segments that sweep in on first show.
 * The centre slot shows totals; tapping a slice reports it via [onSelect].
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 22.dp,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(slices) { sweep.snapTo(0f); sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    Box(
        modifier.size(size).semantics { contentDescription = slices.joinToString { "${it.label} ${(it.value / total * 100).toInt()} percent" } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(size).pointerInput(slices) {
                detectTapGestures { p ->
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val angle = (Math.toDegrees(kotlin.math.atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())) + 450) % 360
                    var acc = 0f
                    val idx = slices.indexOfFirst { s -> acc += s.value / total * 360f; angle <= acc }
                    onSelect(if (idx == selected) null else idx.takeIf { it >= 0 })
                }
            },
        ) {
            val stroke = thickness.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 3.5f else 0f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val full = s.value / total * 360f
                val drawn = (full - gap).coerceAtLeast(0.6f) * sweep.value
                val w = if (selected == i) stroke * 1.35f else stroke
                val alpha = if (selected == null || selected == i) 1f else 0.35f
                drawArc(s.color.copy(alpha = alpha), start + gap / 2, drawn, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Butt))
                start += full
            }
        }
        center()
    }
}

/**
 * Vertical bar chart. [highlight] draws one bar in the accent colour (e.g. today or this month);
 * [reference] draws a dashed line such as the daily budget allowance.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    barColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlight: Int? = values.lastIndex,
    reference: Float? = null,
    labelEvery: Int = 1,
    detailLabels: List<String> = labels,
    valueLabel: (Float) -> String = { "" },
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    var touched by remember(values) { mutableStateOf<Int?>(null) }
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(reference ?: 0f).coerceAtLeast(1f)
    val refColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column(modifier.fillMaxWidth()) {
        val shown = touched ?: highlight
        Text(
            text = shown?.let { if (it in detailLabels.indices) "${detailLabels[it]} · ${valueLabel(values[it])}" else null } ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(values) {
                detectTapGestures { p ->
                    val slot = this.size.width / values.size.coerceAtLeast(1)
                    touched = (p.x / slot).toInt().coerceIn(0, values.lastIndex)
                }
            },
        ) {
            if (values.isEmpty()) return@Canvas
            for (i in 1..3) {
                val y = this.size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(this.size.width, y), 1.dp.toPx())
            }
            val slot = this.size.width / values.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / max) * this.size.height * grow.value
                val x = i * slot + (slot - barW) / 2
                val isHi = i == shown
                drawRoundRect(
                    color = if (isHi) highlightColor else barColor,
                    topLeft = Offset(x, this.size.height - h),
                    size = Size(barW, h.coerceAtLeast(if (v > 0) 3.dp.toPx() else 0f)),
                    cornerRadius = CornerRadius(barW / 2.5f),
                )
            }
            reference?.let {
                val y = this.size.height - it / max * this.size.height
                drawLine(refColor, Offset(0f, y), Offset(this.size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % labelEvery == 0) l else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Smooth line with gradient fill; optional dashed [comparison] series (e.g. last month). */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    comparison: List<Float>? = null,
    comparisonColor: Color = MaterialTheme.colorScheme.outline,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = ((values + (comparison ?: emptyList())).maxOrNull() ?: 0f).coerceAtLeast(1f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        fun pathFor(series: List<Float>, count: Int): Path {
            val p = Path()
            if (series.isEmpty()) return p
            val stepX = if (count > 1) size.width / (count - 1) else 0f
            series.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - v / max * size.height * 0.92f
                if (i == 0) p.moveTo(x, y) else {
                    val px = (i - 1) * stepX
                    val py = size.height - series[i - 1] / max * size.height * 0.92f
                    val mx = (px + x) / 2
                    p.cubicTo(mx, py, mx, y, x, y)
                }
            }
            return p
        }
        val count = maxOf(values.size, comparison?.size ?: 0)
        comparison?.let {
            drawPath(pathFor(it, count), comparisonColor, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)), cap = StrokeCap.Round))
        }
        if (values.isEmpty()) return@Canvas
        clipRectReveal(reveal.value) {
            val line = pathFor(values, count)
            val stepX = if (count > 1) size.width / (count - 1) else 0f
            val fill = Path().apply {
                addPath(line)
                lineTo(values.lastIndex * stepX, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lastX = values.lastIndex * stepX
            val lastY = size.height - values.last() / max * size.height * 0.92f
            drawCircle(color, 5.dp.toPx(), Offset(lastX, lastY))
            drawCircle(Color.White.copy(alpha = 0.9f), 2.dp.toPx(), Offset(lastX, lastY))
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectReveal(fraction: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.clipRect(0f, 0f, size.width * fraction + 8f, size.height + 8f)
    block()
    drawContext.canvas.restore()
}

/** Small coloured dot + label + value row used for chart legends. */
@Composable
fun LegendRow(color: Color, label: String, value: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(label, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (supporting != null) {
            Text(supporting, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
