package dev.suks.midas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.suks.midas.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class Settings(
    val currency: String = "INR",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Overall monthly spending budget in minor units; 0 = not set. */
    val monthlyBudgetMinor: Long = 0,
    val billReminders: Boolean = true,
    /** Ask for fingerprint, face or the screen lock when opening the app. */
    val appLock: Boolean = false,
    /** What to call you in settle-up messages. */
    val myName: String = "Me",
    /** Folder (a document tree URI) that gets a backup every day; null = off. */
    val backupFolder: String? = null,
    val lastAutoBackup: Long = 0,
)

/** One DataStore per file for the whole process (DataStore forbids two instances on one file). */
private object SettingsStores {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    fun get(context: Context, name: String): DataStore<Preferences> = synchronized(stores) {
        stores.getOrPut(name) {
            val app = context.applicationContext
            PreferenceDataStoreFactory.create { app.preferencesDataStoreFile(name) }
        }
    }
}

/**
 * App-wide preferences (theme, colours, reminders) live in [global]; money preferences (currency,
 * overall budget) live in [workspace] so every workspace has its own. For the default workspace
 * both are the same store, which is where everything lived before workspaces existed.
 */
class SettingsRepository(
    private val global: DataStore<Preferences>,
    private val workspace: DataStore<Preferences>,
    private val onChanged: ChangeListener = {},
) {
    constructor(store: DataStore<Preferences>, onChanged: ChangeListener = {}) : this(store, store, onChanged)

    companion object {
        fun forWorkspace(context: Context, workspaceId: String, onChanged: ChangeListener) = SettingsRepository(
            SettingsStores.get(context, Workspace.settingsName(Workspace.DEFAULT_ID)),
            SettingsStores.get(context, Workspace.settingsName(workspaceId)),
            onChanged,
        )
    }

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val budget = longPreferencesKey("monthly_budget")
        val reminders = booleanPreferencesKey("bill_reminders")
        val appLock = booleanPreferencesKey("app_lock")
        val myName = stringPreferencesKey("my_name")
        val backupFolder = stringPreferencesKey("backup_folder")
        val lastAutoBackup = longPreferencesKey("last_auto_backup")
    }

    val settings: Flow<Settings> = combine(global.data, workspace.data) { g, w ->
        Settings(
            currency = w[Keys.currency] ?: "INR",
            themeMode = g[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = g[Keys.dynamic] ?: false,
            monthlyBudgetMinor = w[Keys.budget] ?: 0,
            billReminders = g[Keys.reminders] ?: true,
            appLock = g[Keys.appLock] ?: false,
            myName = g[Keys.myName] ?: "Me",
            backupFolder = g[Keys.backupFolder],
            lastAutoBackup = g[Keys.lastAutoBackup] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setCurrency(code: String) = edit(workspace) { it[Keys.currency] = code }
    suspend fun setTheme(mode: ThemeMode) = edit(global) { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = edit(global) { it[Keys.dynamic] = on }
    suspend fun setMonthlyBudget(minor: Long) = edit(workspace) { it[Keys.budget] = minor.coerceAtLeast(0) }
    suspend fun setBillReminders(on: Boolean) = edit(global) { it[Keys.reminders] = on }
    suspend fun setAppLock(on: Boolean) = edit(global) { it[Keys.appLock] = on }
    suspend fun setBackupFolder(uri: String?) = edit(global) { if (uri == null) it.remove(Keys.backupFolder) else it[Keys.backupFolder] = uri }
    suspend fun setLastAutoBackup(at: Long) = edit(global) { it[Keys.lastAutoBackup] = at }
    suspend fun setMyName(name: String) = edit(global) { it[Keys.myName] = Workspace.cleanName(name) ?: "Me" }

    private suspend fun edit(store: DataStore<Preferences>, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
        onChanged()
    }
}
