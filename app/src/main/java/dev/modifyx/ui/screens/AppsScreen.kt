package dev.modifyx.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.AppEntry
import dev.modifyx.data.AppFilter
import dev.modifyx.data.BloatCatalog
import dev.modifyx.data.DebloatAction
import dev.modifyx.data.SortMode
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.AppIcon
import dev.modifyx.ui.components.Badge
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.components.RiskBadge
import dev.modifyx.ui.formatBytes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(
    vm: MainViewModel,
    onOpenDetail: (String) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps = remember(vm.snapshot, vm.query, settings.filter, settings.sortMode, settings.showCatalogOnly) {
        vm.visibleApps(settings)
    }
    var pendingAction by remember { mutableStateOf<DebloatAction?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }

    val selection = vm.selection
    val selectedApps = remember(selection, apps) { apps.filter { it.packageName in selection } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::updateQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            placeholder = { Text("Search name or package") },
            shape = RoundedCornerShape(12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppFilter.entries.forEach { filter ->
                FilterChip(
                    selected = settings.filter == filter,
                    onClick = { vm.setFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${apps.size} packages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { sortExpanded = true }) {
                    Text("Sort: ${settings.sortMode.label}")
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            trailingIcon = {
                                if (settings.sortMode == mode) Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                vm.setSort(mode)
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
            if (settings.filter == AppFilter.ALL) {
                TextButton(onClick = { vm.selectAll(apps) }) { Text("Select all") }
            }
        }

        if (selection.isNotEmpty()) {
            SelectionBar(
                vm = vm,
                selected = selectedApps,
                onAction = { pendingAction = it },
                onClear = vm::clearSelection,
            )
        }

        HorizontalDivider()

        if (vm.loading && apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (apps.isEmpty()) {
            EmptyState(
                title = "No packages match",
                message = if (vm.query.isNotBlank()) {
                    "Nothing matches \"${vm.query}\" in this filter."
                } else {
                    "This filter has no entries on this device."
                },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    PackageRow(
                        vm = vm,
                        app = app,
                        selected = app.packageName in selection,
                        selectionMode = selection.isNotEmpty(),
                        onClick = {
                            if (selection.isEmpty()) onOpenDetail(app.packageName) else vm.toggleSelection(app.packageName)
                        },
                        onLongClick = { vm.toggleSelection(app.packageName) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }

    val action = pendingAction
    if (action != null) {
        val targets = vm.applicableTargets(action, selectedApps, settings)
        ConfirmDialog(
            title = action.title,
            message = "Apply to ${targets.size} package${if (targets.size == 1) "" else "s"}?",
            confirmLabel = action.title,
            destructive = action == DebloatAction.UNINSTALL_FOR_USER || action == DebloatAction.UNINSTALL_FULL,
            warning = if (targets.size < selectedApps.size) {
                "${selectedApps.size - targets.size} selected package(s) are skipped because they are protected, risky, or already in that state."
            } else {
                null
            },
            onConfirm = {
                pendingAction = null
                vm.runBatch(action, targets)
            },
            onDismiss = { pendingAction = null },
        )
    }
}

@Composable
private fun SelectionBar(
    vm: MainViewModel,
    selected: List<AppEntry>,
    onAction: (DebloatAction) -> Unit,
    onClear: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onAction(DebloatAction.UNINSTALL_FOR_USER) }) { Text("Uninstall") }
                TextButton(onClick = { onAction(DebloatAction.DISABLE) }) { Text("Disable") }
                TextButton(onClick = { onAction(DebloatAction.RESTORE_FOR_USER) }) { Text("Restore") }
                TextButton(onClick = { onAction(DebloatAction.ENABLE) }) { Text("Enable") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackageRow(
    vm: MainViewModel,
    app: AppEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val catalog = remember(app.packageName) { BloatCatalog.find(app.packageName) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { vm.toggleSelection(app.packageName) })
            }
            AppIcon(vm.repository, app.packageName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (app.removedForUser) {
                        Badge("removed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    } else if (app.disabled) {
                        Badge("disabled", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    if (app.isSystem) {
                        Badge("system", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    catalog?.let { RiskBadge(it.risk) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatBytes(app.apkBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.versionName != null) {
                    Text(
                        app.versionName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
