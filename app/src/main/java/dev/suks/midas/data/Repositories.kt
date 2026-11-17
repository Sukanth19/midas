package dev.suks.midas.data

import androidx.room.withTransaction
import dev.suks.midas.domain.CsvTransaction
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.CaptureStatus
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.ParsedPayment
import dev.suks.midas.domain.PaymentMethod
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

    suspend fun lastCategoryFor(title: String, type: TxType): Long? = dao.lastCategoryFor(title.trim(), type)

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
    val prices: Flow<List<GroceryPrice>> = dao.observePrices()

    suspend fun getItems() = dao.getItems()

    /** Saves the item and, if its price changed, adds the new price to its history. */
    suspend fun save(item: GroceryItem, today: LocalDate = LocalDate.now()): Long {
        val id = db.withTransaction {
            recordPrice(item, today)
            dao.upsert(item).let { if (item.id != 0L) item.id else it }
        }
        onChanged()
        return id
    }

    private suspend fun recordPrice(item: GroceryItem, on: LocalDate) {
        if (item.unitPriceMinor <= 0 || item.name.isBlank()) return
        val key = GroceryMath.priceKey(item.name)
        val last = dao.latestPrice(key)
        if (last != null && last.unit == item.unit && last.unitPriceMinor == item.unitPriceMinor) return
        dao.insertPrice(GroceryPrice(name = key, unit = item.unit, unitPriceMinor = item.unitPriceMinor, date = on))
    }
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
            // Items priced before price history existed get their first point here.
            items.filter { it.checked }.forEach { recordPrice(it, today) }
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
    suspend fun markPaid(id: Long, paidOn: LocalDate, amountMinor: Long? = null, method: PaymentMethod? = null): Long? {
        val txId = db.withTransaction {
            val bill = dao.get(id) ?: return@withTransaction null
            val txId = db.transactions().insert(
                Transaction(
                    amountMinor = amountMinor ?: bill.amountMinor, type = TxType.EXPENSE, categoryId = bill.categoryId,
                    title = bill.name, date = paidOn, paymentMethod = method ?: bill.paymentMethod,
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

class CaptureRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.captures()
    val pending: Flow<List<CapturedPayment>> = dao.observePending()

    suspend fun get(id: Long) = dao.get(id)

    /**
     * Stores a payment read from an SMS or notification. The same payment often arrives twice (the
     * bank's SMS and the UPI app's alert), so one with the same amount and direction within
     * [DUPLICATE_WINDOW_MS] is treated as the same: it only fills in a missing payee. Returns the new
     * id, or null for a duplicate.
     */
    suspend fun capture(p: ParsedPayment, source: String, text: String, at: Long): Long? {
        val id = db.withTransaction {
            dao.pruneHandled(at - KEEP_HANDLED_MS)
            val twin = dao.findSimilar(p.amountMinor, p.type, at - DUPLICATE_WINDOW_MS, at + DUPLICATE_WINDOW_MS)
            if (twin != null) {
                if (twin.merchant == null && p.merchant != null) dao.update(twin.copy(merchant = p.merchant))
                null
            } else {
                dao.insert(
                    CapturedPayment(
                        amountMinor = p.amountMinor, type = p.type, merchant = p.merchant, method = p.method,
                        account = p.account, source = source, text = text.take(500), receivedAt = at,
                    ),
                )
            }
        }
        onChanged()
        return id
    }

    suspend fun markAdded(id: Long, transactionId: Long) { dao.setStatus(id, CaptureStatus.ADDED, transactionId); onChanged() }
    suspend fun dismiss(id: Long) { dao.setStatus(id, CaptureStatus.DISMISSED); onChanged() }
    suspend fun dismissAll() { dao.dismissAll(); onChanged() }

    companion object {
        const val DUPLICATE_WINDOW_MS = 15 * 60 * 1000L
        const val KEEP_HANDLED_MS = 60L * 24 * 60 * 60 * 1000
    }
}

/** People in a shared workspace, who paid for what, and settle-ups. */
class SplitRepository(private val db: MidasDatabase, private val onChanged: ChangeListener) {
    private val dao = db.split()
    val people: Flow<List<Person>> = dao.observePeople()
    val sharedExpenses: Flow<List<Transaction>> = dao.observeShared()
    val settlements: Flow<List<Settlement>> = dao.observeSettlements()

    suspend fun getPeople() = dao.getPeople()

    suspend fun addPerson(name: String): Long? {
        val clean = Workspace.cleanName(name) ?: return null
        return dao.insertPerson(Person(name = clean)).also { onChanged() }
    }

    suspend fun rename(person: Person, name: String) {
        val clean = Workspace.cleanName(name) ?: return
        dao.updatePerson(person.copy(name = clean))
        onChanged()
    }

    /** Removes someone who isn't in any expense or settle-up yet. Returns false if they are. */
    suspend fun removePerson(id: Long): Boolean {
        val removed = db.withTransaction {
            if (dao.usesOf(id) > 0) false else { dao.deletePerson(id); true }
        }
        if (removed) onChanged()
        return removed
    }

    suspend fun settle(fromId: Long?, toId: Long?, amountMinor: Long, on: LocalDate): Long {
        require(amountMinor > 0) { "amount must be positive" }
        require(fromId != toId) { "can't settle with yourself" }
        return dao.insertSettlement(Settlement(fromId = fromId, toId = toId, amountMinor = amountMinor, date = on)).also { onChanged() }
    }

    suspend fun deleteSettlement(id: Long) { dao.deleteSettlement(id); onChanged() }
}
