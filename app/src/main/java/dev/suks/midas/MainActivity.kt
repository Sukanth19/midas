package dev.suks.midas

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.suks.midas.data.Settings
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.suks.midas.ui.MidasApp
import dev.suks.midas.ui.lock.AppLock
import dev.suks.midas.ui.lock.LockScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dev.suks.midas.ui.theme.MidasTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    /** Set by widgets / app shortcuts; MidasApp consumes it and resets it. */
    private val pendingAction = mutableStateOf<String?>(null)

    /** Lives in the Application so rotating the screen doesn't lock the app again. */
    private val appLock get() = (application as MidasApplication).appLock
    /** Null until the setting has loaded; the app stays covered until then. */
    private var lockEnabled: Boolean? = null
    private val showLock = mutableStateOf(true)
    private var prompting = false

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
        lifecycleScope.launch {
            app.container.settings.settings.map { it.appLock }.distinctUntilChanged().collect { on ->
                lockEnabled = on
                if (!on) appLock.unlock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!on)
                showLock.value = on && appLock.locked
                if (showLock.value && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) promptUnlock()
            }
        }
        setContent {
            // Theme and colours are app-wide, so it doesn't matter which workspace's settings they come from.
            val settings by remember { app.container.settings.settings }.collectAsState(initial = Settings())
            val workspaceId by app.workspaces.registry.activeId.collectAsState()
            MidasTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Box {
                    MidasApp(
                        workspaceKey = workspaceId,
                        pendingAction = pendingAction.value,
                        onActionHandled = { pendingAction.value = null },
                    )
                    if (showLock.value) LockScreen(onUnlock = ::promptUnlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lockEnabled?.let { showLock.value = appLock.onStart(it) }
    }

    override fun onResume() {
        super.onResume()
        if (showLock.value && lockEnabled == true) promptUnlock()
    }

    override fun onStop() {
        super.onStop()
        appLock.onStop()
    }

    private fun promptUnlock() {
        if (prompting) return
        prompting = true
        AppLock.prompt(this, "Unlock Midas") { ok ->
            prompting = false
            if (ok) { appLock.unlock(); showLock.value = false }
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
        const val ACTION_OPEN_HOME = "dev.suks.midas.OPEN_HOME"
    }
}
