package dev.modifyx.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.modifyx.ui.UpdateState

/**
 * Body of the update card. Home and Settings both render this, so the wording, the
 * signature-checked install button and the progress bar can never drift apart.
 */
@Composable
fun UpdateSection(state: UpdateState, onInstall: () -> Unit, modifier: Modifier = Modifier) {
    val info = state.available
    if (info == null) {
        state.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Text("Version ${info.version} available", style = MaterialTheme.typography.titleMedium)
    if (info.notes.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            info.notes.take(700),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onInstall,
        enabled = !state.downloading,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(if (state.downloading) "Downloading ${state.progress}%" else "Download & install")
    }
    if (state.downloading) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.message?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
