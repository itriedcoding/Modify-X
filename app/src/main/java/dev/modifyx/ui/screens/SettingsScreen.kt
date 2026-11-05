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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.JunkRules
import dev.modifyx.data.ThemeId
import dev.modifyx.data.ThemeMode
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.components.ToggleRow
import dev.modifyx.ui.components.UpdateSection
import dev.modifyx.ui.theme.Palette

@Composable
fun SettingsScreen(vm: MainViewModel, onAddFolder: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update = vm.update
    val context = LocalContext.current
    var showThemeEditor by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Settings", subtitle = "Folders, rules and appearance")

        SectionCard(
            title = "Folders",
            subtitle = "Android's file picker grants access per folder. Revoking it here stops all access.",
        ) {
            if (vm.roots.isEmpty()) {
                Text(
                    "No folder granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.roots.forEach { root ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        root.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.removeRoot(root) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${root.label}")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) { Text("Add folder") }
        }

        SectionCard(
            title = "Junk rules",
            subtitle = "Only matched names and extensions can be deleted. Turn off anything you want to keep.",
        ) {
            JunkRules.all.forEach { rule ->
                ToggleRow(
                    title = rule.label,
                    subtitle = rule.description,
                    checked = rule.id !in settings.disabledRules,
                    onCheckedChange = { vm.setRuleEnabled(rule.id, it) },
                )
            }
        }

        SectionCard(title = "Scan options") {
            ToggleRow(
                title = "Scan automatically",
                subtitle = "Scan when the app opens and the last result is older than the interval below",
                checked = settings.autoScan,
                onCheckedChange = vm::setAutoScan,
            )
            if (settings.autoScan) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1L, 6L, 24L).forEach { hours ->
                        FilterChip(
                            selected = settings.autoScanHours == hours,
                            onClick = { vm.setAutoScanHours(hours) },
                            label = { Text(if (hours == 1L) "Hourly" else "Every $hours h") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Include hidden files",
                subtitle = "Hidden files are skipped by default. Thumbnail caches are always inspected.",
                checked = settings.includeHidden,
                onCheckedChange = vm::setIncludeHidden,
            )
            Spacer(Modifier.height(8.dp))
            Text("Smallest duplicate to compare", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0L, 1L, 5L, 10L).forEach { mb ->
                    FilterChip(
                        selected = settings.minDuplicateMb == mb,
                        onClick = { vm.setMinDuplicateMb(mb) },
                        label = { Text(if (mb == 0L) "Any size" else "$mb MB") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Large file threshold", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(50L, 100L, 250L, 500L).forEach { mb ->
                    FilterChip(
                        selected = settings.largeFileMb == mb,
                        onClick = { vm.setLargeFileMb(mb) },
                        label = { Text("$mb MB") },
                    )
                }
            }
        }

        SectionCard(title = "Safety") {
            ToggleRow(
                title = "Confirm before deleting",
                subtitle = "Show a summary of what will be deleted and how much it frees",
                checked = settings.confirmDeletes,
                onCheckedChange = vm::setConfirmDeletes,
            )
        }

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
                        leadingIcon = { Box(Modifier.size(14.dp).background(Color(theme.seed), CircleShape)) },
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
            settings.customThemes.forEach { theme ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).background(Color(theme.seed), CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(theme.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteCustomTheme(theme.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${theme.name}")
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
                subtitle = "Use Material You colours with the Wallpaper theme",
                checked = settings.dynamicColor,
                onCheckedChange = vm::setDynamicColor,
            )
        }

        SectionCard(
            title = "Updates",
            subtitle = "Checks github.com/itriedcoding/Modify-X/releases; downloads are signature-checked.",
        ) {
            Text("Installed ${update.currentVersion.ifBlank { version }}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "Check automatically",
                subtitle = "At most once a day when the app opens",
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
            Spacer(Modifier.height(10.dp))
            UpdateSection(state = update, onInstall = vm::downloadAndInstallUpdate)
        }

        SectionCard(title = "About") {
            Text("Modify-X $version", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "A local storage cleaner. It reads and deletes only the folders you grant through Android's file picker. " +
                    "Nothing is uploaded, and the only network request is checking the release page for updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::replayOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("Show welcome again")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::refreshRoots, modifier = Modifier.fillMaxWidth()) {
                Text("Re-check folder access")
            }
        }

        Spacer(Modifier.height(16.dp))
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
private fun ThemeEditorDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("My theme") }
    var hue by remember { mutableFloatStateOf(160f) }
    var saturation by remember { mutableFloatStateOf(0.65f) }

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
                        Box(Modifier.size(48.dp).background(Color(swatch), CircleShape))
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
