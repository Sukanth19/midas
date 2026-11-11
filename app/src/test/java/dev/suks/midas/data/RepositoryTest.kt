package dev.suks.midas.data

import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.domain.CsvTransaction
import dev.suks.midas.domain.Frequency
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.TxSource
import dev.suks.midas.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: MidasDatabase
    private var changes = 0
    private val onChanged: ChangeListener = { changes++ }
    private val today = LocalDate.of(2026, 11, 11)

    private val tx get() = TransactionRepository(db, onChanged)
    private val buy get() = BuyListRepository(db, onChanged)
    private val grocery get() = GroceryRepository(db, onChanged)
    private val bills get() = BillRepository(db, onChanged)
    private val cats get() = CategoryRepository(db, onChanged)

    @Before fun setUp() { db = MidasDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true) }
    @After fun tearDown() = db.close()

    private suspend fun catId(name: String) = db.categories().findByName(name)!!.id

    @Test fun `default categories are seeded on create`() = runTest {
        val all = cats.getAll()
        assertEquals(DefaultCategories.all.size, all.size)
        assertTrue(all.any { it.name == "Groceries" && it.type == TxType.EXPENSE })
        assertTrue(all.any { it.name == "Salary" && it.type == TxType.INCOME })
    }

    @Test fun `save, update and delete a transaction`() = runTest {
        val id = tx.save(Transaction(amountMinor = 25_000, type = TxType.EXPENSE, categoryId = catId("Transport"), title = "Taxi", date = today))
        assertEquals("Taxi", tx.get(id)!!.title)
        tx.save(tx.get(id)!!.copy(title = "Airport taxi", amountMinor = 30_000))
        assertEquals(30_000L, tx.get(id)!!.amountMinor)
        assertEquals(30_000L, tx.expenseInMonth(YearMonth.from(today)))
        tx.delete(id)
        assertNull(tx.get(id))
        assertEquals(3, changes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero amount is rejected`() = runTest {
        tx.save(Transaction(amountMinor = 0, type = TxType.EXPENSE, categoryId = null, title = "x", date = today))
    }

    @Test fun `deleting a category keeps its transactions as uncategorised`() = runTest {
        val cat = db.categories().findByName("Gifts")!!
        val id = tx.save(Transaction(amountMinor = 1_000, type = TxType.EXPENSE, categoryId = cat.id, title = "Present", date = today))
        cats.delete(cat)
        assertNull(tx.get(id)!!.categoryId)
    }

    @Test fun `month query only returns that month, newest first`() = runTest {
        tx.save(Transaction(amountMinor = 1, type = TxType.EXPENSE, categoryId = null, title = "oct", date = LocalDate.of(2026, 10, 31)))
        tx.save(Transaction(amountMinor = 2, type = TxType.EXPENSE, categoryId = null, title = "nov1", date = LocalDate.of(2026, 11, 1)))
        tx.save(Transaction(amountMinor = 3, type = TxType.EXPENSE, categoryId = null, title = "nov30", date = LocalDate.of(2026, 11, 30)))
        assertEquals(listOf("nov30", "nov1"), tx.inMonth(YearMonth.of(2026, 11)).first().map { it.title })
    }

    @Test fun `buying an item records the expense and undo removes it`() = runTest {
        val id = buy.save(BuyItem(name = "Headphones", estimatedPriceMinor = 500_000, categoryId = catId("Shopping")))
        val txId = buy.markPurchased(id, 449_900, today)
        assertNotNull(txId)
        val item = buy.items.first().single()
        assertTrue(item.purchased)
        assertEquals(449_900L, item.actualPriceMinor)
        val spend = tx.get(txId!!)!!
        assertEquals(TxSource.BUY_LIST, spend.source)
        assertEquals("Headphones", spend.title)
        assertEquals(catId("Shopping"), spend.categoryId)

        // Marking it again doesn't double count.
        assertEquals(txId, buy.markPurchased(id, 1, today))
        assertEquals(1, tx.getAll().size)

        buy.undoPurchase(id)
        assertFalse(buy.items.first().single().purchased)
        assertNull(tx.get(txId))
    }

    @Test fun `clear purchased only removes bought items`() = runTest {
        val a = buy.save(BuyItem(name = "A", estimatedPriceMinor = 100))
        buy.save(BuyItem(name = "B", estimatedPriceMinor = 100))
        buy.markPurchased(a, 100, today)
        buy.clearPurchased()
        assertEquals(listOf("B"), buy.items.first().map { it.name })
    }

    @Test fun `finishing a grocery week logs history, an expense, and resets the list`() = runTest {
        val milk = grocery.save(GroceryItem(name = "Milk", quantity = 2.0, unit = "l", unitPriceMinor = 6_000, section = "Dairy & Eggs"))
        grocery.save(GroceryItem(name = "Rice", quantity = 5.0, unit = "kg", unitPriceMinor = 7_000, section = "Pantry"))
        grocery.save(GroceryItem(name = "Birthday cake", quantity = 1.0, unitPriceMinor = 80_000, staple = false))
        grocery.setChecked(milk, true)

        val week = grocery.finishWeek(today, actualSpentMinor = 120_000, groceriesCategoryId = catId("Groceries"))
        assertEquals(LocalDate.of(2026, 11, 9), week.weekStart)
        assertEquals(127_000L, week.estimatedMinor)
        assertEquals(120_000L, week.actualMinor)
        assertEquals(3, week.itemCount)

        val logged = tx.get(week.transactionId!!)!!
        assertEquals(TxSource.GROCERY, logged.source)
        assertEquals(120_000L, logged.amountMinor)

        val left = grocery.getItems()
        assertEquals(listOf("Milk", "Rice"), left.map { it.name }.sorted())
        assertTrue(left.none { it.checked })
        assertEquals(1, grocery.weeks.first().size)
    }

    @Test fun `finishing a week without logging still keeps history`() = runTest {
        val id = grocery.save(GroceryItem(name = "Eggs", unitPriceMinor = 8_400))
        grocery.setChecked(id, true)
        val week = grocery.finishWeek(today, actualSpentMinor = null, groceriesCategoryId = null)
        assertNull(week.transactionId)
        assertEquals(8_400L, week.actualMinor) // falls back to what was in the cart
        assertTrue(tx.getAll().isEmpty())
    }

    @Test fun `quantity buttons step by unit and never go below one step`() = runTest {
        val id = grocery.save(GroceryItem(name = "Apples", quantity = 0.5, unit = "kg"))
        grocery.adjustQuantity(id, +1)
        assertEquals(0.75, grocery.getItems().single().quantity, 1e-9)
        repeat(5) { grocery.adjustQuantity(id, -1) }
        assertEquals(0.25, grocery.getItems().single().quantity, 1e-9)
        grocery.toggle(id)
        assertTrue(grocery.getItems().single().checked)
    }

    @Test fun `paying a bill records it and moves to the next due date`() = runTest {
        val id = bills.save(
            Bill(name = "Rent", amountMinor = 1_800_000, categoryId = catId("Rent"), frequency = Frequency.MONTHLY, nextDue = LocalDate.of(2026, 1, 31)),
        )
        val txId = bills.markPaid(id, LocalDate.of(2026, 1, 30))!!
        val bill = bills.get(id)!!
        assertEquals(LocalDate.of(2026, 2, 28), bill.nextDue)
        assertEquals(LocalDate.of(2026, 1, 30), bill.lastPaidOn)
        val paid = tx.get(txId)!!
        assertEquals(TxSource.BILL, paid.source)
        assertEquals(id, paid.billId)
        assertEquals(PaymentMethod.BANK, paid.paymentMethod)

        // Anchor day keeps the bill on the 31st after a short month.
        bills.markPaid(id, LocalDate.of(2026, 2, 28), amountMinor = 1_850_000)
        assertEquals(LocalDate.of(2026, 3, 31), bills.get(id)!!.nextDue)
        assertEquals(listOf(1_850_000L, 1_800_000L), tx.getAll().map { it.amountMinor })
    }

    @Test fun `skipping a bill moves the date without a payment`() = runTest {
        val id = bills.save(Bill(name = "Gym", amountMinor = 150_000, frequency = Frequency.WEEKLY, nextDue = today))
        bills.skip(id)
        assertEquals(today.plusWeeks(1), bills.get(id)!!.nextDue)
        assertTrue(tx.getAll().isEmpty())
    }

    @Test fun `inactive bills are not returned as active`() = runTest {
        bills.save(Bill(name = "Old plan", amountMinor = 100, nextDue = today, active = false))
        bills.save(Bill(name = "Phone", amountMinor = 59_900, nextDue = today))
        assertEquals(listOf("Phone"), bills.getActive().map { it.name })
    }

    @Test fun `csv import matches categories by name and creates missing ones`() = runTest {
        val before = cats.getAll().size
        val n = tx.import(
            listOf(
                CsvTransaction(today, TxType.EXPENSE, 45_000, "transport", "Uber", "", PaymentMethod.UPI),
                CsvTransaction(today, TxType.EXPENSE, 99_900, "Hobbies", "Paints", "", PaymentMethod.CARD),
                CsvTransaction(today, TxType.EXPENSE, 10_000, "Hobbies", "Brushes", "", PaymentMethod.CARD),
            ),
        )
        assertEquals(3, n)
        assertEquals(before + 1, cats.getAll().size)
        val all = tx.getAll()
        assertEquals(catId("Transport"), all.first { it.title == "Uber" }.categoryId)
        assertEquals(1, all.filter { it.title != "Uber" }.map { it.categoryId }.distinct().size)
    }

    @Test fun `category budgets are stored and never negative`() = runTest {
        val id = catId("Food & Dining")
        cats.setBudget(id, 800_000)
        assertEquals(800_000L, db.categories().get(id)!!.monthlyBudgetMinor)
        cats.setBudget(id, -5)
        assertEquals(0L, db.categories().get(id)!!.monthlyBudgetMinor)
    }
}
