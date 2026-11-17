package dev.suks.midas.domain

import java.time.LocalDate
import java.time.YearMonth

enum class BudgetStatus { NONE, ON_TRACK, WARNING, OVER }

data class BudgetProgress(
    val limitMinor: Long,
    val spentMinor: Long,
) {
    val remainingMinor: Long get() = limitMinor - spentMinor

    /** 0.0..n; can exceed 1 when over budget. 0 when there is no limit. */
    val fraction: Float get() = if (limitMinor <= 0) 0f else spentMinor.toFloat() / limitMinor

    val status: BudgetStatus
        get() = when {
            limitMinor <= 0 -> BudgetStatus.NONE
            spentMinor > limitMinor -> BudgetStatus.OVER
            fraction >= WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.ON_TRACK
        }

    companion object {
        const val WARNING_THRESHOLD = 0.8f
    }
}

/** Today's spending room once the bills still due this month have been set aside. */
data class SafeToSpend(
    /** What's left for today; negative once today's spending has gone past the daily share. */
    val todayMinor: Long,
    /** The even daily share for the rest of the month, today included. */
    val perDayMinor: Long,
    /** Unpaid bills still due this month, held back from the budget. */
    val reservedForBillsMinor: Long,
)

object Budgets {

    /**
     * How much can still be spent per day for the rest of [month] (today included) without going over.
     * Returns 0 once the budget is used up, and null outside the month or without a limit.
     */
    fun dailyAllowance(progress: BudgetProgress, month: YearMonth, today: LocalDate): Long? {
        if (progress.limitMinor <= 0 || YearMonth.from(today) != month) return null
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        return (progress.remainingMinor.coerceAtLeast(0) / daysLeft)
    }

    /**
     * "Safe to spend today": the budget left at the start of today, minus bills still due this month,
     * shared evenly over the days left, minus what has already gone out today.
     * Null without a budget or outside [month].
     */
    fun safeToSpend(
        limitMinor: Long, spentBeforeTodayMinor: Long, spentTodayMinor: Long, billsDueMinor: Long,
        month: YearMonth, today: LocalDate,
    ): SafeToSpend? {
        if (limitMinor <= 0 || YearMonth.from(today) != month) return null
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        val pool = (limitMinor - spentBeforeTodayMinor - billsDueMinor).coerceAtLeast(0)
        val perDay = pool / daysLeft
        return SafeToSpend(perDay - spentTodayMinor, perDay, billsDueMinor.coerceAtLeast(0))
    }

    /**
     * Straight-line projection of month-end spend from the pace so far.
     * In a past month the projection is just the actual total.
     */
    fun projectedSpend(spentMinor: Long, month: YearMonth, today: LocalDate): Long {
        val current = YearMonth.from(today)
        if (month.isBefore(current)) return spentMinor
        if (month.isAfter(current)) return 0
        val elapsed = today.dayOfMonth
        return Math.round(spentMinor.toDouble() / elapsed * month.lengthOfMonth())
    }
}
