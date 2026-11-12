package dev.suks.midas.ui.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.suks.midas.MidasApplication
import dev.suks.midas.WorkspaceManager
import dev.suks.midas.data.Workspace
import kotlinx.coroutines.launch

class WorkspacesViewModel(private val manager: WorkspaceManager) : ViewModel() {
    val workspaces = manager.registry.workspaces
    val activeId = manager.registry.activeId

    fun select(id: String) = manager.select(id)
    fun create(name: String) = viewModelScope.launch { manager.create(name, switchTo = true) }
    fun rename(id: String, name: String) = manager.rename(id, name)
    fun delete(id: String) = manager.delete(id)
}

@Composable
fun rememberWorkspaces(): WorkspacesViewModel {
    val app = LocalContext.current.applicationContext as MidasApplication
    return viewModel(key = "workspaces") { WorkspacesViewModel(app.workspaces) }
}

/** Small pill in the home top bar: which workspace is open, tap to switch. */
@Composable
fun WorkspaceChip(workspace: Workspace, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.testTag("workspace_chip"),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceAvatar(workspace, size = 26)
            Text(
                workspace.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 120.dp),
            )
            Icon(Icons.Rounded.UnfoldMore, contentDescription = "Switch workspace", modifier = Modifier.padding(start = 2.dp).size(18.dp))
        }
    }
}

@Composable
fun WorkspaceAvatar(workspace: Workspace, size: Int = 36) {
    Box(
        Modifier.size(size.dp).background(Color(workspace.color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(workspace.initial, color = Color.White, style = if (size >= 32) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSheet(vm: WorkspacesViewModel, onDismiss: () -> Unit) {
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WorkspaceSheetContent(
            workspaces = workspaces,
            activeId = activeId,
            onSelect = { vm.select(it); onDismiss() },
            onCreate = { vm.create(it); onDismiss() },
            onRename = { id, name -> vm.rename(id, name) },
            onDelete = { vm.delete(it) },
        )
    }
}

/** Stateless body of the switcher, so it can be tested without a bottom sheet. */
@Composable
fun WorkspaceSheetContent(
    workspaces: List<Workspace>,
    activeId: String,
    onSelect: (String) -> Unit = {},
    onCreate: (String) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Workspace?>(null) }
    var deleting by remember { mutableStateOf<Workspace?>(null) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
        Text("Workspaces", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            "Each one has its own spending, lists, bills, budget and currency.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn {
            items(workspaces, key = { it.id }) { ws ->
                val active = ws.id == activeId
                ListItem(
                    headlineContent = { Text(ws.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (active) ({ Text("Open now") }) else null,
                    leadingContent = { WorkspaceAvatar(ws) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            if (active) Icon(Icons.Rounded.Check, "Open", tint = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { editing = ws }, modifier = Modifier.testTag("edit_${ws.name}")) {
                                Icon(Icons.Rounded.Edit, "Edit ${ws.name}")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSelect(ws.id) }.testTag("workspace_${ws.name}"),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("New workspace") },
                    supportingContent = { Text("For a roommate, a friend, a trip…") },
                    leadingContent = {
                        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { creating = true }.testTag("new_workspace"),
                )
            }
        }
    }

    if (creating) {
        NameDialog(title = "New workspace", initial = "", confirm = "Create", onDismiss = { creating = false }) {
            creating = false
            onCreate(it)
        }
    }
    editing?.let { ws ->
        NameDialog(
            title = "Edit workspace",
            initial = ws.name,
            confirm = "Save",
            onDismiss = { editing = null },
            onDelete = if (ws.isDefault) null else ({ editing = null; deleting = ws }),
        ) {
            editing = null
            onRename(ws.id, it)
        }
    }
    deleting?.let { ws ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${ws.name}?") },
            text = { Text("Every transaction, list, bill and budget in this workspace is removed for good. Your other workspaces aren't touched.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDelete(ws.id) }, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val clean = Workspace.cleanName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name,
                { if (it.length <= Workspace.MAX_NAME_LENGTH) name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Roommate") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("workspace_name"),
            )
        },
        confirmButton = {
            TextButton(onClick = { clean?.let(onConfirm) }, enabled = clean != null, modifier = Modifier.testTag("workspace_confirm")) { Text(confirm) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("workspace_delete")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
