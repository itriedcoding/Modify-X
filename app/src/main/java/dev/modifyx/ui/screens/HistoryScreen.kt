package dev.modifyx.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.formatBytes
import dev.modifyx.ui.formatDateTime
import dev.modifyx.ui.formatDuration

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val records by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "History",
            subtitle = "${records.size} cleanup session(s) recorded on this device",
            onBack = onBack,
            trailing = {
                if (records.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear history")
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )

        if (records.isEmpty()) {
            EmptyState(
                title = "No cleanups yet",
                message = "When you delete something, the confirmed byte count and any refusals appear here.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(records, key = { it.id }) { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(record.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${record.deleted} deleted · ${record.failed} refused · ${formatDuration(record.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatDateTime(record.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatBytes(record.bytesFreed), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history",
            message = "Delete the local cleanup log? The files are already gone; only the record is removed.",
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
