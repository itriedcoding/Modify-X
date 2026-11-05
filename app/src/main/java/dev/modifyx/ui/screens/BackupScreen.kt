package dev.modifyx.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.formatDateTime

@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val snapshots = vm.snapshots
    val preview = vm.importPreview
    var pendingDelete by remember { mutableStateOf<dev.modifyx.data.SnapshotFile?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Backup & restore", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "A snapshot records which packages are installed, removed or disabled, so the same debloat state can be rebuilt after a factory reset or on another device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Export") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("modifyx-backup-${System.currentTimeMillis()}.json") },
                    modifier = Modifier.weight(1f),
                ) { Text("Export JSON") }
                OutlinedButton(onClick = vm::saveLocalSnapshot, modifier = Modifier.weight(1f)) {
                    Text("Save locally")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The exported file contains package names and states only. No account data, no telemetry.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Import") {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Choose a backup file") }
            Spacer(Modifier.height(8.dp))
            Text(
                "You will see what the backup contains before anything is changed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Local snapshots",
            subtitle = if (snapshots.isEmpty()) "No snapshots saved on this device yet." else null,
        ) {
            snapshots.forEach { snapshot ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatDateTime(snapshot.createdAt),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${snapshot.removed} removed · ${snapshot.disabled} disabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.restoreSnapshot(snapshot) }) { Text("Open") }
                    IconButton(onClick = { pendingDelete = snapshot }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete snapshot")
                    }
                }
                HorizontalDivider()
            }
        }

        Text(
            "Restoring only reinstalls packages that the backup marks as removed. Re-applying removals is the destructive direction and is always confirmed.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (preview != null) {
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("Backup from ${preview.document.device}") },
            text = {
                Column {
                    Text(
                        "Created ${formatDateTime(preview.document.createdAt)} on Android ${preview.document.androidRelease}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${preview.document.entries.size} packages · ${preview.document.removedCount} removed · ${preview.document.disabledCount} disabled",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${preview.restorable} removed package(s) can be restored on this device. " +
                            "${preview.reRemovable} currently installed package(s) were removed in the backup.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(preview.document.entries.filter { it.isRemoved }.take(40)) { entry ->
                            Text(
                                "${entry.label} · ${entry.packageName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = vm::restoreImportedPackages,
                    enabled = preview.restorable > 0,
                ) { Text("Restore ${preview.restorable}") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = vm::applyImportedRemovals,
                        enabled = preview.reRemovable > 0,
                    ) { Text("Re-remove ${preview.reRemovable}") }
                    TextButton(onClick = vm::cancelImport) { Text("Cancel") }
                }
            },
        )
    }

    pendingDelete?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete snapshot") },
            text = { Text("Remove this local snapshot? The exported files elsewhere are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.deleteSnapshot(snapshot)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}
