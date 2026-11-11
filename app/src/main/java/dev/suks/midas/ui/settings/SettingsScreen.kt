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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
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
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = stateOf(c.settings.settings, Settings())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setCurrency(code: String) = viewModelScope.launch { c.settings.setCurrency(code) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setDynamic(on: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(on) }
    fun setReminders(on: Boolean) = viewModelScope.launch { c.settings.setBillReminders(on) }
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
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit) {
    val vm = midasViewModel { SettingsViewModel(it) }
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
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.import(it, context.contentResolver) }
    }
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

            Group("Data")
            Row(Icons.Rounded.FileDownload, "Export to CSV", "Every transaction, readable in any spreadsheet") { exportLauncher.launch("midas-${LocalDate.now()}.csv") }
            Row(Icons.Rounded.FileUpload, "Import from CSV", "Columns: date, type, amount, category, title, note") { importLauncher.launch(arrayOf("text/*", "application/csv", "text/comma-separated-values")) }

            Group("About")
            Row(Icons.Rounded.Savings, "Midas", "Your data stays on this phone. No accounts, no tracking.") {}
            Row(Icons.Rounded.Info, "Version", BuildConfigInfo.versionName(context)) {}
        }
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
