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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.EmptyFolder
import dev.modifyx.data.FileCategory
import dev.modifyx.data.ScannedFile
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.components.FilePreview
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.formatBytes

@Composable
fun ExploreScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan = vm.scan
    var tab by remember { mutableIntStateOf(0) }
    var confirmLarge by remember { mutableIntStateOf(0) }
    var confirmFolders by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Explore",
            subtitle = "Large files, empty folders and what takes the most space",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )

        if (scan == null) {
            EmptyState(title = "No scan yet", message = "Run a scan from Home to fill this screen.")
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Large") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Empty folders") })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("By type") })
        }

        when (tab) {
            0 -> LargeTab(vm = vm, threshold = settings.largeFileBytes, onDelete = { confirmLarge++ })
            1 -> FoldersTab(vm = vm, onDelete = { confirmFolders++ })
            else -> TypesTab(vm = vm)
        }
    }

    if (confirmLarge > 0) {
        val bytes = vm.largeBytesSelected()
        ConfirmDialog(
            title = "Delete large files",
            message = "Permanently delete ${vm.largeSelection.size} file(s) and free ${formatBytes(bytes)}?",
            confirmLabel = "Delete",
            destructive = true,
            warning = "Large files are often videos or backups you still need. Review the list before deleting.",
            onConfirm = {
                confirmLarge = 0
                vm.cleanLargeFiles()
            },
            onDismiss = { confirmLarge = 0 },
        )
    }

    if (confirmFolders > 0) {
        ConfirmDialog(
            title = "Delete empty folders",
            message = "Remove ${vm.folderSelection.size} empty folder(s)? They contain no files.",
            confirmLabel = "Delete",
            destructive = false,
            onConfirm = {
                confirmFolders = 0
                vm.cleanEmptyFolders()
            },
            onDismiss = { confirmFolders = 0 },
        )
    }
}

@Composable
private fun LargeTab(vm: MainViewModel, threshold: Long, onDelete: () -> Unit) {
    val files = vm.scan?.largeFiles(threshold).orEmpty()
    if (files.isEmpty()) {
        EmptyState(
            title = "Nothing this large",
            message = "No scanned file is at least ${formatBytes(threshold)}. The threshold can be changed in Settings.",
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${files.size} file(s) ≥ ${formatBytes(threshold)} · ${formatBytes(vm.largeBytesSelected())} selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.selectLarge(threshold) }) { Text("All") }
            TextButton(onClick = vm::clearLargeSelection) { Text("None") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(files, key = { it.uri }) { file ->
                FileRow(
                    resolver = vm.resolver,
                    file = file,
                    checked = file.uri in vm.largeSelection,
                    onToggle = { vm.toggleLarge(file.uri) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            }
        }
        BottomAction(
            label = "${vm.largeSelection.size} selected",
            detail = formatBytes(vm.largeBytesSelected()),
            enabled = vm.largeSelection.isNotEmpty(),
            buttonLabel = "Delete",
            onClick = onDelete,
        )
    }
}

@Composable
private fun FoldersTab(vm: MainViewModel, onDelete: () -> Unit) {
    val folders = vm.emptyFolders()
    if (folders.isEmpty()) {
        EmptyState(title = "No empty folders", message = "Every scanned folder contains at least one file or a non-empty subfolder.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${folders.size} empty folder(s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::selectAllFolders) { Text("All") }
            TextButton(onClick = vm::clearFolderSelection) { Text("None") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(folders, key = { it.uri }) { folder ->
                FolderRow(
                    folder = folder,
                    checked = folder.uri in vm.folderSelection,
                    onToggle = { vm.toggleFolder(folder.uri) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            }
        }
        BottomAction(
            label = "${vm.folderSelection.size} folder(s) selected",
            detail = "contains no files",
            enabled = vm.folderSelection.isNotEmpty(),
            buttonLabel = "Remove",
            onClick = onDelete,
        )
    }
}

@Composable
private fun TypesTab(vm: MainViewModel) {
    val scan = vm.scan ?: return
    val byCategory = remember(scan) { scan.categoryBytes() }
    val total = byCategory.values.sum().coerceAtLeast(1L)
    LazyColumn(Modifier.fillMaxSize()) {
        items(FileCategory.entries.toList(), key = { it.name }) { category ->
            val bytes = byCategory[category] ?: 0L
            val count = scan.files.count { it.category == category }
            if (bytes == 0L) return@items
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "$count file(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { bytes.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    resolver: android.content.ContentResolver,
    file: ScannedFile,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FilePreview(resolver, file, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FolderRow(folder: EmptyFolder, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                folder.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BottomAction(
    label: String,
    detail: String,
    enabled: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClick, enabled = enabled) { Text(buttonLabel) }
        }
    }
}
