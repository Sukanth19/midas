package dev.suks.midas.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Minimal shape the grocery maths needs; the Room entity maps onto it. */
data class GroceryLine(val quantity: Double, val unitPriceMinor: Long, val checked: Boolean)

data class GroceryTotals(
    val estimatedMinor: Long,
    val inCartMinor: Long,
    val itemCount: Int,
    val checkedCount: Int,
) {
    val remainingMinor: Long get() = estimatedMinor - inCartMinor
    val progress: Float get() = if (itemCount == 0) 0f else checkedCount.toFloat() / itemCount
}

data class PricePoint(val date: LocalDate, val unitPriceMinor: Long)

/** How an item's price has moved; [points] are oldest first. */
data class PriceTrend(val points: List<PricePoint>) {
    val latestMinor: Long? get() = points.lastOrNull()?.unitPriceMinor
    val previousMinor: Long? get() = points.getOrNull(points.size - 2)?.unitPriceMinor

    /** Change since the price before the latest one, e.g. 0.1 for +10%. Null without two prices. */
    val lastChange: Float? get() = change(previousMinor, latestMinor)

    /** Change since the first price on record. */
    val overallChange: Float? get() = if (points.size < 2) null else change(points.first().unitPriceMinor, latestMinor)

    private fun change(from: Long?, to: Long?): Float? =
        if (from == null || to == null || from <= 0) null else (to - from).toFloat() / from
}

object GroceryMath {

    /** Price histories are matched on the name, ignoring case and stray spaces. */
    fun priceKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

    /** quantity × unit price, rounded half-up to the minor unit (1.5 kg × ₹33.33 = ₹50.00). */
    fun lineTotal(quantity: Double, unitPriceMinor: Long): Long {
        if (quantity <= 0 || unitPriceMinor <= 0) return 0
        return BigDecimal.valueOf(quantity)
            .multiply(BigDecimal.valueOf(unitPriceMinor))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun totals(lines: List<GroceryLine>): GroceryTotals {
        var est = 0L
        var cart = 0L
        var checked = 0
        for (l in lines) {
            val t = lineTotal(l.quantity, l.unitPriceMinor)
            est += t
            if (l.checked) {
                cart += t
                checked++
            }
        }
        return GroceryTotals(est, cart, lines.size, checked)
    }

    /** "1", "1.5", "0.25": no trailing zeros, up to 3 decimals. */
    fun formatQuantity(q: Double): String =
        BigDecimal.valueOf(q).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Step used by the +/- buttons: whole units for pieces, 0.25 for kg/l, 50 for g/ml. */
    fun stepFor(unit: String): Double = when (unit.lowercase()) {
        "kg", "l", "lb" -> 0.25
        "g", "ml" -> 50.0
        else -> 1.0
    }

    val units = listOf("pcs", "kg", "g", "l", "ml", "pack", "dozen", "lb")

    val sections = listOf("Produce", "Dairy & Eggs", "Bakery", "Meat & Fish", "Pantry", "Frozen", "Snacks", "Drinks", "Household", "Personal care", "Other")
}
