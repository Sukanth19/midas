package dev.suks.midas.domain

/** A payment read from a bank SMS or a payment app's notification. */
data class ParsedPayment(
    val amountMinor: Long,
    val type: TxType,
    /** Who was paid (or who paid you), if the message says. */
    val merchant: String?,
    val method: PaymentMethod,
    /** Last digits of the account or card, if given. */
    val account: String?,
)

/**
 * Reads Indian bank SMS and UPI app alerts ("Rs.450.00 debited from A/c XX1234 to SWIGGY",
 * "You paid ₹200 to Rahul", "Rahul sent you ₹500"). It's deliberately cautious: OTPs, payment
 * requests, reminders, offers and failed payments are ignored, and anything without a clear
 * amount and direction returns null.
 */
object PaymentParser {

    private val ignore = listOf(
        "otp", "one time password", "one-time password", "verification code", "do not share",
        "has requested", "is requesting", "collect request", "payment request", "requested money",
        "will be debited", "will be auto-debited", "is due", "due on", "due by", "due date", "minimum amount due", "bill generated",
        "pre-approved", "preapproved", "apply now", "offer", "cashback of up to", "win ", "voucher",
        "failed", "declined", "unsuccessful", "could not be", "insufficient",
        "withdrawn at", "atm withdrawal", "cash withdrawal",
    )

    private val incomingPhrases = listOf("paid you", "sent you", "received from", "you received", "you've received", "you have received")
    private val creditWords = listOf("credited", "received", "deposited", "refund", "refunded", "reversed", "cashback of")
    private val debitWords = listOf("debited", "spent", "paid", "sent", "purchase", "payment of", "txn of", "transaction of", "transferred", "debit")

    private const val NUMBER = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val amountWithCurrency = Regex("""(?:₹|\brs\.?|\binr)\s*$NUMBER""", RegexOption.IGNORE_CASE)
    private val amountAfterVerb = Regex("""(?:debited|credited)\s+(?:by|with|for)\s+$NUMBER""", RegexOption.IGNORE_CASE)
    private val account = Regex("""(?:a/c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s*(?:in|with)?)?\s*[x*.]*\s*([0-9]{3,6})\b""", RegexOption.IGNORE_CASE)

    /** Words that end a name: "to SWIGGY on 12-11", "to Rahul Ref 1234", "at AMAZON via card". */
    private const val NAME = """([A-Za-z0-9@][A-Za-z0-9@._&' -]{1,60}?)"""
    private const val NAME_END = """(?=\s+(?:on|ref|refno|ref no|upi|via|using|from|avl|avbl|bal|a/c|txn|imps|neft|info|if|not)\b|[,;:(\n]|\.(?:\s|$)|\s*-\s|$)"""
    private val paidTo = Regex("""\b(?:trf to|transferred to|paid to|sent to|payment to|to vpa|to|at|towards)\s+$NAME$NAME_END""", RegexOption.IGNORE_CASE)
    private val paidBy = Regex("""\b(?:from vpa|by vpa|from|by)\s+$NAME$NAME_END""", RegexOption.IGNORE_CASE)
    private val senderFirst = Regex("""^$NAME\s+(?:paid|sent) you\b""", RegexOption.IGNORE_CASE)

    /**
     * [lenient] is for text the user shared on purpose ("Payment successful ₹450 to Swiggy"): nothing
     * is filtered out, and with no clear direction it's taken as an expense.
     */
    fun parse(text: String, currency: String = "INR", lenient: Boolean = false): ParsedPayment? {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val lower = clean.lowercase()
        if (!lenient && ignore.any { it in lower }) return null

        val amountText = (amountWithCurrency.find(clean) ?: amountAfterVerb.find(clean))?.groupValues?.get(1) ?: return null
        val amount = Money.parse(amountText.replace(",", ""), currency)?.takeIf { it > 0 } ?: return null

        val type = when {
            incomingPhrases.any { it in lower } -> TxType.INCOME
            else -> {
                val debitAt = debitWords.mapNotNull { w -> wordIndex(lower, w) }.minOrNull()
                val creditAt = creditWords.mapNotNull { w -> wordIndex(lower, w) }.minOrNull()
                when {
                    debitAt != null && (creditAt == null || debitAt < creditAt) -> TxType.EXPENSE
                    creditAt != null -> TxType.INCOME
                    lenient -> TxType.EXPENSE
                    else -> return null
                }
            }
        }

        val merchant = if (type == TxType.EXPENSE) {
            paidTo.findAll(clean).map { it.groupValues[1] }.firstOrNull { usableName(it) }
        } else {
            senderFirst.find(clean)?.groupValues?.get(1)?.takeIf { usableName(it) }
                ?: paidBy.findAll(clean).map { it.groupValues[1] }.firstOrNull { usableName(it) }
        }

        val method = when {
            "upi" in lower || "vpa" in lower || '@' in (merchant ?: "") -> PaymentMethod.UPI
            "card" in lower -> PaymentMethod.CARD
            "neft" in lower || "imps" in lower || "rtgs" in lower -> PaymentMethod.BANK
            else -> PaymentMethod.UPI.takeIf { "₹" in clean && merchant != null } ?: PaymentMethod.BANK
        }

        return ParsedPayment(amount, type, merchant?.let(::prettyName), method, account.find(clean)?.groupValues?.get(1))
    }

    /** Index of [word] as a whole word, so "paid" doesn't match inside "prepaid". */
    private fun wordIndex(text: String, word: String): Int? =
        Regex("""(?<![a-z])${Regex.escape(word)}(?![a-z])""").find(text)?.range?.first

    private val notNames = Regex("""^(?:your|you|my|a/c|ac|acct|account|card|bank|rs\.?|inr|₹|the|xx\d*|\**\d+|[x*]+\d+|\d[\d,./-]*)$""", RegexOption.IGNORE_CASE)

    private fun usableName(raw: String): Boolean {
        val name = raw.trim()
        if (name.length < 2 || notNames.matches(name)) return false
        if (name.lowercase().startsWith("a/c") || name.lowercase().startsWith("your ")) return false
        return name.any { it.isLetter() }
    }

    /** "SWIGGY" → "Swiggy", "swiggy.upi@axisbank" → "Swiggy Upi", "Rahul Kumar" stays. */
    fun prettyName(raw: String): String {
        var name = raw.trim().removePrefix("VPA ").removePrefix("vpa ").trim()
        if ('@' in name) name = name.substringBefore('@').replace(Regex("[._-]+"), " ").replace(Regex("\\d{6,}"), "").trim().ifEmpty { name }
        val shouting = name.none { it.isLowerCase() }
        return if (shouting || name.none { it.isUpperCase() }) {
            name.lowercase().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        } else name
    }
}
