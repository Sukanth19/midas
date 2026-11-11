package dev.suks.midas

import android.app.Application
import dev.suks.midas.reminders.BillReminderWorker
import dev.suks.midas.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MidasApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        AppContainer(this, onChanged = { WidgetUpdater.requestUpdate(this, appScope) })
    }

    override fun onCreate() {
        super.onCreate()
        BillReminderWorker.createChannel(this)
        BillReminderWorker.schedule(this)
        // Publish generated widget previews (Android 15+) so the picker shows the real design.
        appScope.launch { WidgetUpdater.publishPreviews(this@MidasApplication) }
    }
}
