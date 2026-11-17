package dev.suks.midas.domain

import java.math.RoundingMode
import java.net.URLEncoder

/** Builds UPI payment links (the `upi://pay` deep link every Indian payment app handles) and reads their replies. */
object Upi {
    enum class Result { SUCCESS, FAILED, UNKNOWN }

    private val idPattern = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$")

    /** A UPI ID looks like name@bank, e.g. "landlord@okhdfcbank" or "9876543210@ybl". */
    fun isValidId(id: String): Boolean = idPattern.matches(id.trim())

    /** The link that opens a UPI app with payee, amount and note filled in. Amounts are rupees with paise. */
    fun payUri(upiId: String, payeeName: String, amountMinor: Long, note: String): String {
        val amount = Money.toDecimal(amountMinor, "INR").setScale(2, RoundingMode.HALF_UP).toPlainString()
        return "upi://pay?" + listOf(
            "pa" to upiId.trim(),
            "pn" to payeeName.trim(),
            "am" to amount,
            "cu" to "INR",
            "tn" to note.trim().take(50),
        ).filter { it.second.isNotEmpty() }.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    }

    /**
     * Reads the reply a UPI app sends back, e.g. "txnId=AX12&responseCode=00&Status=SUCCESS&txnRef=…".
     * Apps disagree on case and some send nothing, so anything unclear is [Result.UNKNOWN].
     */
    fun parseResponse(response: String?): Result {
        if (response.isNullOrBlank()) return Result.UNKNOWN
        val fields = response.split("&").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim().lowercase() to part.substring(i + 1).trim().lowercase()
        }.toMap()
        return when (fields["status"]) {
            "success", "submitted" -> Result.SUCCESS
            "failure", "failed" -> Result.FAILED
            else -> Result.UNKNOWN
        }
    }

    // Some UPI apps reject "%40" in the payee ID, so "@" stays as it is.
    private fun encode(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20").replace("%40", "@")
}
