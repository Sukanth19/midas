package dev.suks.midas.domain

enum class TxType { EXPENSE, INCOME }

/** Where a transaction came from; lets the UI show a small badge and lets us undo side effects. */
enum class TxSource { MANUAL, BILL, BUY_LIST, GROCERY }

enum class PaymentMethod(val label: String) {
    CASH("Cash"), CARD("Card"), UPI("UPI"), BANK("Bank"), OTHER("Other")
}

enum class Priority(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

enum class Frequency(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }

enum class ThemeMode { SYSTEM, LIGHT, DARK }
