package dev.suks.midas.data

import androidx.room.withTransaction
import dev.suks.midas.domain.CsvTransaction
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.Recurrence
import dev.suks.midas.domain.TxSource
import dev.suks.midas.domain.TxType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/** Called after every write so home-screen widgets can refresh. */
typealias ChangeListener = suspend () -> Unit

class CategoryRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.categories()
    val categories: Flow<List<Category>> = dao.observeAll()

    suspend fun getAll() = dao.getAll()
    suspend fun add(category: Category): Long = dao.insert(category).also { onChanged() }
    suspend fun update(category: Category) { dao.update(category); onChanged() }
    suspend fun delete(category: Category) { dao.delete(category); onChanged() }
    suspend fun setBudget(categoryId: Long, budgetMinor: Long) {
        val c = dao.get(categoryId) ?: return
        dao.update(c.copy(monthlyBudgetMinor = budgetMinor.coerceAtLeast(0)))
        onChanged()
    }
}

class TransactionRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.transactions()

    val all: Flow<List<Transaction>> = dao.observeAll()
    fun recent(limit: Int) = dao.observeRecent(limit)
    fun inMonth(month: YearMonth) = Dates.month(month).let { dao.observeBetween(it.start, it.endInclusive) }

    suspend fun get(id: Long) = dao.get(id)
    suspend fun getAll() = dao.getAll()
    suspend fun expenseInMonth(month: YearMonth) = Dates.month(month).let { dao.expenseBetween(it.start, it.endInclusive) }

    suspend fun save(tx: Transaction): Long {
        require(tx.amountMinor > 0) { "amount must be positive" }
        val id = if (tx.id == 0L) dao.insert(tx) else { dao.update(tx); tx.id }
        onChanged()
        return id
    }

    suspend fun delete(id: Long) { dao.delete(id); onChanged() }

    /** Imports CSV rows, matching categories by name and creating any that are missing. */
    suspend fun import(items: List<CsvTransaction>): Int {
        if (items.isEmpty()) return 0
        db.withTransaction {
            val cats = db.categories()
            val cache = mutableMapOf<String, Long>()
            val txs = items.map { item ->
                val key = item.category.lowercase()
                val catId = cache.getOrPut(key) {
                    cats.findByName(item.category)?.id
                        ?: cats.insert(Category(name = item.category, icon = "more", color = 0xFF546E7A, type = item.type))
                }
                Transaction(
                    amountMinor = item.amountMinor, type = item.type, categoryId = catId,
                    title = item.title.ifBlank { item.category }, note = item.note, date = item.date,
                    paymentMethod = item.paymentMethod,
                )
            }
            db.transactions().insertAll(txs)
        }
        onChanged()
        return items.size
    }
}

class BuyListRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.buyItems()
    val items: Flow<List<BuyItem>> = dao.observeAll()

    suspend fun save(item: BuyItem): Long = dao.upsert(item).let { if (item.id != 0L) item.id else it }.also { onChanged() }
    suspend fun delete(id: Long) { dao.delete(id); onChanged() }
    suspend fun clearPurchased() { dao.clearPurchased(); onChanged() }

    /** Marks the item bought and records the spend as an expense transaction. */
    suspend fun markPurchased(id: Long, pricePaidMinor: Long, on: LocalDate): Long? {
        val txId = db.withTransaction {
            val item = dao.get(id) ?: return@withTransaction null
            if (item.purchased) return@withTransaction item.transactionId
            val txId = db.transactions().insert(
                Transaction(
                    amountMinor = pricePaidMinor, type = TxType.EXPENSE, categoryId = item.categoryId,
                    title = item.name, date = on, source = TxSource.BUY_LIST,
                ),
            )
            dao.upsert(item.copy(purchased = true, purchasedOn = on, actualPriceMinor = pricePaidMinor, transactionId = txId))
            txId
        }
        onChanged()
        return txId
    }

    /** Puts a bought item back on the list and removes the expense it created. */
    suspend fun undoPurchase(id: Long) {
        db.withTransaction {
            val item = dao.get(id) ?: return@withTransaction
            item.transactionId?.let { db.transactions().delete(it) }
            dao.upsert(item.copy(purchased = false, purchasedOn = null, actualPriceMinor = null, transactionId = null))
        }
        onChanged()
    }
}

class GroceryRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.grocery()
    val items: Flow<List<GroceryItem>> = dao.observeItems()
    val weeks: Flow<List<GroceryWeek>> = dao.observeWeeks()

    suspend fun getItems() = dao.getItems()
    suspend fun save(item: GroceryItem): Long = dao.upsert(item).let { if (item.id != 0L) item.id else it }.also { onChanged() }
    suspend fun delete(id: Long) { dao.delete(id); onChanged() }
    suspend fun setChecked(id: Long, checked: Boolean) { dao.setChecked(id, checked); onChanged() }
    suspend fun toggle(id: Long) { dao.get(id)?.let { dao.setChecked(id, !it.checked) }; onChanged() }

    suspend fun adjustQuantity(id: Long, direction: Int) {
        val item = dao.get(id) ?: return
        val step = GroceryMath.stepFor(item.unit)
        dao.setQuantity(id, (item.quantity + direction * step).coerceAtLeast(step))
        onChanged()
    }

    /**
     * Closes the current week: stores its totals in the history, optionally logs what was actually
     * spent as one Groceries expense, removes one-off items and unchecks the staples.
     */
    suspend fun finishWeek(today: LocalDate, actualSpentMinor: Long?, groceriesCategoryId: Long?): GroceryWeek {
        val week = db.withTransaction {
            val items = dao.getItems()
            val totals = GroceryMath.totals(items.map { it.toLine() })
            val actual = actualSpentMinor ?: totals.inCartMinor
            val txId = if (actual > 0 && actualSpentMinor != null) {
                db.transactions().insert(
                    Transaction(
                        amountMinor = actual, type = TxType.EXPENSE, categoryId = groceriesCategoryId,
                        title = "Weekly groceries", date = today, source = TxSource.GROCERY,
                    ),
                )
            } else null
            val week = GroceryWeek(
                weekStart = Dates.weekStart(today), estimatedMinor = totals.estimatedMinor,
                actualMinor = actual, itemCount = totals.itemCount, transactionId = txId,
            )
            val id = dao.insertWeek(week)
            dao.deleteOneOffs()
            dao.uncheckAll()
            week.copy(id = id)
        }
        onChanged()
        return week
    }
}

class BillRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.bills()
    val bills: Flow<List<Bill>> = dao.observeAll()

    suspend fun getActive() = dao.getActive()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun save(bill: Bill): Long {
        require(bill.amountMinor > 0) { "amount must be positive" }
        return dao.upsert(bill).let { if (bill.id != 0L) bill.id else it }.also { onChanged() }
    }
    suspend fun delete(id: Long) { dao.delete(id); onChanged() }

    /** Records a payment for the bill's current due date and moves it to the next one. */
    suspend fun markPaid(id: Long, paidOn: LocalDate, amountMinor: Long? = null): Long? {
        val txId = db.withTransaction {
            val bill = dao.get(id) ?: return@withTransaction null
            val txId = db.transactions().insert(
                Transaction(
                    amountMinor = amountMinor ?: bill.amountMinor, type = TxType.EXPENSE, categoryId = bill.categoryId,
                    title = bill.name, date = paidOn, paymentMethod = bill.paymentMethod,
                    source = TxSource.BILL, billId = bill.id,
                ),
            )
            dao.upsert(
                bill.copy(
                    nextDue = Recurrence.next(bill.nextDue, bill.frequency, bill.interval, bill.anchorDay),
                    lastPaidOn = paidOn,
                ),
            )
            txId
        }
        onChanged()
        return txId
    }

    /** Moves to the next due date without recording a payment. */
    suspend fun skip(id: Long) {
        val bill = dao.get(id) ?: return
        dao.upsert(bill.copy(nextDue = Recurrence.next(bill.nextDue, bill.frequency, bill.interval, bill.anchorDay)))
        onChanged()
    }
}
