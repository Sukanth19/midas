package dev.suks.midas.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import kotlin.math.abs

/**
 * All money is stored as a Long count of the currency's minor unit (paise, cents...).
 * That keeps sums exact; only formatting and parsing ever see decimals.
 */
object Money {

    val supportedCurrencies = listOf("INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD", "AED", "CHF")

    fun fractionDigits(currencyCode: String): Int =
        runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    // Fixed symbols: platform locale data differs between the JVM and Android ("US$" vs "$"),
    // and money should look the same everywhere.
    private val symbols = mapOf(
        "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
        "AUD" to "A$", "CAD" to "C$", "SGD" to "S$", "AED" to "AED ", "CHF" to "CHF ",
    )

    fun symbol(currencyCode: String): String = symbols[currencyCode] ?: "$currencyCode "

    fun toDecimal(minor: Long, currencyCode: String): BigDecimal =
        BigDecimal.valueOf(minor, fractionDigits(currencyCode))

    /**
     * "₹1,25,000", "$1,250.50". Rupees use Indian lakh/crore grouping, everything else groups by
     * thousands. Drops ".00" when [trimZeroFraction] is set and the amount is whole.
     */
    fun format(minor: Long, currencyCode: String, trimZeroFraction: Boolean = true): String {
        val digits = fractionDigits(currencyCode)
        val unit = pow10(digits)
        val abs = abs(minor)
        val whole = (abs / unit).toString()
        val grouped = if (currencyCode == "INR") groupIndian(whole) else groupThousands(whole)
        val showFraction = digits > 0 && !(trimZeroFraction && abs % unit == 0L)
        val fraction = if (showFraction) "." + (abs % unit).toString().padStart(digits, '0') else ""
        return (if (minor < 0) "-" else "") + symbol(currencyCode) + grouped + fraction
    }

    /** Rounds to a whole major unit, for estimates where paise are noise. */
    fun roundToMajor(minor: Long, currencyCode: String): Long {
        val unit = pow10(fractionDigits(currencyCode))
        return Math.round(minor.toDouble() / unit) * unit
    }

    private fun groupThousands(s: String): String = s.reversed().chunked(3).joinToString(",").reversed()

    /** 12500000 -> 1,25,00,000: last three digits, then groups of two. */
    private fun groupIndian(s: String): String {
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }

    /** Short form for charts and widgets: ₹950, ₹12.5k, ₹3.2M. */
    fun formatCompact(minor: Long, currencyCode: String): String {
        val major = toDecimal(minor, currencyCode).toDouble()
        val a = abs(major)
        val sign = if (major < 0) "-" else ""
        val sym = symbol(currencyCode)
        fun one(v: Double): String {
            val s = BigDecimal(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            return s
        }
        return when {
            a >= 1_000_000_000 -> "$sign$sym${one(a / 1_000_000_000)}B"
            a >= 1_000_000 -> "$sign$sym${one(a / 1_000_000)}M"
            a >= 1_000 -> "$sign$sym${one(a / 1_000)}k"
            else -> "$sign$sym${BigDecimal(a).setScale(0, RoundingMode.HALF_UP).toPlainString()}"
        }
    }

    /**
     * Parses what a person types into an amount field ("1,250.5", " 99 ", "12.") into minor units.
     * Returns null for anything that is not a non-negative number, or has too many decimals.
     */
    fun parse(input: String, currencyCode: String): Long? {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty() || cleaned == ".") return null
        if (!cleaned.matches(Regex("""\d*\.?\d*"""))) return null
        val digits = fractionDigits(currencyCode)
        val frac = cleaned.substringAfter('.', "")
        if (frac.length > digits) return null
        return runCatching {
            BigDecimal(if (cleaned.endsWith(".")) cleaned.dropLast(1) else cleaned)
                .movePointRight(digits)
                .longValueExact()
        }.getOrNull()
    }

    /** Minor units back to an editable string with no grouping: 125050 -> "1250.5". */
    fun toInput(minor: Long, currencyCode: String): String =
        toDecimal(minor, currencyCode).stripTrailingZeros().toPlainString().let { if (it == "0") "" else it }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
