package dev.suks.midas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.suks.midas.domain.CaptureStatus
import dev.suks.midas.domain.TxType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, name")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Insert suspend fun insert(category: Category): Long
    @Insert suspend fun insertAll(categories: List<Category>)
    @Update suspend fun update(category: Category)
    @Delete suspend fun delete(category: Category)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Transaction?

    /** The category last used for a payee, to pre-fill it next time. */
    @Query("SELECT categoryId FROM transactions WHERE title = :title COLLATE NOCASE AND type = :type AND categoryId IS NOT NULL ORDER BY date DESC, createdAt DESC LIMIT 1")
    suspend fun lastCategoryFor(title: String, type: TxType): Long?

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end")
    suspend fun expenseBetween(start: LocalDate, end: LocalDate): Long

    @Insert suspend fun insert(tx: Transaction): Long
    @Insert suspend fun insertAll(txs: List<Transaction>)
    @Update suspend fun update(tx: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BuyItemDao {
    @Query("SELECT * FROM buy_items ORDER BY purchased, CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, targetDate IS NULL, targetDate, createdAt DESC")
    fun observeAll(): Flow<List<BuyItem>>

    @Query("SELECT * FROM buy_items WHERE id = :id")
    suspend fun get(id: Long): BuyItem?

    @Upsert suspend fun upsert(item: BuyItem): Long

    @Query("DELETE FROM buy_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM buy_items WHERE purchased = 1")
    suspend fun clearPurchased()
}

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items ORDER BY section, sortOrder, name")
    fun observeItems(): Flow<List<GroceryItem>>

    @Query("SELECT * FROM grocery_items ORDER BY section, sortOrder, name")
    suspend fun getItems(): List<GroceryItem>

    @Query("SELECT * FROM grocery_items WHERE id = :id")
    suspend fun get(id: Long): GroceryItem?

    @Upsert suspend fun upsert(item: GroceryItem): Long

    @Query("UPDATE grocery_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE grocery_items SET quantity = :quantity WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Double)

    @Query("DELETE FROM grocery_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM grocery_items WHERE staple = 0")
    suspend fun deleteOneOffs()

    @Query("UPDATE grocery_items SET checked = 0")
    suspend fun uncheckAll()

    @Query("SELECT * FROM grocery_weeks ORDER BY weekStart DESC")
    fun observeWeeks(): Flow<List<GroceryWeek>>

    @Insert suspend fun insertWeek(week: GroceryWeek): Long

    @Query("SELECT * FROM grocery_prices ORDER BY date, id")
    fun observePrices(): Flow<List<GroceryPrice>>

    @Query("SELECT * FROM grocery_prices WHERE name = :name ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun latestPrice(name: String): GroceryPrice?

    @Insert suspend fun insertPrice(price: GroceryPrice): Long
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY active DESC, nextDue, name")
    fun observeAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE active = 1 ORDER BY nextDue, name")
    suspend fun getActive(): List<Bill>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): Bill?

    @Upsert suspend fun upsert(bill: Bill): Long

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captured_payments WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    fun observePending(): Flow<List<CapturedPayment>>

    @Query("SELECT * FROM captured_payments WHERE id = :id")
    suspend fun get(id: Long): CapturedPayment?

    @Query("SELECT * FROM captured_payments WHERE amountMinor = :amount AND type = :type AND receivedAt BETWEEN :from AND :to ORDER BY receivedAt LIMIT 1")
    suspend fun findSimilar(amount: Long, type: TxType, from: Long, to: Long): CapturedPayment?

    @Insert suspend fun insert(p: CapturedPayment): Long
    @Update suspend fun update(p: CapturedPayment)

    @Query("UPDATE captured_payments SET status = :status, transactionId = :txId WHERE id = :id")
    suspend fun setStatus(id: Long, status: CaptureStatus, txId: Long? = null)

    @Query("UPDATE captured_payments SET status = 'DISMISSED' WHERE status = 'PENDING'")
    suspend fun dismissAll()

    @Query("DELETE FROM captured_payments WHERE status != 'PENDING' AND receivedAt < :before")
    suspend fun pruneHandled(before: Long)
}

@Dao
interface SplitDao {
    @Query("SELECT * FROM people ORDER BY createdAt, id")
    fun observePeople(): Flow<List<Person>>

    @Query("SELECT * FROM people ORDER BY createdAt, id")
    suspend fun getPeople(): List<Person>

    @Insert suspend fun insertPerson(p: Person): Long
    @Update suspend fun updatePerson(p: Person)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    /** How many expenses or settle-ups mention this person. */
    @Query("SELECT (SELECT COUNT(*) FROM transactions WHERE paidById = :id) + (SELECT COUNT(*) FROM settlements WHERE fromId = :id OR toId = :id)")
    suspend fun usesOf(id: Long): Int

    @Query("SELECT * FROM transactions WHERE shared = 1 AND type = 'EXPENSE' ORDER BY date DESC, createdAt DESC")
    fun observeShared(): Flow<List<Transaction>>

    @Query("SELECT * FROM settlements ORDER BY date DESC, createdAt DESC")
    fun observeSettlements(): Flow<List<Settlement>>

    @Insert suspend fun insertSettlement(s: Settlement): Long

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)
}
