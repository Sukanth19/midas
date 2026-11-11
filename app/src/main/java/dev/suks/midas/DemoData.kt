package dev.suks.midas

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.suks.midas.data.Bill
import dev.suks.midas.data.BuyItem
import dev.suks.midas.data.GroceryItem
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.Frequency
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.Priority
import dev.suks.midas.domain.TxType

/**
 * Fills an empty install with a believable month of data, for screenshots and trying the app.
 * Debug builds only: `adb shell am start -n dev.suks.midas/.MainActivity --ez seed_demo true`.
 */
object DemoData {
    fun allowed(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    suspend fun seed(c: AppContainer) {
        if (c.transactions.getAll().isNotEmpty()) return
        val today = c.today()
        val cats = c.categories.getAll().associateBy { it.name }
        fun cat(n: String) = cats[n]?.id
        c.settings.setMonthlyBudget(4_000_000)
        c.categories.setBudget(cat("Food & Dining")!!, 600_000)
        c.categories.setBudget(cat("Groceries")!!, 800_000)
        c.categories.setBudget(cat("Shopping")!!, 500_000)

        val spends = listOf(
            Triple(0, "Lunch at Truffles", "Food & Dining" to 42_000L),
            Triple(0, "Metro card top-up", "Transport" to 50_000L),
            Triple(1, "Weekly groceries", "Groceries" to 185_000L),
            Triple(2, "Movie night", "Entertainment" to 90_000L),
            Triple(3, "Uber to office", "Transport" to 31_000L),
            Triple(4, "New running shoes", "Shopping" to 349_900L),
            Triple(5, "Coffee with Ana", "Food & Dining" to 38_000L),
            Triple(6, "Pharmacy", "Health" to 64_000L),
            Triple(8, "Weekly groceries", "Groceries" to 172_000L),
            Triple(9, "Electricity", "Bills & Utilities" to 240_000L),
            Triple(10, "Rent", "Rent" to 1_800_000L),
            Triple(12, "Dinner out", "Food & Dining" to 128_000L),
            Triple(15, "Spotify", "Subscriptions" to 11_900L),
            Triple(20, "Weekly groceries", "Groceries" to 160_000L),
            Triple(24, "Birthday gift", "Gifts" to 250_000L),
            Triple(33, "Rent", "Rent" to 1_800_000L),
            Triple(38, "Flight to Goa", "Travel" to 920_000L),
            Triple(40, "Weekly groceries", "Groceries" to 175_000L),
            Triple(45, "Concert tickets", "Entertainment" to 300_000L),
            Triple(63, "Rent", "Rent" to 1_800_000L),
            Triple(70, "Laptop stand", "Shopping" to 220_000L),
            Triple(95, "Rent", "Rent" to 1_800_000L),
        )
        spends.forEach { (daysAgo, title, catAmount) ->
            val date = today.minusDays(daysAgo.toLong())
            c.transactions.save(Transaction(amountMinor = catAmount.second, type = TxType.EXPENSE, categoryId = cat(catAmount.first), title = title, date = date, paymentMethod = PaymentMethod.UPI))
        }
        for (m in 0..3) {
            c.transactions.save(Transaction(amountMinor = 9_500_000, type = TxType.INCOME, categoryId = cat("Salary"), title = "Salary", date = today.withDayOfMonth(1).minusMonths(m.toLong()), paymentMethod = PaymentMethod.BANK))
        }

        listOf(
            GroceryItem(name = "Milk", quantity = 2.0, unit = "l", unitPriceMinor = 6_000, section = "Dairy & Eggs", checked = true),
            GroceryItem(name = "Eggs", quantity = 1.0, unit = "dozen", unitPriceMinor = 8_400, section = "Dairy & Eggs"),
            GroceryItem(name = "Tomatoes", quantity = 1.0, unit = "kg", unitPriceMinor = 4_000, section = "Produce", checked = true),
            GroceryItem(name = "Bananas", quantity = 1.0, unit = "dozen", unitPriceMinor = 6_000, section = "Produce"),
            GroceryItem(name = "Spinach", quantity = 2.0, unit = "pack", unitPriceMinor = 3_000, section = "Produce"),
            GroceryItem(name = "Basmati rice", quantity = 5.0, unit = "kg", unitPriceMinor = 11_000, section = "Pantry"),
            GroceryItem(name = "Bread", quantity = 1.0, unit = "pcs", unitPriceMinor = 5_000, section = "Bakery"),
            GroceryItem(name = "Coffee beans", quantity = 1.0, unit = "pack", unitPriceMinor = 45_000, section = "Drinks"),
            GroceryItem(name = "Dish soap", quantity = 1.0, unit = "pcs", unitPriceMinor = 9_900, section = "Household", staple = false),
        ).forEach { c.grocery.save(it) }

        listOf(
            Bill(name = "Rent", amountMinor = 1_800_000, categoryId = cat("Rent"), icon = "home", nextDue = today.plusDays(19)),
            Bill(name = "Electricity", amountMinor = 240_000, categoryId = cat("Bills & Utilities"), icon = "bolt", nextDue = today.plusDays(2)),
            Bill(name = "Internet", amountMinor = 79_900, categoryId = cat("Bills & Utilities"), icon = "wifi", nextDue = today.plusDays(5)),
            Bill(name = "Phone", amountMinor = 59_900, categoryId = cat("Bills & Utilities"), icon = "phone", nextDue = today.minusDays(1)),
            Bill(name = "Netflix", amountMinor = 64_900, categoryId = cat("Subscriptions"), icon = "subscriptions", nextDue = today.plusDays(12)),
            Bill(name = "Car insurance", amountMinor = 1_450_000, categoryId = cat("Transport"), icon = "car", frequency = Frequency.YEARLY, nextDue = today.plusDays(80)),
        ).forEach { c.bills.save(it) }

        listOf(
            BuyItem(name = "Noise-cancelling headphones", estimatedPriceMinor = 2_499_000, priority = Priority.HIGH, categoryId = cat("Shopping"), targetDate = today.plusDays(20)),
            BuyItem(name = "Desk lamp", estimatedPriceMinor = 180_000, priority = Priority.MEDIUM, categoryId = cat("Shopping")),
            BuyItem(name = "Yoga mat", estimatedPriceMinor = 120_000, priority = Priority.LOW, categoryId = cat("Health")),
            BuyItem(name = "Cookbook", estimatedPriceMinor = 60_000, priority = Priority.LOW, categoryId = cat("Education")),
        ).forEach { c.buyList.save(it) }
    }
}
