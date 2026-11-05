package dev.modifyx.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import dev.modifyx.core.PrivilegeMode
import dev.modifyx.core.PrivilegeState
import dev.modifyx.data.PackageRepository
import dev.modifyx.data.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App icon with a letter fallback for packages whose icon is unavailable. */
@Composable
fun AppIcon(
    repository: PackageRepository,
    packageName: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { repository.iconFor(packageName) }
    }
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = packageName.substringAfterLast('.').take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RiskBadge(risk: RiskLevel, modifier: Modifier = Modifier) {
    val (container, content) = when (risk) {
        RiskLevel.SAFE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RiskLevel.CAUTION -> Color(0xFFFFE0A3) to Color(0xFF4A3400)
        RiskLevel.RISKY -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Badge(risk.label, container, content, modifier)
}

@Composable
fun Badge(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.62f),
        )
    }
}

@Composable
fun PrivilegeCard(
    state: PrivilegeState,
    adbBusy: Boolean,
    adbMessage: String?,
    onDetect: () -> Unit,
    onConnectAdb: (Int) -> Unit,
    onDisconnectAdb: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var port by remember(state.adbPort) { mutableStateOf(state.adbPort.toString()) }
    val container = when (state.mode) {
        PrivilegeMode.ROOT, PrivilegeMode.ADB -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (state.mode) {
        PrivilegeMode.ROOT, PrivilegeMode.ADB -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.summary, style = MaterialTheme.typography.titleMedium, color = content)
                    Text(
                        when (state.mode) {
                            PrivilegeMode.ROOT -> "su binary: ${state.suBinary ?: "detected"}. Commands run as UID 0."
                            PrivilegeMode.ADB -> "adbd is serving the shell user on port ${state.adbPort}."
                            PrivilegeMode.LOCAL -> "Without root or the ADB bridge this app cannot change packages. It can still inspect them."
                            PrivilegeMode.NONE -> "Probing for root and adbd…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.8f),
                    )
                }
                if (state.detecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDetect, enabled = !state.detecting) { Text("Re-detect") }
                if (state.adbConnected) {
                    OutlinedButton(onClick = onDisconnectAdb) { Text("Disconnect ADB") }
                }
            }

            if (state.mode != PrivilegeMode.ROOT) {
                Spacer(Modifier.height(14.dp))
                Text("ADB bridge", style = MaterialTheme.typography.titleSmall, color = content)
                Text(
                    "Modify-X speaks the ADB protocol to this phone's own adbd, so no other app is involved. " +
                        "Enable USB debugging, run adb tcpip ${state.adbPort} from a computer once, then connect here. " +
                        "adbd shows a key fingerprint prompt: compare it with the value below and allow it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { typed -> port = typed.filter { it.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.width(130.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConnectAdb(port.toIntOrNull() ?: 5555) },
                        enabled = !adbBusy,
                    ) {
                        Text(if (adbBusy) "Connecting…" else if (state.adbConnected) "Reconnect" else "Connect")
                    }
                }
                if (state.adbKeyFingerprint.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Key ${state.adbKeyFingerprint}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = content.copy(alpha = 0.75f),
                    )
                }
                state.adbError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.9f))
                }
                adbMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.9f))
                    TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    warning: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (warning != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BusyDialog(title: String, done: Int, total: Int, current: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(Modifier.animateContentSize()) {
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("$done / $total", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (current.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MonoBlock(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}
