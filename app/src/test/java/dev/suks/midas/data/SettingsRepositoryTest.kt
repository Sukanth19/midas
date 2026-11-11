package dev.suks.midas.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.suks.midas.domain.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After fun tearDown() = scope.cancel()

    private fun repo(onChanged: ChangeListener = {}) = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("settings.preferences_pb").also { it.delete() } },
        onChanged,
    )

    @Test fun `defaults`() = runTest {
        val s = repo().current()
        assertEquals("INR", s.currency)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertFalse(s.dynamicColor)
        assertEquals(0L, s.monthlyBudgetMinor)
        assertTrue(s.billReminders)
    }

    @Test fun `writes persist and notify`() = runTest {
        var changes = 0
        val r = repo { changes++ }
        r.setCurrency("USD")
        r.setTheme(ThemeMode.DARK)
        r.setDynamicColor(true)
        r.setMonthlyBudget(4_000_000)
        r.setBillReminders(false)
        val s = r.current()
        assertEquals(Settings("USD", ThemeMode.DARK, true, 4_000_000, false), s)
        assertEquals(5, changes)
    }

    @Test fun `negative budget is clamped`() = runTest {
        val r = repo()
        r.setMonthlyBudget(-100)
        assertEquals(0L, r.current().monthlyBudgetMinor)
    }
}
