package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryMathTest {

    @Test fun `line totals round half up to the minor unit`() {
        assertEquals(5_000L, GroceryMath.lineTotal(1.5, 3_333)) // 4999.5 -> 5000
        assertEquals(35_000L, GroceryMath.lineTotal(5.0, 7_000))
        assertEquals(1_750L, GroceryMath.lineTotal(0.25, 7_000))
        assertEquals(0L, GroceryMath.lineTotal(0.0, 7_000))
        assertEquals(0L, GroceryMath.lineTotal(2.0, 0))
    }

    @Test fun `floating point quantities do not drift`() {
        // 0.1 + 0.2 style errors must not leak into money.
        assertEquals(330L, GroceryMath.lineTotal(0.3, 1_100))
    }

    @Test fun `totals split estimate and cart`() {
        val t = GroceryMath.totals(
            listOf(
                GroceryLine(2.0, 6_000, checked = true),   // 12,000
                GroceryLine(1.0, 8_400, checked = false),  // 8,400
                GroceryLine(5.0, 7_000, checked = true),   // 35,000
            ),
        )
        assertEquals(55_400L, t.estimatedMinor)
        assertEquals(47_000L, t.inCartMinor)
        assertEquals(8_400L, t.remainingMinor)
        assertEquals(3, t.itemCount)
        assertEquals(2, t.checkedCount)
        assertEquals(2f / 3f, t.progress, 0.0001f)
    }

    @Test fun `empty list`() {
        val t = GroceryMath.totals(emptyList())
        assertEquals(0L, t.estimatedMinor)
        assertEquals(0f, t.progress)
    }

    @Test fun `quantity formatting and steps`() {
        assertEquals("1", GroceryMath.formatQuantity(1.0))
        assertEquals("1.5", GroceryMath.formatQuantity(1.5))
        assertEquals("0.25", GroceryMath.formatQuantity(0.25))
        assertEquals("0.333", GroceryMath.formatQuantity(1.0 / 3))
        assertEquals(0.25, GroceryMath.stepFor("kg"), 0.0)
        assertEquals(50.0, GroceryMath.stepFor("g"), 0.0)
        assertEquals(1.0, GroceryMath.stepFor("pcs"), 0.0)
    }
}
