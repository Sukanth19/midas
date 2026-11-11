package dev.suks.midas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.suks.midas.domain.Frequency
import dev.suks.midas.domain.GroceryLine
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.Priority
import dev.suks.midas.domain.TxPoint
import dev.suks.midas.domain.TxSource
import dev.suks.midas.domain.TxType
import java.time.LocalDate

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** ARGB colour used for the category dot and chart slice. */
    val color: Long,
    val type: TxType = TxType.EXPENSE,
    /** Monthly budget in minor units; 0 means no budget. */
    val monthlyBudgetMinor: Long = 0,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId"), Index("date"), Index("billId")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long?,
    val title: String,
    val note: String = "",
    val date: LocalDate,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val source: TxSource = TxSource.MANUAL,
    val billId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toPoint() = TxPoint(date, amountMinor, type, categoryId)
}

@Entity(tableName = "buy_items", indices = [Index("purchased")])
data class BuyItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val estimatedPriceMinor: Long,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val targetDate: LocalDate? = null,
    val note: String = "",
    val link: String = "",
    val purchased: Boolean = false,
    val purchasedOn: LocalDate? = null,
    val actualPriceMinor: Long? = null,
    val transactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A line on the reusable weekly grocery list. Unchecked every time a new week starts. */
@Entity(tableName = "grocery_items")
data class GroceryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val unitPriceMinor: Long = 0,
    val section: String = "Other",
    val checked: Boolean = false,
    /** Staples stay on the list after "start new week"; one-offs are removed. */
    val staple: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun toLine() = GroceryLine(quantity, unitPriceMinor, checked)
}

/** One finished grocery week, kept for the history and the cost trend chart. */
@Entity(tableName = "grocery_weeks")
data class GroceryWeek(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: LocalDate,
    val estimatedMinor: Long,
    val actualMinor: Long,
    val itemCount: Int,
    val transactionId: Long? = null,
)

@Entity(tableName = "bills", indices = [Index("nextDue")])
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val categoryId: Long? = null,
    val icon: String = "receipt",
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val nextDue: LocalDate,
    /** Day of month the bill was set up on, so monthly bills on the 31st don't drift. */
    val anchorDay: Int = nextDue.dayOfMonth,
    val remindDaysBefore: Int = 2,
    val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    val active: Boolean = true,
    val lastPaidOn: LocalDate? = null,
)
