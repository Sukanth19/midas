package dev.suks.midas.ecosystem

import dev.suks.midas.data.Category
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.TxType

/** Decides which expenses count as gym / fitness / supplement spending for pupdr's reports. */
object FitnessSpend {
    private val categoryWords = Regex("""\b(gym|fitness|sports?|health|supplements?|workout|training)\b""", RegexOption.IGNORE_CASE)
    private val titleWords = Regex(
        """\b(gym|fitness|protein|whey|creatine|pre-?workout|supplements?|bcaa|shaker|dumbbells?|yoga|trainer|crossfit|cult(fit)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isFitness(title: String, categoryName: String?): Boolean =
        (categoryName != null && categoryWords.containsMatchIn(categoryName)) || titleWords.containsMatchIn(title)

    fun total(transactions: List<Transaction>, categories: List<Category>): Long {
        val names = categories.associate { it.id to it.name }
        return transactions.filter { it.type == TxType.EXPENSE && isFitness(it.title, it.categoryId?.let(names::get)) }.sumOf { it.amountMinor }
    }

    /** The category a supplement bought from pupdr should land in, if the user has one that fits. */
    fun categoryFor(categories: List<Category>): Long? =
        categories.firstOrNull { it.type == TxType.EXPENSE && categoryWords.containsMatchIn(it.name) }?.id
}
