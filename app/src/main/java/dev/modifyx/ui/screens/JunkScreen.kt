package dev.modifyx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.JunkFinding
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.Badge
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.components.FilePreview
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.formatBytes

@Composable
fun JunkScreen(vm: MainViewModel, onAddFolder: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan = vm.scan
    val selection = vm.junkSelection
    var confirm by remember { mutableStateOf(false) }

    val findings = scan?.junk.orEmpty()
    val grouped = remember(findings) { findings.groupBy { it.ruleLabel } }
    val selectedBytes = vm.junkBytesSelected()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Junk files",
            subtitle = "Matched by name and extension, never by guesswork",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (scan == null) {
            EmptyState(
                title = "No scan yet",
                message = if (vm.roots.isEmpty()) {
                    "Grant a folder first, then scan it."
                } else {
                    "Run a scan to see which files match the junk rules."
                },
                actionLabel = if (vm.roots.isEmpty()) "Choose a folder" else "Scan now",
                onAction = if (vm.roots.isEmpty()) onAddFolder else ({ vm.startScan() }),
            )
            return@Column
        }

        if (findings.isEmpty()) {
            EmptyState(
                title = "No junk found",
                message = "Nothing in the scanned folders matches the enabled rules. Rules can be changed in Settings.",
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${selection.size} of ${findings.size} selected · ${formatBytes(selectedBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.selectJunk(safeOnly = true) }) { Text("Safe") }
            TextButton(onClick = { vm.selectJunk(safeOnly = false) }) { Text("All") }
            TextButton(onClick = vm::clearJunkSelection) { Text("None") }
        }

        LazyColumn(Modifier.weight(1f)) {
            grouped.forEach { (label, items) ->
                item(key = "header-$label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${items.size} · ${formatBytes(items.sumOf { it.file.size })}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items, key = { it.file.uri }) { finding ->
                    JunkRow(
                        resolver = vm.resolver,
                        finding = finding,
                        checked = finding.file.uri in selection,
                        onToggle = { vm.toggleJunk(finding.file.uri) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${selection.size} file(s)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatBytes(selectedBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { if (settings.confirmDeletes) confirm = true else vm.cleanJunk() },
                    enabled = selection.isNotEmpty(),
                ) { Text("Delete") }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Delete junk files",
            message = "Permanently delete ${selection.size} file(s) and free ${formatBytes(selectedBytes)}?",
            confirmLabel = "Delete",
            destructive = true,
            warning = "Android's file picker grants real deletion, not a recycle bin. Deleted files cannot be recovered.",
            onConfirm = {
                confirm = false
                vm.cleanJunk()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun JunkRow(
    resolver: android.content.ContentResolver,
    finding: JunkFinding,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FilePreview(resolver, finding.file, size = 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                finding.file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                finding.file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            if (!finding.safe) {
                Badge(
                    "review",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatBytes(finding.file.size), style = MaterialTheme.typography.labelSmall)
    }
}
