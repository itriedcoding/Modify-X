package dev.modifyx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.modifyx.data.BloatCatalog
import dev.modifyx.data.Profile
import dev.modifyx.data.RiskLevel
import dev.modifyx.ui.components.ConfirmDialog
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.components.ToggleRow
import dev.modifyx.ui.formatAgo
import dev.modifyx.ui.MainViewModel

@Composable
fun ProfilesScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val profiles = vm.profiles
    val builtIns = profiles.filter { it.builtIn }
    val custom = profiles.filterNot { it.builtIn }

    var preview by remember { mutableStateOf<Profile?>(null) }
    var pendingApply by remember { mutableStateOf<Profile?>(null) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Presets",
            subtitle = "Curated bloatware groups, filtered to this device. Nothing applies until you press Apply.",
        )

        if (builtIns.isEmpty()) {
            SectionCard(title = "No catalog matches") {
                Text(
                    "No packages from the known-bloat catalog were found. Try the Apps tab to select packages manually.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        builtIns.forEach { profile ->
            val risky = remember(profile) {
                profile.packages.count { BloatCatalog.find(it)?.risk != RiskLevel.SAFE }
            }
            SectionCard(
                title = profile.name,
                subtitle = profile.group?.blurb,
            ) {
                Text(
                    "${profile.packages.size} installed package${if (profile.packages.size == 1) "" else "s"}" +
                        if (risky > 0) " · $risky need caution or a risky override" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { preview = profile }, modifier = Modifier.weight(1f)) {
                        Text("Preview")
                    }
                    Button(
                        onClick = { pendingApply = profile },
                        enabled = privileges.canModify,
                        modifier = Modifier.weight(1f),
                    ) { Text("Apply") }
                }
            }
        }

        SectionCard(
            title = "Your profiles",
            subtitle = "Select packages in the Apps tab, then save the selection as a profile.",
        ) {
            if (custom.isEmpty()) {
                Text(
                    "No saved profiles yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            custom.forEach { profile ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${profile.packages.size} packages · saved ${formatAgo(profile.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { preview = profile }) { Text("View") }
                    IconButton(onClick = { pendingDelete = profile }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete profile")
                    }
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { profileName = ""; showSaveDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (vm.selection.isEmpty()) {
                        "Select packages in Apps to build a profile"
                    } else {
                        "Save ${vm.selection.size} selected packages as profile"
                    },
                )
            }
        }

        SectionCard(
            title = "Automation",
            subtitle = "Re-apply a profile after every boot. Requires root or the ADB bridge to be available at boot.",
        ) {
            ToggleRow(
                title = "Apply profile after boot",
                subtitle = if (settings.autoApplyProfileId == null) {
                    "Choose a profile below first."
                } else {
                    "Profile: ${profiles.firstOrNull { it.id == settings.autoApplyProfileId }?.name ?: settings.autoApplyProfileId}"
                },
                checked = settings.autoApplyEnabled,
                onCheckedChange = vm::setAutoApply,
            )
            Spacer(Modifier.height(8.dp))
            val choices = profiles
            Column {
                choices.forEach { profile ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.setAutoApplyProfile(profile.id) }) {
                            Text(if (settings.autoApplyProfileId == profile.id) "Selected" else "Use")
                        }
                    }
                }
            }
        }
    }

    preview?.let { profile ->
        val apps = vm.previewProfile(profile)
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(profile.name) },
            text = {
                Column {
                    Text(
                        "${apps.size} package${if (apps.size == 1) "" else "s"} will be uninstalled for user 0.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            val note = BloatCatalog.find(app.packageName)
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName + (note?.let { " · ${it.risk.label}" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        preview = null
                        pendingApply = profile
                    },
                    enabled = privileges.canModify,
                ) { Text("Apply") }
            },
        )
    }

    pendingApply?.let { profile ->
        ConfirmDialog(
            title = "Apply ${profile.name}",
            message = "Uninstall ${profile.packages.size} packages for user 0? Risky and protected entries are skipped.",
            confirmLabel = "Apply",
            destructive = true,
            warning = "If the device misbehaves, restore packages from History.",
            onConfirm = {
                pendingApply = null
                vm.applyProfile(profile)
            },
            onDismiss = { pendingApply = null },
        )
    }

    pendingDelete?.let { profile ->
        ConfirmDialog(
            title = "Delete profile",
            message = "Remove \"${profile.name}\" from your saved profiles?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                vm.deleteProfile(profile)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${vm.selection.size} selected package(s): " +
                            vm.selection.take(5).joinToString(", ") + if (vm.selection.size > 5) "…" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveProfile(profileName, vm.selection)
                        showSaveDialog = false
                    },
                    enabled = profileName.isNotBlank() && vm.selection.isNotEmpty(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
        )
    }
}
