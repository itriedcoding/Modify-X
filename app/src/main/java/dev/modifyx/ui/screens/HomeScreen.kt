package dev.modifyx.ui.screens

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.ScanResult
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.InlineProgress
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.components.SectionAction
import dev.modifyx.ui.components.StatTile
import dev.modifyx.ui.components.StorageBar
import dev.modifyx.ui.formatAgo
import dev.modifyx.ui.formatBytes

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenJunk: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val scan = vm.scan
    val scanning = vm.scanning
    val progress = vm.progress
    val roots = vm.roots

    val storage = remember {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault(0L to 0L)
    }
    val usedFraction = if (storage.second > 0) (storage.second - storage.first).toFloat() / storage.second else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Modify-X",
            subtitle = "Storage cleaner · everything stays on this device",
            trailing = {
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.DateRange, contentDescription = "Cleanup history")
                }
            },
        )

        SectionCard(title = "Device storage") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatTile("free", formatBytes(storage.first), Modifier.weight(1f))
                StatTile("used", formatBytes(storage.second - storage.first), Modifier.weight(1f))
                StatTile("total", formatBytes(storage.second), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            StorageBar(
                usedFraction = usedFraction,
                label = "${(usedFraction * 100).toInt()}% of shared storage in use",
            )
        }

        SectionCard(
            title = "Folders",
            subtitle = if (roots.isEmpty()) {
                "Nothing is scannable until you grant a folder."
            } else {
                "${roots.size} folder(s) granted through Android's file picker"
            },
        ) {
            roots.forEach { root ->
                Text(
                    "· ${root.label}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (roots.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionAction(label = "Choose a folder", onClick = onAddFolder, primary = true)
            } else {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddFolder, modifier = Modifier.weight(1f)) { Text("Add folder") }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("Manage") }
                }
            }
        }

        SectionCard(title = "Scan") {
            if (scanning) {
                InlineProgress(progress?.current ?: "Scanning…")
                Spacer(Modifier.height(8.dp))
                Text(
                    "${progress?.files ?: 0} files · ${formatBytes(progress?.bytes ?: 0L)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::cancelScan, modifier = Modifier.fillMaxWidth()) { Text("Stop scan") }
            } else {
                Button(
                    onClick = vm::startScan,
                    enabled = roots.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (scan == null) "Scan my folders" else "Scan again") }
                if (scan != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Last scan ${formatAgo(scan.scannedAt)} · ${scan.files.size} files · ${formatBytes(scan.bytesScanned)}" +
                            if (scan.cancelled) " · partial" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (roots.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add a folder first — Modify-X has no storage permission and can only see what you hand it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (scan != null && !scanning) {
            Results(
                scan = scan,
                largeThreshold = settings.largeFileBytes,
                onOpenJunk = onOpenJunk,
                onOpenDuplicates = onOpenDuplicates,
                onOpenExplore = onOpenExplore,
            )

            if (scan.warnings.isNotEmpty()) {
                SectionCard(title = "Scan notes") {
                    scan.warnings.forEach { warning ->
                        Text(
                            "· $warning",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (scan.skippedHidden > 0) {
                        Text(
                            "· ${scan.skippedHidden} hidden file(s) skipped. Enable hidden files in Settings to include them.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (scan.tooLargeToHash > 0) {
                        Text(
                            "· ${scan.tooLargeToHash} file(s) over 512 MB were left out of duplicate detection.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionCard(
            title = "Recent cleanups",
            subtitle = if (history.isEmpty()) "Nothing deleted yet." else null,
        ) {
            if (history.isEmpty()) {
                Text(
                    "Deletions are logged here with the exact amount of space the platform confirmed was freed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.take(3).forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${formatAgo(record.timestamp)} · ${record.deleted} deleted" +
                                    if (record.failed > 0) " · ${record.failed} refused" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatBytes(record.bytesFreed), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = onOpenHistory) { Text("View all") }
            }
        }

        SectionCard(title = "Privacy") {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Files are read and deleted on the device. The only network request is checking this project's GitHub release page for updates. There are no accounts, ads, or analytics.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            "Settings: duplicates " +
                (if (settings.minDuplicateMb == 0L) "of any size" else "≥ ${settings.minDuplicateMb} MB") +
                " · large files ≥ ${settings.largeFileMb} MB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Results(
    scan: ScanResult,
    largeThreshold: Long,
    onOpenJunk: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenExplore: () -> Unit,
) {
    val large = scan.largeFiles(largeThreshold).size
    SectionCard(title = "Found") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatTile("reclaimable", formatBytes(scan.reclaimable), Modifier.weight(1f))
            StatTile("junk", formatBytes(scan.junkBytes), Modifier.weight(1f))
            StatTile("duplicates", formatBytes(scan.duplicateBytes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        ResultRow(
            title = "Junk files",
            detail = "${scan.junk.size} matched · ${formatBytes(scan.junkBytes)}",
            onClick = onOpenJunk,
        )
        ResultRow(
            title = "Duplicate files",
            detail = "${scan.duplicates.size} group(s) · ${formatBytes(scan.duplicateBytes)} reclaimable",
            onClick = onOpenDuplicates,
        )
        ResultRow(
            title = "Large & empty",
            detail = "$large large file(s) · ${scan.emptyFolders.size} empty folder(s)",
            onClick = onOpenExplore,
        )
    }
}

@Composable
private fun ResultRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}
