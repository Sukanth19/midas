package dev.suks.midas

import android.app.Application
import dev.suks.midas.data.MidasDatabase
import dev.suks.midas.data.Workspace
import dev.suks.midas.data.WorkspaceRegistry
import dev.suks.midas.reminders.BillReminderWorker
import dev.suks.midas.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

class MidasApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val workspaces: WorkspaceManager by lazy {
        WorkspaceManager(
            registry = WorkspaceRegistry(getSharedPreferences("workspaces", MODE_PRIVATE)),
            scope = appScope,
            newContainer = { id ->
                AppContainer(
                    this,
                    database = MidasDatabase.build(this, name = Workspace.databaseName(id)),
                    onChanged = { WidgetUpdater.requestUpdate(this, appScope) },
                    workspaceId = id,
                )
            },
            deleteData = { id ->
                deleteDatabase(Workspace.databaseName(id))
                File(filesDir, "datastore/${Workspace.settingsName(id)}.preferences_pb").delete()
            },
        )
    }

    /** The open workspace's container. Widgets, quick actions and screens all follow it. */
    val container: AppContainer get() = workspaces.current

    override fun onCreate() {
        super.onCreate()
        BillReminderWorker.createChannel(this)
        BillReminderWorker.schedule(this)
        // Publish generated widget previews (Android 15+) so the picker shows the real design.
        appScope.launch { WidgetUpdater.publishPreviews(this@MidasApplication) }
        // Widgets show the open workspace, so redraw them whenever it changes.
        appScope.launch { workspaces.registry.activeId.drop(1).collect { WidgetUpdater.requestUpdate(this@MidasApplication, appScope) } }
    }
}
