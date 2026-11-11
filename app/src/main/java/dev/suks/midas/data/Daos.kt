package dev.suks.midas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
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
