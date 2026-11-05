package dev.modifyx.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.DebloatAction
import dev.modifyx.ui.components.PrivilegeCard
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.components.StatTile
import dev.modifyx.ui.components.UpdateSection
import dev.modifyx.ui.formatAgo
import dev.modifyx.ui.formatBytes
import dev.modifyx.ui.MainViewModel

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenApps: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val snapshot = vm.snapshot

    val installed = snapshot.apps.filter { it.installedForUser }
    val removed = snapshot.apps.filter { it.removedForUser }
    val disabled = snapshot.apps.filter { it.disabled }
    val reclaimable = (removed + disabled).sumOf { it.apkBytes }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Modify-X", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrivilegeCard(
            state = privileges,
            adbBusy = vm.adbBusy,
            adbMessage = vm.adbMessage,
            onDetect = vm::detectPrivileges,
            onConnectAdb = vm::connectAdb,
            onDisconnectAdb = vm::disconnectAdb,
            onDismissMessage = vm::dismissAdbMessage,
        )

        val available = vm.update.available
        if (available != null) {
            SectionCard(title = "Update available · ${available.version}") {
                UpdateSection(state = vm.update, onInstall = vm::downloadAndInstallUpdate)
            }
        }

        SectionCard(title = "Device overview") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatTile("installed", installed.size.toString(), Modifier.weight(1f))
                StatTile("system", installed.count { it.isSystem }.toString(), Modifier.weight(1f))
                StatTile("user", installed.count { it.isUserApp }.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatTile("removed", removed.size.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                StatTile("disabled", disabled.size.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatTile("reclaimed", formatBytes(reclaimable), Modifier.weight(1f))
            }
            if (snapshot.warning != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(snapshot.warning!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionCard(
            title = "Debloat",
            subtitle = "Package changes require root or the ADB bridge. Nothing is sent off the device.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenApps, modifier = Modifier.weight(1f)) { Text("All apps") }
                OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.weight(1f)) { Text("Presets") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBackup, modifier = Modifier.weight(1f)) { Text("Backup") }
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) { Text("History") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::refresh) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rescan packages")
            }
        }

        SectionCard(
            title = "Recent activity",
            subtitle = if (history.isEmpty()) "Nothing has been changed yet." else null,
        ) {
            if (history.isEmpty()) {
                Text(
                    "Actions you run are recorded here with their real exit status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.take(5).forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (record.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (record.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${record.action.title} · ${record.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatAgo(record.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TextButton(onClick = onOpenHistory) { Text("Open full history") }
            }
        }

        SectionCard(title = "Safety") {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Uninstall for user 0 removes an app for you without touching the system partition, and can be undone with Restore. Core packages are blocked by default. Take a snapshot before large changes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            "Removing packages can break features you rely on. Every action is reversible from History while the package data still exists.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
