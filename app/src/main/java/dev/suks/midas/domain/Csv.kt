package dev.suks.midas.domain

import java.time.LocalDate

/** RFC 4180-style CSV: commas, double-quote escaping, quoted fields may hold commas and newlines. */
object Csv {

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\""
        else field

    fun write(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString(",") { escape(it) } } + "\n"

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var rowHasContent = false
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; rowHasContent = true }
                    ',' -> { row += field.toString(); field.clear(); rowHasContent = true }
                    '\r' -> {}
                    '\n' -> {
                        if (rowHasContent || field.isNotEmpty()) { row += field.toString(); rows += row }
                        row = mutableListOf(); field.clear(); rowHasContent = false
                    }
                    else -> { field.append(c); rowHasContent = true }
                }
            }
            i++
        }
        if (rowHasContent || field.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}

/** One exported transaction, flattened to names so the file is readable in a spreadsheet. */
data class CsvTransaction(
    val date: LocalDate,
    val type: TxType,
    val amountMinor: Long,
    val category: String,
    val title: String,
    val note: String,
    val paymentMethod: PaymentMethod,
)

object TransactionCsv {
    /** Excel adds a byte-order mark to UTF-8 CSVs. */
    private val BOM = Char(0xFEFF).toString()

    val header = listOf("date", "type", "amount", "category", "title", "note", "payment_method")

    fun export(items: List<CsvTransaction>, currencyCode: String): String =
        Csv.write(listOf(header) + items.map {
            listOf(
                it.date.toString(),
                it.type.name.lowercase(),
                Money.toDecimal(it.amountMinor, currencyCode).toPlainString(),
                it.category,
                it.title,
                it.note,
                it.paymentMethod.name.lowercase(),
            )
        })

    data class ImportResult(val items: List<CsvTransaction>, val skippedRows: Int)

    /** Lenient import: columns are found by header name, bad rows are counted and skipped. */
    fun import(text: String, currencyCode: String): ImportResult {
        val rows = Csv.parse(text.removePrefix(BOM))
        if (rows.isEmpty()) return ImportResult(emptyList(), 0)
        val head = rows.first().map { it.trim().lowercase() }
        fun col(name: String) = head.indexOf(name)
        val iDate = col("date"); val iType = col("type"); val iAmount = col("amount")
        val iCat = col("category"); val iTitle = col("title"); val iNote = col("note"); val iPay = col("payment_method")
        if (iDate < 0 || iAmount < 0) return ImportResult(emptyList(), rows.size - 1)
        var skipped = 0
        val items = rows.drop(1).mapNotNull { r ->
            fun get(i: Int) = if (i in r.indices) r[i].trim() else ""
            val date = runCatching { LocalDate.parse(get(iDate)) }.getOrNull()
            // Bank exports often sign the amount instead of having a type column; both are accepted.
            val amount = Money.parse(get(iAmount).removePrefix("-"), currencyCode)
            if (date == null || amount == null || amount == 0L) { skipped++; return@mapNotNull null }
            val type = if (get(iType).equals("income", ignoreCase = true)) TxType.INCOME else TxType.EXPENSE
            CsvTransaction(
                date = date,
                type = type,
                amountMinor = amount,
                category = get(iCat).ifBlank { "Other" },
                title = get(iTitle),
                note = get(iNote),
                paymentMethod = PaymentMethod.entries.firstOrNull { it.name.equals(get(iPay), true) } ?: PaymentMethod.OTHER,
            )
        }
        return ImportResult(items, skipped)
    }
}
