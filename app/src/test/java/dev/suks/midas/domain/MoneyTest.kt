package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun `formats rupees with indian grouping and drops zero paise`() {
        assertEquals("₹1,25,000", Money.format(12_500_000, "INR"))
        assertEquals("₹1,250.50", Money.format(125_050, "INR"))
    }

    @Test fun `indian grouping into lakhs and crores`() {
        assertEquals("₹999", Money.format(99_900, "INR"))
        assertEquals("₹1,000", Money.format(100_000, "INR"))
        assertEquals("₹12,34,56,789", Money.format(123_456_789_00, "INR"))
        assertEquals("-₹1,00,000.25", Money.format(-10_000_025, "INR"))
    }

    @Test fun `other currencies group by thousands`() {
        assertEquals("$1,234,567.89", Money.format(123_456_789, "USD"))
        assertEquals("€0.05", Money.format(5, "EUR"))
        assertEquals("¥1,500", Money.format(1_500, "JPY"))
        assertEquals("CHF 12", Money.format(1_200, "CHF"))
    }

    @Test fun `round to whole units`() {
        assertEquals(2_365_500L, Money.roundToMajor(2_365_533, "INR"))
        assertEquals(1_500L, Money.roundToMajor(1_500, "JPY"))
    }

    @Test fun `keeps fraction when asked`() {
        assertEquals("$12.00", Money.format(1200, "USD", trimZeroFraction = false))
    }

    @Test fun `yen has no minor unit`() {
        assertEquals(0, Money.fractionDigits("JPY"))
        assertEquals(1500L, Money.parse("1500", "JPY"))
        assertNull(Money.parse("15.5", "JPY"))
    }

    @Test fun `parses what people type`() {
        assertEquals(125_050L, Money.parse("1,250.5", "INR"))
        assertEquals(9_900L, Money.parse(" 99 ", "USD"))
        assertEquals(1_200L, Money.parse("12.", "USD"))
        assertEquals(50L, Money.parse(".5", "USD"))
    }

    @Test fun `rejects junk and too many decimals`() {
        assertNull(Money.parse("", "INR"))
        assertNull(Money.parse(".", "INR"))
        assertNull(Money.parse("abc", "INR"))
        assertNull(Money.parse("1.234", "INR"))
        assertNull(Money.parse("-5", "INR"))
        assertNull(Money.parse("1.2.3", "INR"))
    }

    @Test fun `compact form for charts and widgets`() {
        assertEquals("₹950", Money.formatCompact(95_000, "INR"))
        assertEquals("₹12.5k", Money.formatCompact(1_250_000, "INR"))
        assertEquals("₹1k", Money.formatCompact(100_000, "INR"))
        assertEquals("$3.2M", Money.formatCompact(320_000_000, "USD"))
        assertEquals("-₹2k", Money.formatCompact(-200_000, "INR"))
    }

    @Test fun `round trips through the input string`() {
        for (v in listOf(1L, 99L, 100L, 125_050L, 10_000_000L)) {
            assertEquals(v, Money.parse(Money.toInput(v, "INR"), "INR"))
        }
        assertEquals("", Money.toInput(0, "INR"))
        assertEquals("1250.5", Money.toInput(125_050, "INR"))
    }
}
