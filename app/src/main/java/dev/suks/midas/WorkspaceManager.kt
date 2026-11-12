package dev.suks.midas

import dev.suks.midas.data.Workspace
import dev.suks.midas.data.WorkspaceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns one [AppContainer] per workspace, opened lazily and kept around so switching back is instant.
 * The UI, widgets and quick actions always talk to [current]; reminders look at every workspace.
 */
class WorkspaceManager(
    val registry: WorkspaceRegistry,
    private val scope: CoroutineScope,
    private val newContainer: (workspaceId: String) -> AppContainer,
    private val deleteData: (workspaceId: String) -> Unit,
    /** Screens of a workspace that was just left may still be winding down; close its database after this. */
    private val closeDelayMs: Long = 10_000,
) {
    private val containers = mutableMapOf<String, AppContainer>()

    val current: AppContainer get() = containerFor(registry.activeId.value)

    fun containerFor(id: String): AppContainer = synchronized(containers) {
        containers.getOrPut(id) { newContainer(id) }
    }

    /** Every workspace with its container, default first. */
    fun all(): List<Pair<Workspace, AppContainer>> = registry.workspaces.value.map { it to containerFor(it.id) }

    fun select(id: String): Boolean = registry.select(id)

    /** Creates a workspace that starts with the same currency as the one currently open. */
    suspend fun create(name: String, switchTo: Boolean = true): Workspace {
        val currency = current.settings.current().currency
        val ws = registry.create(name)
        containerFor(ws.id).settings.setCurrency(currency)
        if (switchTo) select(ws.id)
        return ws
    }

    fun rename(id: String, name: String): Boolean = registry.rename(id, name)

    /** Deletes a workspace and everything in it. The default workspace can't be deleted. */
    fun delete(id: String): Boolean {
        if (!registry.remove(id)) return false
        val container = synchronized(containers) { containers.remove(id) }
        deleteData(id)
        if (container != null) scope.launch {
            delay(closeDelayMs)
            runCatching { container.close() }
        }
        return true
    }
}
