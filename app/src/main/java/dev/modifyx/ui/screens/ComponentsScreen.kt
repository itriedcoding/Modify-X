package dev.modifyx.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.ComponentRow
import dev.modifyx.data.ComponentType
import dev.modifyx.ui.components.Badge
import dev.modifyx.ui.components.BusyDialog
import dev.modifyx.ui.components.EmptyState
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.MainViewModel

/**
 * Individual manifest components of one package. Disabling a receiver stops a broadcast
 * trigger without removing the whole app; the switch reflects what the platform reports.
 */
@Composable
fun ComponentsScreen(
    vm: MainViewModel,
    packageName: String,
    onBack: () -> Unit,
) {
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val components = vm.components
    val entry = remember(vm.snapshot, packageName) { vm.entryFor(packageName) }
    var filter by remember { mutableStateOf<ComponentType?>(null) }

    LaunchedEffect(packageName) { vm.loadComponents(packageName) }

    val visible = remember(components, filter) {
        if (filter == null) components else components.filter { it.type == filter }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = entry?.label ?: packageName,
            subtitle = packageName,
            onBack = onBack,
            trailing = {
                if (vm.componentsLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Components are the activities, services, receivers and providers the app declares. " +
                    "Disabling one stops that entry point while the package stays installed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!privileges.canModify) {
                SectionCard(title = "Read-only mode") {
                    Text(
                        "Changing components needs root or the ADB bridge. Counts below still come from the real manifest.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("All ${components.size}") },
                )
                ComponentType.entries.forEach { type ->
                    val count = components.count { it.type == type }
                    FilterChip(
                        selected = filter == type,
                        onClick = { filter = type },
                        label = { Text("${type.label}s $count") },
                    )
                }
            }
        }

        if (components.isEmpty() && !vm.componentsLoading) {
            EmptyState(
                title = "No components",
                message = if (privileges.canModify) {
                    "The package manager returned nothing for this package."
                } else {
                    "Component details need a privileged shell."
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                items(visible, key = { it.name }) { component ->
                    ComponentRowItem(
                        component = component,
                        enabled = privileges.canModify && vm.componentBusy == null,
                        onClick = { vm.toggleComponent(packageName, component.name, !component.enabled) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    vm.busy?.let { BusyDialog(it.title, it.done, it.total, it.current) }
}

@Composable
private fun ComponentRowItem(
    component: ComponentRow,
    enabled: Boolean,
    onClick: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                component.shortName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge(
                    component.type.label,
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (component.exported) {
                    Badge(
                        "exported",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Badge(
                    component.state.label,
                    if (component.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    if (component.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                )
                component.permission?.let { permission ->
                    Badge(
                        permission.substringAfterLast('.'),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(
            checked = component.enabled,
            onCheckedChange = { onClick(it) },
            enabled = enabled,
        )
    }
}
