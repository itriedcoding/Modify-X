package dev.modifyx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.BloatCatalog
import dev.modifyx.data.ThemeId
import dev.modifyx.data.ThemeMode
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.theme.Palette

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenConsole: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val update = vm.update
    val context = LocalContext.current
    var showProtected by remember { mutableStateOf(false) }
    var showThemeEditor by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = "Theme") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = settings.themeId == ThemeId.DYNAMIC,
                    onClick = { vm.setThemeId(ThemeId.DYNAMIC) },
                    label = { Text("Wallpaper") },
                )
                FilterChip(
                    selected = settings.themeId == ThemeId.MATERIAL,
                    onClick = { vm.setThemeId(ThemeId.MATERIAL) },
                    label = { Text("Material") },
                )
                settings.customThemes.forEach { theme ->
                    FilterChip(
                        selected = settings.themeId == ThemeId.custom(theme.id),
                        onClick = { vm.setThemeId(ThemeId.custom(theme.id)) },
                        label = { Text(theme.name) },
                        leadingIcon = {
                            Box(Modifier.size(14.dp).background(Color(theme.seed), CircleShape))
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showThemeEditor = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Create theme")
            }

            if (settings.customThemes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                settings.customThemes.forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(18.dp).background(Color(theme.seed), CircleShape))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            theme.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.deleteCustomTheme(theme.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${theme.name}")
                        }
                    }
                }
            }

            ToggleRow(
                title = "AMOLED black",
                subtitle = "Pure black surfaces in dark mode",
                checked = settings.amoled,
                onCheckedChange = vm::setAmoled,
            )
            ToggleRow(
                title = "Wallpaper colours",
                subtitle = "Use Material You colours when the Wallpaper theme is selected",
                checked = settings.dynamicColor,
                onCheckedChange = vm::setDynamicColor,
            )
        }

        SectionCard(
            title = "Updates",
            subtitle = "Follows github.com/${"itriedcoding/Modify-X"}/releases; downloads are signature-checked before installing.",
        ) {
            Text(
                "Installed ${update.currentVersion.ifBlank { version }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "Check automatically",
                subtitle = "Once a day when the app opens",
                checked = settings.autoUpdateCheck,
                onCheckedChange = vm::setAutoUpdateCheck,
            )
            OutlinedButton(
                onClick = { vm.checkForUpdates(notify = true) },
                enabled = !update.checking && !update.downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (update.checking) "Checking…" else "Check for updates")
            }

            val info = update.available
            if (info != null) {
                Spacer(Modifier.height(10.dp))
                Text("Version ${info.version} available", style = MaterialTheme.typography.titleMedium)
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        info.notes.take(700),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = vm::downloadAndInstallUpdate,
                    enabled = !update.downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (update.downloading) "Downloading ${update.progress}%" else "Download & install")
                }
                if (update.downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { update.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            update.message?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Browsing") {
            ToggleRow(
                title = "Only known bloatware",
                subtitle = "Hide packages that are not in the curated catalog",
                checked = settings.showCatalogOnly,
                onCheckedChange = vm::setCatalogOnly,
            )
            Text(
                if (vm.usageAccessGranted) {
                    "Usage access is granted: the Unused filter and last-used times come from real usage stats."
                } else {
                    "Usage access is not granted, so last-used times and the Unused filter are unavailable."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::openUsageAccessSettings, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.usageAccessGranted) "Manage usage access" else "Grant usage access")
            }
        }

        SectionCard(
            title = "Safety",
            subtitle = "These guards are off by default because they allow changes that can break a device.",
        ) {
            ToggleRow(
                title = "Allow risky actions",
                subtitle = "Permit packages the catalog marks as risky (Play services, launchers, …)",
                checked = settings.allowRisky,
                onCheckedChange = vm::setAllowRisky,
            )
            ToggleRow(
                title = "Allow protected packages",
                subtitle = "Permit touching core Android packages. A mis-tap here can brick the ROM.",
                checked = settings.allowProtected,
                onCheckedChange = vm::setAllowProtected,
            )
            ToggleRow(
                title = "Confirm destructive actions",
                subtitle = "Ask before uninstalling or clearing data",
                checked = settings.confirmDestructive,
                onCheckedChange = vm::setConfirmDestructive,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showProtected = true }, modifier = Modifier.fillMaxWidth()) {
                Text("View protected packages (${BloatCatalog.protectedPackages.size})")
            }
        }

        SectionCard(title = "Tools") {
            OutlinedButton(onClick = onOpenConsole, modifier = Modifier.fillMaxWidth()) {
                Text("Shell console")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Backup & restore")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::detectPrivileges, modifier = Modifier.fillMaxWidth()) {
                Text("Re-detect privileges (${privileges.summary})")
            }
            ToggleRow(
                title = "Reconnect ADB on launch",
                subtitle = "Try adbd on port ${settings.adbPort} when the app starts",
                checked = settings.adbAutoConnect,
                onCheckedChange = vm::setAdbAutoConnect,
            )
        }

        SectionCard(title = "About") {
            Text("Modify-X $version", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "A local debloater. Package changes go through root or the built-in ADB bridge and every " +
                    "result is read back from the platform. The only network request is reading this project's " +
                    "public GitHub release page for updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Uninstall for user 0 uses `pm uninstall --user 0`; restore uses `pm install-existing --user 0`. " +
                    "Everything else is a thin, verified wrapper around platform commands.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showProtected) {
        AlertDialog(
            onDismissRequest = { showProtected = false },
            title = { Text("Protected packages") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(BloatCatalog.protectedPackages.toList().sorted()) { pkg ->
                        Text(pkg, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProtected = false }) { Text("Close") } },
        )
    }

    if (showThemeEditor) {
        ThemeEditorDialog(
            onDismiss = { showThemeEditor = false },
            onSave = { name, seed ->
                showThemeEditor = false
                vm.saveCustomTheme(name, seed)
            },
        )
    }
}

@Composable
private fun ThemeEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf("My theme") }
    var hue by remember { mutableStateOf(160f) }
    var saturation by remember { mutableStateOf(0.65f) }

    val primary = Palette.argb(hue, saturation, 0.45f)
    val container = Palette.argb(hue, saturation * 0.5f, 0.9f)
    val accent = Palette.argb(hue + 26f, saturation * 0.55f, 0.55f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create theme") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Hue ${hue.toInt()}°", style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0.1f..1f)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(primary, container, accent).forEach { swatch ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(Color(swatch), CircleShape),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, primary) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
