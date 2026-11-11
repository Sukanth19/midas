package dev.suks.midas.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.suks.midas.AppContainer
import dev.suks.midas.data.Category
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.components.CategoryBadge
import dev.suks.midas.ui.components.CategoryIcons
import dev.suks.midas.ui.components.SectionHeader
import dev.suks.midas.ui.midasViewModel
import dev.suks.midas.ui.stateOf
import kotlinx.coroutines.launch

class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories = stateOf(c.categories.categories, emptyList())
    fun save(cat: Category) = viewModelScope.launch { if (cat.id == 0L) c.categories.add(cat) else c.categories.update(cat) }
    fun delete(cat: Category) = viewModelScope.launch { c.categories.delete(cat) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = midasViewModel { CategoriesViewModel(it) }
    val cats by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Categories") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Category(name = "", icon = "more", color = CategoryIcons.palette.first()) }) { Icon(Icons.Rounded.Add, "Add category") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp, start = 8.dp, end = 8.dp)) {
            TxType.entries.forEach { type ->
                item(key = type.name) { SectionHeader(if (type == TxType.EXPENSE) "Expenses" else "Income", Modifier.padding(horizontal = 12.dp)) }
                items(cats.filter { it.type == type }, key = { it.id }) { cat ->
                    ListItem(
                        headlineContent = { Text(cat.name) },
                        leadingContent = { CategoryBadge(cat.icon, cat.color, size = 40.dp) },
                        modifier = Modifier.clickable { editing = cat },
                    )
                }
            }
        }
    }
    editing?.let { cat ->
        CategoryDialog(cat, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null }, onDelete = { vm.delete(it); editing = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(cat: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit, onDelete: (Category) -> Unit) {
    var name by remember { mutableStateOf(cat.name) }
    var icon by remember { mutableStateOf(cat.icon) }
    var color by remember { mutableStateOf(cat.color) }
    var type by remember { mutableStateOf(cat.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cat.id == 0L) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (cat.id == 0L) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        TxType.entries.forEachIndexed { i, t ->
                            SegmentedButton(type == t, { type = t }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (t == TxType.EXPENSE) "Expense" else "Income") }
                        }
                    }
                }
                Text("Colour", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.palette.forEach { c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(c)).clickable { color = c }
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                Text("Icon", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.all.forEach { (k, v) -> FilterChip(icon == k, { icon = k }, { Icon(v, k, Modifier.size(18.dp)) }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(cat.copy(name = name.trim(), icon = icon, color = color, type = type)) }) { Text("Save") } },
        dismissButton = {
            if (cat.id != 0L) TextButton(onClick = { onDelete(cat) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
