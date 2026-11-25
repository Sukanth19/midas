package dev.suks.midas.ecosystem

import dev.suks.midas.data.Category
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FitnessSpendTest {
    private val day = LocalDate.of(2026, 11, 26)
    private val cats = listOf(
        Category(id = 1, name = "Food", icon = "food", color = 0),
        Category(id = 2, name = "Health & Fitness", icon = "health", color = 0),
        Category(id = 3, name = "Salary", icon = "work", color = 0, type = TxType.INCOME),
    )

    private fun tx(title: String, cat: Long?, amount: Long, type: TxType = TxType.EXPENSE) =
        Transaction(amountMinor = amount, type = type, categoryId = cat, title = title, date = day)

    @Test fun matchesByCategoryOrTitle() {
        assertTrue(FitnessSpend.isFitness("Monthly fee", "Gym"))
        assertTrue(FitnessSpend.isFitness("Whey protein 1kg", "Shopping"))
        assertTrue(FitnessSpend.isFitness("Creatine", null))
        assertFalse(FitnessSpend.isFitness("Groceries", "Food"))
        assertFalse(FitnessSpend.isFitness("Gymkhana club dinner", "Food"))
    }

    @Test fun totalsOnlyFitnessExpenses() {
        val txs = listOf(
            tx("Gym membership", 2, 150_000),
            tx("Creatine 250g", 1, 90_000),
            tx("Vegetables", 1, 40_000),
            tx("Refund from gym", 3, 10_000, TxType.INCOME),
        )
        assertEquals(240_000, FitnessSpend.total(txs, cats))
    }

    @Test fun picksAFittingCategory() {
        assertEquals(2L, FitnessSpend.categoryFor(cats))
        assertNull(FitnessSpend.categoryFor(cats.filter { it.id != 2L }))
    }
}
