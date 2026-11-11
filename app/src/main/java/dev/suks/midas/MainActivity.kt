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
        if (savedInstanceState == null) pendingAction.value = intent?.action
        val app = application as MidasApplication
        val container = app.container
        if (intent?.getBooleanExtra("seed_demo", false) == true && DemoData.allowed(this)) {
            app.appScope.launch { DemoData.seed(container) }
        }
        setContent {
            val settings by container.settings.settings.collectAsState(initial = Settings())
            MidasTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                MidasApp(
                    pendingAction = pendingAction.value,
                    onActionHandled = { pendingAction.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAction.value = intent.action
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "dev.suks.midas.ADD_EXPENSE"
        const val ACTION_OPEN_GROCERIES = "dev.suks.midas.OPEN_GROCERIES"
        const val ACTION_OPEN_BILLS = "dev.suks.midas.OPEN_BILLS"
    }
}
