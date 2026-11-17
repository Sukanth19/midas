@file:UseSerializers(LocalDateSerializer::class)

package dev.suks.midas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.suks.midas.domain.CaptureStatus
import dev.suks.midas.domain.Frequency
import dev.suks.midas.domain.GroceryLine
import dev.suks.midas.domain.Payment
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.PricePoint
import dev.suks.midas.domain.Priority
import dev.suks.midas.domain.Recurrence
import dev.suks.midas.domain.TxPoint
import dev.suks.midas.domain.TxSource
import dev.suks.midas.domain.TxType
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
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

@Serializable
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
    /** In a workspace shared with people: who paid. Null means you did. */
    val paidById: Long? = null,
    /** Split evenly between you and everyone in the workspace. */
    @ColumnInfo(defaultValue = "0") val shared: Boolean = false,
) {
    fun toPoint() = TxPoint(date, amountMinor, type, categoryId)
}

/** Someone you share a workspace's costs with: a roommate, a friend on a trip. */
@Serializable
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Money handed over to square up. A null id means you. */
@Serializable
@Entity(tableName = "settlements")
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long?,
    val toId: Long?,
    val amountMinor: Long,
    val date: LocalDate,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toPayment() = Payment(fromId, toId, amountMinor)
}

@Serializable
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
@Serializable
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
@Serializable
@Entity(tableName = "grocery_weeks")
data class GroceryWeek(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: LocalDate,
    val estimatedMinor: Long,
    val actualMinor: Long,
    val itemCount: Int,
    val transactionId: Long? = null,
)

/** A grocery item's price per unit as it was on [date]; a new row is kept each time it changes. */
@Serializable
@Entity(tableName = "grocery_prices", indices = [Index("name")])
data class GroceryPrice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [GroceryMath.priceKey] of the item name, so "Milk" and "milk " share one history. */
    val name: String,
    val unit: String,
    val unitPriceMinor: Long,
    val date: LocalDate,
) {
    fun toPoint() = PricePoint(date, unitPriceMinor)
}

/** A payment read from a bank SMS or a payment app's notification, waiting to be added or dismissed. */
@Entity(tableName = "captured_payments", indices = [Index("status"), Index("receivedAt")])
data class CapturedPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val merchant: String?,
    val method: PaymentMethod,
    val account: String?,
    /** Where it came from, e.g. "SMS" or "Google Pay". */
    val source: String,
    /** The original message, so it can be checked when reviewing. */
    val text: String,
    val receivedAt: Long,
    val status: CaptureStatus = CaptureStatus.PENDING,
    val transactionId: Long? = null,
)

@Serializable
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
    /** Payee's UPI ID; when set the bill can be paid straight from a UPI app. Blank = none. */
    @ColumnInfo(defaultValue = "") val upiId: String = "",
) {
    /** UPI only works in rupees and needs the payee's ID. */
    fun canPayByUpi(currency: String): Boolean = currency == "INR" && upiId.isNotBlank()

    /** Unpaid amount still due from [today] to the end of the month, overdue included. */
    fun dueRestOfMonth(today: LocalDate): Long =
        if (!active) 0 else Recurrence.dueRestOfMonth(nextDue, frequency, interval, anchorDay, amountMinor, today)
}
