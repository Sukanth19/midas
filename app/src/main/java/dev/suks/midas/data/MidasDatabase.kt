package dev.suks.midas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.suks.midas.domain.TxType
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromDate(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun toDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
}

@Database(
    entities = [Category::class, Transaction::class, BuyItem::class, GroceryItem::class, GroceryWeek::class, Bill::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MidasDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun buyItems(): BuyItemDao
    abstract fun grocery(): GroceryDao
    abstract fun bills(): BillDao

    companion object {
        fun build(context: Context, inMemory: Boolean = false, name: String = Workspace.databaseName(Workspace.DEFAULT_ID)): MidasDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, MidasDatabase::class.java).allowMainThreadQueries()
            } else {
                Room.databaseBuilder(context, MidasDatabase::class.java, name)
            }
            return builder.addCallback(SeedCallback).build()
        }
    }
}

/** Seeds default categories the first time the database is created. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultCategories.all.forEachIndexed { i, c ->
            db.execSQL(
                "INSERT INTO categories (name, icon, color, type, monthlyBudgetMinor, sortOrder) VALUES (?, ?, ?, ?, 0, ?)",
                arrayOf<Any>(c.name, c.icon, c.color, c.type.name, i),
            )
        }
    }
}

object DefaultCategories {
    val all = listOf(
        Category(name = "Groceries", icon = "cart", color = 0xFF2E7D32),
        Category(name = "Rent", icon = "home", color = 0xFF5E35B1),
        Category(name = "Food & Dining", icon = "restaurant", color = 0xFFEF6C00),
        Category(name = "Transport", icon = "car", color = 0xFF1565C0),
        Category(name = "Shopping", icon = "bag", color = 0xFFD81B60),
        Category(name = "Bills & Utilities", icon = "bolt", color = 0xFFF9A825),
        Category(name = "Subscriptions", icon = "subscriptions", color = 0xFF00838F),
        Category(name = "Health", icon = "health", color = 0xFFC62828),
        Category(name = "Entertainment", icon = "movie", color = 0xFF6A1B9A),
        Category(name = "Education", icon = "school", color = 0xFF283593),
        Category(name = "Travel", icon = "flight", color = 0xFF00695C),
        Category(name = "Personal care", icon = "spa", color = 0xFFAD1457),
        Category(name = "Gifts", icon = "gift", color = 0xFF8E24AA),
        Category(name = "Other", icon = "more", color = 0xFF546E7A),
        Category(name = "Salary", icon = "work", color = 0xFF2E7D32, type = TxType.INCOME),
        Category(name = "Freelance", icon = "laptop", color = 0xFF00897B, type = TxType.INCOME),
        Category(name = "Refunds", icon = "undo", color = 0xFF3949AB, type = TxType.INCOME),
        Category(name = "Other income", icon = "savings", color = 0xFF7CB342, type = TxType.INCOME),
    )
}
