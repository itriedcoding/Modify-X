package dev.modifyx.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import dev.modifyx.data.DuplicateGroup
import dev.modifyx.data.FileCategory
import dev.modifyx.data.ScannedFile
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.Badge
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.components.FilePreview
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.formatBytes

@Composable
fun DuplicatesScreen(vm: MainViewModel, onAddFolder: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan = vm.scan
    var category by remember { mutableStateOf<FileCategory?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val selection = vm.duplicateSelection

    val allGroups = scan?.duplicates.orEmpty()
    val categories = remember(allGroups) { allGroups.map { it.category }.distinct().sortedBy { it.ordinal } }
    val groups = remember(allGroups, category) {
        if (category == null) allGroups else allGroups.filter { it.category == category }
    }
    val selectedBytes = vm.duplicateBytesSelected()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Duplicates",
            subtitle = "Proven identical with a full SHA-256 hash",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (scan == null) {
            EmptyState(
                title = "No scan yet",
                message = if (vm.roots.isEmpty()) "Grant a folder first, then scan it." else "Run a scan to compare file contents.",
                actionLabel = if (vm.roots.isEmpty()) "Choose a folder" else "Scan now",
                onAction = if (vm.roots.isEmpty()) onAddFolder else vm::startScan,
            )
            return@Column
        }

        if (allGroups.isEmpty()) {
            EmptyState(
                title = "No duplicates",
                message = "No two files in the scanned folders have the same size and the same SHA-256 hash." +
                    " Lower the minimum size in Settings to compare smaller files.",
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
            categories.forEach { entry ->
                FilterChip(
                    selected = category == entry,
                    onClick = { category = entry },
                    label = { Text(entry.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${groups.size} group(s) · ${formatBytes(groups.sumOf { it.waste })} reclaimable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::selectDuplicateCopies) { Text("Select copies") }
            TextButton(onClick = vm::clearDuplicateSelection) { Text("None") }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(groups, key = { it.key }) { group ->
                GroupCard(
                    resolver = vm.resolver,
                    group = group,
                    selected = selection,
                    onToggle = vm::toggleDuplicate,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                    Text(
                        if (selection.size == 1) "1 copy selected" else "${selection.size} copies selected",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        formatBytes(selectedBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { if (settings.confirmDeletes) confirm = true else vm.cleanDuplicates() },
                    enabled = selection.isNotEmpty(),
                ) { Text("Delete") }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Delete duplicates",
            message = "Permanently delete ${selection.size} duplicate copy(ies) and free ${formatBytes(selectedBytes)}? The keeper of every group stays.",
            confirmLabel = "Delete",
            destructive = true,
            warning = "Check the previews first. Deletion is permanent and happens through Android's file provider.",
            onConfirm = {
                confirm = false
                vm.cleanDuplicates()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun GroupCard(
    resolver: android.content.ContentResolver,
    group: DuplicateGroup,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val keeper = group.keeper
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${group.files.size} copies · ${formatBytes(group.size)} each",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${formatBytes(group.waste)} reclaimable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Badge(
                group.category.label,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.files.take(4).forEach { file ->
                FilePreview(resolver, file, size = 52.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        group.files.forEach { file ->
            FileRow(
                file = file,
                isKeeper = file.uri == keeper.uri,
                checked = file.uri in selected,
                onToggle = { onToggle(file.uri) },
            )
        }
    }
}

@Composable
private fun FileRow(
    file: ScannedFile,
    isKeeper: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = !isKeeper)
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
        if (isKeeper) {
            Badge(
                "keep",
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall)
        }
    }
}
