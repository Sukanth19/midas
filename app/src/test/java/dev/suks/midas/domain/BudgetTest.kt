package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetTest {

    @Test fun `status thresholds`() {
        assertEquals(BudgetStatus.NONE, BudgetProgress(0, 500).status)
        assertEquals(BudgetStatus.ON_TRACK, BudgetProgress(1000, 799).status)
        assertEquals(BudgetStatus.WARNING, BudgetProgress(1000, 800).status)
        assertEquals(BudgetStatus.WARNING, BudgetProgress(1000, 1000).status)
        assertEquals(BudgetStatus.OVER, BudgetProgress(1000, 1001).status)
    }

    @Test fun `remaining and fraction`() {
        val p = BudgetProgress(40_000, 50_000)
        assertEquals(-10_000L, p.remainingMinor)
        assertEquals(1.25f, p.fraction, 0.0001f)
        assertEquals(0f, BudgetProgress(0, 10).fraction)
    }

    @Test fun `daily allowance splits what is left over remaining days including today`() {
        val nov = YearMonth.of(2026, 11) // 30 days
        // 11th: 20 days left including today
        assertEquals(1_000L, Budgets.dailyAllowance(BudgetProgress(30_000, 10_000), nov, LocalDate.of(2026, 11, 11)))
        assertEquals(0L, Budgets.dailyAllowance(BudgetProgress(30_000, 40_000), nov, LocalDate.of(2026, 11, 11)))
        assertNull(Budgets.dailyAllowance(BudgetProgress(0, 0), nov, LocalDate.of(2026, 11, 11)))
        assertNull(Budgets.dailyAllowance(BudgetProgress(30_000, 0), nov, LocalDate.of(2026, 12, 1)))
    }

    @Test fun `projection extrapolates current pace`() {
        val nov = YearMonth.of(2026, 11)
        assertEquals(30_000L, Budgets.projectedSpend(10_000, nov, LocalDate.of(2026, 11, 10)))
        assertEquals(12_345L, Budgets.projectedSpend(12_345, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 10)))
        assertEquals(0L, Budgets.projectedSpend(12_345, YearMonth.of(2026, 12), LocalDate.of(2026, 11, 10)))
    }
}
