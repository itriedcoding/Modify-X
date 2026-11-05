package dev.modifyx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.data.BloatCatalog
import dev.modifyx.data.ThemeMode
import dev.modifyx.ui.MainViewModel
import dev.modifyx.ui.components.SectionCard

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenConsole: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showProtected by remember { mutableStateOf(false) }
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

        SectionCard(title = "Appearance") {
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
            ToggleRow(
                title = "Dynamic color",
                subtitle = "Use wallpaper colors on Android 12 and newer",
                checked = settings.dynamicColor,
                onCheckedChange = vm::setDynamicColor,
            )
        }

        SectionCard(title = "Browsing") {
            ToggleRow(
                title = "Only known bloatware",
                subtitle = "Hide packages that are not in the curated catalog",
                checked = settings.showCatalogOnly,
                onCheckedChange = vm::setCatalogOnly,
            )
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
        }

        SectionCard(title = "About") {
            Text("Modify-X $version", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "A local debloater. It talks to Android's package manager through root or a Shizuku-provided shell, stores everything on the device, and requests no network permission at all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Uninstall for user 0 uses `pm uninstall --user 0`; restore uses `pm install-existing --user 0`. Everything else is a thin, verified wrapper around platform commands.",
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
