package dev.suks.midas.ui.settings

import android.Manifest
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FolderOpen
import dev.suks.midas.data.BackupFolder
import dev.suks.midas.data.BackupWorker
import androidx.core.net.toUri
import androidx.compose.material.icons.rounded.Restore
import dev.suks.midas.MidasApplication
import dev.suks.midas.data.BackupFile
import dev.suks.midas.data.BackupManager
import dev.suks.midas.domain.Dates
import androidx.compose.material3.AlertDialog
import java.time.Instant
import java.time.ZoneId
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Sms
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.suks.midas.capture.PaymentNotificationListener
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.BuildConfigInfo
import dev.suks.midas.data.Settings
import dev.suks.midas.domain.CsvTransaction
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.ThemeMode
import dev.suks.midas.domain.TransactionCsv
import dev.suks.midas.ui.lock.AppLock
import dev.suks.midas.ui.midasViewModel
import androidx.fragment.app.FragmentActivity
import dev.suks.midas.ui.stateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(private val c: AppContainer, private val backups: BackupManager) : ViewModel() {
    val settings = stateOf(c.settings.settings, Settings())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    /** A backup that has been read and is waiting for the user to confirm the restore. */
    private val _pendingRestore = MutableStateFlow<BackupFile?>(null)
    val pendingRestore = _pendingRestore.asStateFlow()

    fun backUp(uri: Uri, resolver: ContentResolver) = viewModelScope.launch {
        val ok = runCatching {
            val text = backups.export()
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("no stream") }
        }.isSuccess
        _message.value = if (ok) "Backup saved" else "Backup failed"
    }

    fun openBackup(uri: Uri, resolver: ContentResolver) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
        if (text == null) { _message.value = "Couldn't read that file"; return@launch }
        try {
            _pendingRestore.value = backups.read(text)
        } catch (e: IllegalArgumentException) {
            _message.value = e.message
        }
    }

    fun confirmRestore() {
        val backup = _pendingRestore.value ?: return
        _pendingRestore.value = null
        // Runs in the app scope: restoring may switch workspace, which closes this screen.
        backups.restoreInBackground(backup) { ok -> _message.value = if (ok) "Backup restored" else "Restore failed" }
    }

    fun cancelRestore() { _pendingRestore.value = null }

    fun setBackupFolder(context: Context, uri: Uri?) = viewModelScope.launch {
        val old = c.settings.current().backupFolder?.toUri()
        if (old != null && old != uri) runCatching { context.contentResolver.releasePersistableUriPermission(old, FOLDER_ACCESS) }
        if (uri == null) {
            BackupWorker.cancel(context)
            c.settings.setBackupFolder(null)
            return@launch
        }
        val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, FOLDER_ACCESS) }.isSuccess
        if (!ok) { _message.value = "Midas couldn't get access to that folder"; return@launch }
        c.settings.setBackupFolder(uri.toString())
        BackupWorker.schedule(context)
        _message.value = "Backing up to ${BackupFolder.label(uri)} every day"
    }

    fun setCurrency(code: String) = viewModelScope.launch { c.settings.setCurrency(code) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setDynamic(on: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(on) }
    fun setReminders(on: Boolean) = viewModelScope.launch { c.settings.setBillReminders(on) }
    fun setAppLock(on: Boolean) = viewModelScope.launch { c.settings.setAppLock(on) }
    fun setCaptureSms(on: Boolean) = viewModelScope.launch { c.settings.setCaptureSms(on) }
    fun showMessage(text: String) { _message.value = text }
    fun messageShown() { _message.value = null }

    fun export(uri: Uri, resolver: ContentResolver) = viewModelScope.launch {
        val s = c.settings.current()
        val cats = c.categories.getAll().associateBy { it.id }
        val rows = c.transactions.getAll().map {
            CsvTransaction(it.date, it.type, it.amountMinor, it.categoryId?.let(cats::get)?.name ?: "Other", it.title, it.note, it.paymentMethod)
        }
        val ok = withContext(Dispatchers.IO) {
            runCatching { resolver.openOutputStream(uri)?.use { it.write(TransactionCsv.export(rows, s.currency).toByteArray()) } }.isSuccess
        }
        _message.value = if (ok) "Exported ${rows.size} transactions" else "Export failed"
    }

    fun import(uri: Uri, resolver: ContentResolver) = viewModelScope.launch {
        val s = c.settings.current()
        val text = withContext(Dispatchers.IO) { runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
        if (text == null) { _message.value = "Couldn't read that file"; return@launch }
        val result = TransactionCsv.import(text, s.currency)
        val n = c.transactions.import(result.items)
        _message.value = "Imported $n transactions" + if (result.skippedRows > 0) " · skipped ${result.skippedRows} invalid rows" else ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit, onSplit: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MidasApplication
    val vm = midasViewModel { SettingsViewModel(it, app.backups) }
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.export(it, context.contentResolver) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.backUp(it, context.contentResolver) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.openBackup(it, context.contentResolver) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.setBackupFolder(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.import(it, context.contentResolver) }
    }
    val toggleLock: (Boolean) -> Unit = { on ->
        val activity = context as? FragmentActivity
        when {
            activity == null -> {}
            !AppLock.available(activity) -> vm.showMessage("Set up a screen lock on your phone first")
            // Confirm it's really you both ways, so someone holding an unlocked phone can't switch it off.
            else -> AppLock.prompt(activity, if (on) "Turn on app lock" else "Turn off app lock") { ok -> if (ok) vm.setAppLock(on) }
        }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECEIVE_SMS] == true) vm.setCaptureSms(true)
        else vm.showMessage("Midas needs permission to read incoming SMS for this")
    }
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setReminders(granted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Group("General")
            Row(Icons.Rounded.Payments, "Currency", "${s.currency} · ${Money.symbol(s.currency)}") { pickCurrency = true }
            Row(Icons.Rounded.Category, "Categories", "Add, rename or recolour categories", onClick = onCategories)
            Row(Icons.Rounded.Groups, "Split with people", "Share this workspace's costs with roommates or friends", onClick = onSplit)

            Group("Appearance")
            Row(Icons.Rounded.DarkMode, "Theme", s.themeMode.name.lowercase().replaceFirstChar(Char::uppercase).let { if (it == "System") "Follow system" else it }) { pickTheme = true }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(Icons.Rounded.Palette, "Material You colours", "Use colours from your wallpaper instead of Midas gold", trailing = {
                    Switch(s.dynamicColor, vm::setDynamic)
                }) { vm.setDynamic(!s.dynamicColor) }
            }

            Group("Reminders")
            Row(Icons.Rounded.NotificationsActive, "Bill reminders", "Notify me before rent and bills are due", trailing = {
                Switch(s.billReminders, { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.setReminders(on)
                })
            }) {}

            Group("Auto-capture")
            val smsOn = s.captureSms && smsGranted
            val setSms: (Boolean) -> Unit = { on ->
                if (!on) vm.setCaptureSms(false)
                else smsPermission.launch(
                    // Also ask to post notifications on Android 13+, so new payments can say they're waiting.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)
                    else arrayOf(Manifest.permission.RECEIVE_SMS),
                )
            }
            Row(Icons.Rounded.Sms, "Read bank SMS", "Payments from bank messages wait on Home for you to add. OTPs and personal texts are ignored.", trailing = {
                Switch(smsOn, setSms, modifier = Modifier.testTag("capture_sms"))
            }) { setSms(!smsOn) }
            var listenerOn by remember { mutableStateOf(PaymentNotificationListener.hasAccess(context)) }
            LifecycleResumeEffect(Unit) {
                // Access is granted on a system screen, so check again on coming back.
                listenerOn = PaymentNotificationListener.hasAccess(context)
                onPauseOrDispose {}
            }
            Row(Icons.Rounded.NotificationsActive, "Read payment app alerts", "Google Pay, PhonePe, Paytm, BHIM, CRED and bank apps. Other apps' notifications are ignored.", trailing = {
                Switch(listenerOn, { openListenerSettings(context) }, modifier = Modifier.testTag("capture_notifications"))
            }) { openListenerSettings(context) }
            Text(
                "Captured payments go to your Personal workspace and never leave this phone. Works with rupee (INR) accounts.\n\n" +
                    "If Android says it's a restricted setting (it does for apps installed from outside the Play Store), " +
                    "open App info, tap ⋮ › Allow restricted settings, then try again.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            TextButton(onClick = { openAppInfo(context) }, modifier = Modifier.padding(start = 12.dp)) { Text("Open App info") }

            Group("Privacy")
            Row(Icons.Rounded.Fingerprint, "App lock", "Ask for fingerprint, face or screen lock when opening Midas", trailing = {
                Switch(s.appLock, { on -> toggleLock(on) })
            }) { toggleLock(!s.appLock) }

            Group("Backup")
            Row(Icons.Rounded.Backup, "Back up now", "Every workspace, transaction, list and bill in one file") {
                backupLauncher.launch(BackupManager.fileName(LocalDate.now()))
            }
            val folder = s.backupFolder?.toUri()
            Row(
                Icons.Rounded.FolderOpen, "Automatic daily backup",
                when {
                    folder == null -> "Off. Pick a folder, e.g. one Google Drive or Syncthing syncs"
                    s.lastAutoBackup > 0 -> "To ${BackupFolder.label(folder)} · last ${Dates.shortDate(Instant.ofEpochMilli(s.lastAutoBackup).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())}"
                    else -> "To ${BackupFolder.label(folder)}"
                },
                trailing = {
                    Switch(folder != null, { on -> if (on) folderLauncher.launch(null) else vm.setBackupFolder(context, null) }, modifier = Modifier.testTag("auto_backup"))
                },
            ) { folderLauncher.launch(folder) }
            Row(Icons.Rounded.Restore, "Restore from backup", "Replaces everything in Midas with a backup file") {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
            }

            Group("Data")
            Row(Icons.Rounded.FileDownload, "Export to CSV", "Every transaction, readable in any spreadsheet") { exportLauncher.launch("midas-${LocalDate.now()}.csv") }
            Row(Icons.Rounded.FileUpload, "Import from CSV", "Columns: date, type, amount, category, title, note") { importLauncher.launch(arrayOf("text/*", "application/csv", "text/comma-separated-values")) }

            Group("About")
            Row(Icons.Rounded.Savings, "Midas", "Your data stays on this phone. No accounts, no tracking.") {}
            Row(Icons.Rounded.Info, "Version", BuildConfigInfo.versionName(context)) {}
        }
    }

    pendingRestore?.let { b ->
        val txCount = b.workspaces.sumOf { it.transactions.size }
        val made = Instant.ofEpochMilli(b.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Made ${Dates.shortDate(made, LocalDate.now())} · ${b.workspaces.size} workspace${if (b.workspaces.size == 1) "" else "s"}, " +
                        "$txCount transaction${if (txCount == 1) "" else "s"}.\n\nEverything in Midas now will be replaced by it.",
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmRestore, modifier = Modifier.testTag("confirm_restore")) { Text("Restore") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } },
        )
    }
    if (pickCurrency) {
        ChoiceDialog("Currency", Money.supportedCurrencies, s.currency, { "$it  ${Money.symbol(it)}" }, onDismiss = { pickCurrency = false }) { vm.setCurrency(it); pickCurrency = false }
    }
    if (pickTheme) {
        ChoiceDialog("Theme", ThemeMode.entries, s.themeMode, { if (it == ThemeMode.SYSTEM) "Follow system" else it.name.lowercase().replaceFirstChar(Char::uppercase) }, onDismiss = { pickTheme = false }) {
            vm.setTheme(it); pickTheme = false
        }
    }
}

@Composable
private fun Group(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Row(icon: ImageVector, title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.clickable { onPick(o) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(o == selected, { onPick(o) })
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Notification access is granted on a system screen; on Android 11+ it can open straight at Midas. */
private fun openListenerSettings(context: Context) {
    val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(SystemSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, PaymentNotificationListener.component(context).flattenToString())
    } else null
    val fallback = Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    runCatching { context.startActivity(direct ?: fallback) }.onFailure { runCatching { context.startActivity(fallback) } }
}

private fun openAppInfo(context: Context) {
    runCatching { context.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
}

private const val FOLDER_ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
