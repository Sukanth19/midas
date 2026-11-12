package dev.suks.midas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.suks.midas.data.Settings
import dev.suks.midas.ui.MidasApp
import dev.suks.midas.ui.theme.MidasTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set by widgets / app shortcuts; MidasApp consumes it and resets it. */
    private val pendingAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as MidasApplication
        if (savedInstanceState == null) {
            openWorkspaceFrom(intent)
            pendingAction.value = intent?.action
        }
        if (intent?.getBooleanExtra("seed_demo", false) == true && DemoData.allowed(this)) {
            val container = app.container
            app.appScope.launch { DemoData.seed(container) }
        }
        setContent {
            // Theme and colours are app-wide, so it doesn't matter which workspace's settings they come from.
            val settings by remember { app.container.settings.settings }.collectAsState(initial = Settings())
            val workspaceId by app.workspaces.registry.activeId.collectAsState()
            MidasTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                MidasApp(
                    workspaceKey = workspaceId,
                    pendingAction = pendingAction.value,
                    onActionHandled = { pendingAction.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openWorkspaceFrom(intent)
        pendingAction.value = intent.action
    }

    /** Bill reminders say which workspace they belong to; tapping one opens that workspace. */
    private fun openWorkspaceFrom(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_WORKSPACE_ID) ?: return
        (application as MidasApplication).workspaces.select(id)
    }

    companion object {
        const val EXTRA_WORKSPACE_ID = "dev.suks.midas.WORKSPACE_ID"
        const val ACTION_ADD_EXPENSE = "dev.suks.midas.ADD_EXPENSE"
        const val ACTION_OPEN_GROCERIES = "dev.suks.midas.OPEN_GROCERIES"
        const val ACTION_OPEN_BILLS = "dev.suks.midas.OPEN_BILLS"
    }
}
