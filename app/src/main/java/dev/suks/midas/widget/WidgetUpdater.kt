package dev.suks.midas.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetUpdater {
    private var pending: Job? = null

    /** Debounced refresh of every widget: a burst of writes causes a single redraw. */
    fun requestUpdate(context: Context, scope: CoroutineScope) {
        pending?.cancel()
        pending = scope.launch {
            delay(300)
            updateAll(context)
        }
    }

    suspend fun updateAll(context: Context) {
        runCatching {
            SpendingWidget().updateAll(context)
            GroceryWidget().updateAll(context)
            BillsWidget().updateAll(context)
            QuickAddWidget().updateAll(context)
        }.onFailure { Log.w("Midas", "widget update failed", it) }
    }

    /**
     * Android 15+ shows a live, generated preview in the widget picker. Publishing is rate-limited,
     * so it only happens once per app version.
     */
    suspend fun publishPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences("widget_previews", Context.MODE_PRIVATE)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(0L)
        if (prefs.getLong("published_for", -1) == version) return
        val manager = GlanceAppWidgetManager(context)
        val ok = runCatching {
            listOf(SpendingWidgetReceiver::class, GroceryWidgetReceiver::class, BillsWidgetReceiver::class, QuickAddWidgetReceiver::class)
                .all { manager.setWidgetPreviews(it) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }
        }.getOrDefault(false)
        if (ok) prefs.edit().putLong("published_for", version).apply()
    }
}
