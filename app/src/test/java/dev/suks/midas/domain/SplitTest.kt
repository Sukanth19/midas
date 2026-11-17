package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTest {
    private val you: MemberId = null
    private val rahul: MemberId = 1
    private val priya: MemberId = 2
    private val all = listOf(you, rahul, priya)

    @Test fun `even split with leftover paise`() {
        val b = Split.balances(all, listOf(SharedExpense(1_000, you)), emptyList())
        // 1000 / 3 = 333 each, 1 left over goes to the first member (you).
        assertEquals(mapOf(you to 666L, rahul to -333L, priya to -333L), b)
        assertEquals(0L, b.values.sum())
    }

    @Test fun `several payers and a settlement`() {
        val expenses = listOf(SharedExpense(90_000, you), SharedExpense(30_000, rahul))
        val b = Split.balances(all, expenses, listOf(Payment(priya, you, 20_000)))
        assertEquals(mapOf(you to 30_000L, rahul to -10_000L, priya to -20_000L), b)
        assertEquals(listOf(Payment(priya, you, 20_000), Payment(rahul, you, 10_000)), Split.settleUp(b))
    }

    @Test fun `settle up between other people`() {
        val b = mapOf(you to 0L, rahul to 500L, priya to -500L)
        assertEquals(listOf(Payment(priya, rahul, 500)), Split.settleUp(b))
        assertTrue(Split.settleUp(mapOf(you to 0L, rahul to 0L)).isEmpty())
    }

    @Test fun `summary for a group chat`() {
        val names = mapOf(you to "Ani", rahul to "Rahul", priya to "Priya")
        val text = Split.summary(
            "Flat 4B", all, names, listOf(SharedExpense(1_240_000, you)),
            listOf(Payment(rahul, you, 413_333), Payment(priya, you, 413_333)), "INR",
        )
        assertEquals(
            "Flat 4B · settle up\nShared spending ₹12,400 · ₹4,133.33 each\n\nRahul pays Ani ₹4,133.33\nPriya pays Ani ₹4,133.33\n\nSent from Midas",
            text,
        )
        assertTrue(Split.summary("Trip", all, names, emptyList(), emptyList(), "INR").contains("All settled up"))
    }
}
