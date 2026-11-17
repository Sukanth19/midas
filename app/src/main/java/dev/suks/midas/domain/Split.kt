package dev.suks.midas.domain

/** Someone in a shared workspace. In all split maths `null` stands for you, the phone's owner. */
typealias MemberId = Long?

/** A shared expense: [paidBy] paid the whole [amountMinor], to be split evenly between everyone. */
data class SharedExpense(val amountMinor: Long, val paidBy: MemberId)

/** Money handed over to square up: [from] paid [to]. */
data class Payment(val from: MemberId, val to: MemberId, val amountMinor: Long)

object Split {

    /**
     * Each member's balance: positive means they're owed money, negative means they owe.
     * Expenses are split evenly between [members]; leftover paise go one each to the first members.
     * Balances always add up to zero.
     */
    fun balances(members: List<MemberId>, expenses: List<SharedExpense>, settlements: List<Payment>): Map<MemberId, Long> {
        val out = members.associateWith { 0L }.toMutableMap()
        if (members.isEmpty()) return out
        for (e in expenses) {
            val share = e.amountMinor / members.size
            val leftover = (e.amountMinor % members.size).toInt()
            members.forEachIndexed { i, m -> out[m] = out.getValue(m) - share - if (i < leftover) 1 else 0 }
            out[e.paidBy] = (out[e.paidBy] ?: 0) + e.amountMinor
        }
        for (s in settlements) {
            out[s.from] = (out[s.from] ?: 0) + s.amountMinor
            out[s.to] = (out[s.to] ?: 0) - s.amountMinor
        }
        return out
    }

    /**
     * The fewest payments (greedy, largest debt to largest credit) that bring every balance to zero.
     */
    fun settleUp(balances: Map<MemberId, Long>): List<Payment> {
        val owed = balances.filter { it.value > 0 }.map { it.key to it.value }.toMutableList()
        val owing = balances.filter { it.value < 0 }.map { it.key to -it.value }.toMutableList()
        val out = mutableListOf<Payment>()
        while (owed.isNotEmpty() && owing.isNotEmpty()) {
            owed.sortByDescending { it.second }
            owing.sortByDescending { it.second }
            val (creditor, credit) = owed[0]
            val (debtor, debt) = owing[0]
            val amount = minOf(credit, debt)
            out += Payment(debtor, creditor, amount)
            if (credit == amount) owed.removeAt(0) else owed[0] = creditor to credit - amount
            if (debt == amount) owing.removeAt(0) else owing[0] = debtor to debt - amount
        }
        return out
    }

    /**
     * A settle-up message to paste into a group chat, e.g.
     * "Flat 4B · settle up / Shared spending ₹12,400 · ₹3,100 each / Rahul pays Ani ₹1,240".
     */
    fun summary(
        title: String, members: List<MemberId>, names: Map<MemberId, String>, expenses: List<SharedExpense>,
        payments: List<Payment>, currency: String,
    ): String {
        val total = expenses.sumOf { it.amountMinor }
        fun name(m: MemberId) = names[m] ?: "?"
        return buildString {
            appendLine("$title · settle up")
            if (total > 0 && members.isNotEmpty()) {
                appendLine("Shared spending ${Money.format(total, currency)} · ${Money.format(total / members.size, currency)} each")
            }
            appendLine()
            if (payments.isEmpty()) appendLine("All settled up ✅")
            payments.forEach { appendLine("${name(it.from)} pays ${name(it.to)} ${Money.format(it.amountMinor, currency)}") }
            appendLine()
            append("Sent from Midas")
        }
    }
}
