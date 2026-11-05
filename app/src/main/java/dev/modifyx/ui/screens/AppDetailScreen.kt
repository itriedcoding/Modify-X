package dev.modifyx.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.core.PrivilegeMode
import dev.modifyx.data.AppEntry
import dev.modifyx.data.DebloatAction
import dev.modifyx.data.BloatCatalog
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.AppIcon
import dev.modifyx.ui.components.Badge
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.KeyValueRow
import dev.modifyx.ui.components.MonoBlock
import dev.modifyx.ui.components.RiskBadge
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.components.BusyDialog
import dev.modifyx.ui.formatBytes
import dev.modifyx.ui.formatDateTime

@Composable
fun AppDetailScreen(
    vm: MainViewModel,
    packageName: String,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val entry = remember(vm.snapshot, packageName) { vm.entryFor(packageName) }
    val details = vm.details
    var confirm by remember { mutableStateOf<DebloatAction?>(null) }
    var permissionsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) { vm.openDetails(packageName) }
    DisposableEffect(packageName) { onDispose { vm.closeDetails() } }

    val busy = vm.busy
    if (busy != null) BusyDialog(busy.title, busy.done, busy.total, busy.current)

    if (entry == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val catalog = remember(packageName) { BloatCatalog.find(packageName) }
    val blocked = vm.blockReason(entry, settings)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            AppIcon(vm.repository, entry.packageName, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (entry.removedForUser) {
                Badge("removed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            if (entry.disabled) {
                Badge("disabled", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            if (entry.isSystem) {
                Badge("system", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.suspended) {
                Badge("suspended", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            catalog?.let { RiskBadge(it.risk) }
        }

        if (!privileges.canModify) {
            SectionCard(title = "Read-only mode") {
                Text(
                    "No privileged shell is available, so actions are disabled. Set up root or Shizuku from the Home tab.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (blocked != null) {
            SectionCard(title = "Action blocked") {
                Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        val enabled = privileges.canModify && blocked == null
        SectionCard(title = "Actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.removedForUser) {
                    Button(
                        onClick = { vm.runSingle(DebloatAction.RESTORE_FOR_USER, entry) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("Restore") }
                } else {
                    Button(
                        onClick = { confirm = DebloatAction.UNINSTALL_FOR_USER },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("Uninstall") }
                }
                if (entry.disabled) {
                    OutlinedButton(
                        onClick = { vm.runSingle(DebloatAction.ENABLE, entry) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("Enable") }
                } else {
                    OutlinedButton(
                        onClick = { vm.runSingle(DebloatAction.DISABLE, entry) },
                        enabled = enabled && entry.installedForUser,
                        modifier = Modifier.weight(1f),
                    ) { Text("Disable") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.runSingle(DebloatAction.FORCE_STOP, entry) },
                    enabled = enabled && entry.installedForUser,
                    modifier = Modifier.weight(1f),
                ) { Text("Force stop") }
                OutlinedButton(
                    onClick = { confirm = DebloatAction.CLEAR_DATA },
                    enabled = enabled && entry.installedForUser,
                    modifier = Modifier.weight(1f),
                ) { Text("Clear data") }
            }
            if (entry.isUserApp && entry.installedForUser) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirm = DebloatAction.UNINSTALL_FULL },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Uninstall completely", color = MaterialTheme.colorScheme.error) }
            }
        }

        SectionCard(title = "Package") {
            KeyValueRow("Version", entry.versionName?.let { "$it (${entry.versionCode})" } ?: "unknown")
            KeyValueRow("Target SDK", if (entry.targetSdk > 0) entry.targetSdk.toString() else "unknown")
            KeyValueRow("Min SDK", if (entry.minSdk > 0) entry.minSdk.toString() else "unknown")
            KeyValueRow("UID", entry.uid.toString())
            KeyValueRow("APK size", formatBytes(entry.apkBytes))
            KeyValueRow("Installed", formatDateTime(entry.firstInstallTime))
            KeyValueRow("Updated", formatDateTime(entry.lastUpdateTime))
            KeyValueRow("Installer", entry.installer ?: "unknown")
            KeyValueRow("Launchable", if (entry.launchable) "yes" else "no")
            KeyValueRow("Type", if (entry.isSystem) "system" else "user")
            KeyValueRow("State", if (entry.removedForUser) "removed for user 0" else if (entry.enabled) "enabled" else "disabled")
        }

        if (details == null) {
            SectionCard(title = "Details") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (entry.removedForUser) {
                            "This package is removed for user 0, so the system no longer exposes its manifest. Restore it to inspect permissions and components."
                        } else {
                            "Reading package manifest…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (details != null) {
            val info = details
            SectionCard(title = "Storage") {
                if (info.dataBytes == null) {
                    Text(
                        "App data size needs root: the adb shell cannot read other apps' private directories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    KeyValueRow("Data", formatBytes(info.dataBytes))
                    KeyValueRow("Cache", formatBytes(info.cacheBytes ?: 0L))
                }
                KeyValueRow("Debuggable", if (info.isDebuggable) "yes" else "no")
                if (info.processes.isNotEmpty()) {
                    KeyValueRow("Processes", info.processes.joinToString(", "))
                }
            }

            SectionCard(
                title = "Components",
                subtitle = "Declared in the manifest",
            ) {
                KeyValueRow("Activities", info.activities.toString())
                KeyValueRow("Services", info.services.toString())
                KeyValueRow("Receivers", info.receivers.toString())
                KeyValueRow("Providers", info.providers.toString())
            }

            SectionCard(title = "Permissions (${info.permissions.size})") {
                if (info.permissions.isEmpty()) {
                    Text("No permissions requested.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val shown = if (permissionsExpanded) info.permissions else info.permissions.take(6)
                    shown.forEach { permission ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    permission.name.removePrefix("android.permission."),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            if (permission.dangerous) {
                                Badge(
                                    "dangerous",
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Badge(
                                if (permission.granted) "granted" else "denied",
                                if (permission.granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                if (permission.granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (info.permissions.size > 6) {
                        OutlinedButton(onClick = { permissionsExpanded = !permissionsExpanded }) {
                            Icon(
                                if (permissionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (permissionsExpanded) "Show less" else "Show all ${info.permissions.size}")
                        }
                    }
                }
            }

            if (!info.signatureSha256.isNullOrBlank()) {
                SectionCard(title = "Signing certificate", subtitle = "SHA-256 of the signing key") {
                    SelectionContainer { MonoBlock(info.signatureSha256!!) }
                }
            }

            if (info.apkPaths.isNotEmpty()) {
                SectionCard(title = "APK paths") {
                    SelectionContainer {
                        MonoBlock(info.apkPaths.joinToString("\n"))
                    }
                }
            }
        }

        if (catalog != null) {
            SectionCard(title = "Catalog note") {
                Text(catalog.note, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    val action = confirm
    if (action != null) {
        ConfirmDialog(
            title = action.title,
            message = when (action) {
                DebloatAction.CLEAR_DATA -> "Delete all data and cache for ${entry.label}? This cannot be undone."
                DebloatAction.UNINSTALL_FULL -> "Uninstall ${entry.label} from the device? The APK will be gone; reinstall it from the store to get it back."
                else -> "Uninstall ${entry.label} for the current user? You can restore it from History."
            },
            confirmLabel = action.title,
            destructive = true,
            onConfirm = {
                confirm = null
                vm.runSingle(action, entry)
            },
            onDismiss = { confirm = null },
        )
    }
}
