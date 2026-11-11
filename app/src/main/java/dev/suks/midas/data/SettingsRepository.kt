package dev.suks.midas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.suks.midas.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val currency: String = "INR",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Overall monthly spending budget in minor units; 0 = not set. */
    val monthlyBudgetMinor: Long = 0,
    val billReminders: Boolean = true,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val store: DataStore<Preferences>, private val onChanged: ChangeListener = {}) {

    constructor(context: Context, onChanged: ChangeListener) : this(context.settingsStore, onChanged)

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val budget = longPreferencesKey("monthly_budget")
        val reminders = booleanPreferencesKey("bill_reminders")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            currency = p[Keys.currency] ?: "INR",
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: false,
            monthlyBudgetMinor = p[Keys.budget] ?: 0,
            billReminders = p[Keys.reminders] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setCurrency(code: String) = edit { it[Keys.currency] = code }
    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = edit { it[Keys.dynamic] = on }
    suspend fun setMonthlyBudget(minor: Long) = edit { it[Keys.budget] = minor.coerceAtLeast(0) }
    suspend fun setBillReminders(on: Boolean) = edit { it[Keys.reminders] = on }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
        onChanged()
    }
}
