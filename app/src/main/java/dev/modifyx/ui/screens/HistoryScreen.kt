package dev.modifyx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dev.modifyx.ui.components.Badge
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.formatDateTime

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val records by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${records.size} recorded action${if (records.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (records.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear history")
                }
            }
        }

        if (records.isEmpty()) {
            EmptyState(
                title = "No actions yet",
                message = "Uninstall, disable or restore a package and the exact command plus its verifiable result will appear here.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(records, key = { it.id }) { record ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (record.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (record.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${record.action.title} · ${record.packageName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatDateTime(record.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (record.undoAction != null) {
                            TextButton(onClick = { vm.undo(record) }) {
                                Text(record.undoAction!!.title.substringBefore(" "))
                            }
                        }
                    }
                    if (!record.success) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            record.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (record.verified && record.success) {
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Badge(
                                "verified",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history",
            message = "Delete the local log of every action? Undo information is lost with it.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClear = false
                vm.clearHistory()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
