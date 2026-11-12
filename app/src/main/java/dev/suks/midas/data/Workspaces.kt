package dev.suks.midas.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * A separate set of books: its own transactions, categories, lists, bills, budget and currency.
 * Handy for tracking money for a roommate or a friend without mixing it into your own.
 */
@Serializable
data class Workspace(val id: String, val name: String, val color: Long) {
    val isDefault: Boolean get() = id == DEFAULT_ID
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    companion object {
        /** The original workspace. It keeps the pre-workspace file names so existing data carries over. */
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "Personal"
        const val MAX_NAME_LENGTH = 30

        fun databaseName(id: String) = if (id == DEFAULT_ID) "midas.db" else "midas-$id.db"
        fun settingsName(id: String) = if (id == DEFAULT_ID) "settings" else "settings-$id"

        val palette = listOf(
            0xFFC9A227, 0xFF1E88E5, 0xFF43A047, 0xFFE53935,
            0xFF8E24AA, 0xFFFB8C00, 0xFF00897B, 0xFF6D4C41,
        )

        /** Trims and shortens a user-typed name; null if nothing usable is left. */
        fun cleanName(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim().ifEmpty { null }
    }
}

/** Persists the list of workspaces and which one is open. Always contains the default workspace. */
class WorkspaceRegistry(
    private val prefs: SharedPreferences,
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "").take(12) },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private val _workspaces = MutableStateFlow(load())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _activeId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> _workspaces.value.any { it.id == id } } ?: Workspace.DEFAULT_ID,
    )
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    val active: Workspace get() = get(activeId.value) ?: workspaces.value.first()

    fun get(id: String): Workspace? = workspaces.value.firstOrNull { it.id == id }

    fun create(name: String): Workspace = synchronized(lock) {
        val clean = requireNotNull(Workspace.cleanName(name)) { "name must not be blank" }
        val existing = _workspaces.value
        var id = newId()
        while (existing.any { it.id == id } || id == Workspace.DEFAULT_ID) id = newId()
        val used = existing.map { it.color }.toSet()
        val color = Workspace.palette.firstOrNull { it !in used } ?: Workspace.palette[existing.size % Workspace.palette.size]
        Workspace(id, clean, color).also { save(existing + it) }
    }

    fun rename(id: String, name: String): Boolean = synchronized(lock) {
        val clean = Workspace.cleanName(name) ?: return false
        val list = _workspaces.value
        if (list.none { it.id == id }) return false
        save(list.map { if (it.id == id) it.copy(name = clean) else it })
        true
    }

    /** Removes a workspace from the list. The default one can't be removed. Returns false if nothing changed. */
    fun remove(id: String): Boolean = synchronized(lock) {
        if (id == Workspace.DEFAULT_ID) return false
        val list = _workspaces.value
        if (list.none { it.id == id }) return false
        if (_activeId.value == id) select(Workspace.DEFAULT_ID)
        save(list.filterNot { it.id == id })
        true
    }

    fun select(id: String): Boolean = synchronized(lock) {
        if (_workspaces.value.none { it.id == id }) return false
        prefs.edit { putString(KEY_ACTIVE, id) }
        _activeId.value = id
        true
    }

    private fun load(): List<Workspace> {
        val stored = prefs.getString(KEY_LIST, null)
            ?.let { runCatching { json.decodeFromString<List<Workspace>>(it) }.getOrNull() }
            .orEmpty()
            .distinctBy { it.id }
        val default = stored.firstOrNull { it.isDefault } ?: Workspace(Workspace.DEFAULT_ID, Workspace.DEFAULT_NAME, Workspace.palette[0])
        return listOf(default) + stored.filterNot { it.isDefault }
    }

    private fun save(list: List<Workspace>) {
        prefs.edit { putString(KEY_LIST, json.encodeToString(list)) }
        _workspaces.value = list
    }

    private companion object {
        const val KEY_LIST = "workspaces"
        const val KEY_ACTIVE = "active"
    }
}
