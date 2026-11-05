package dev.modifyx.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.modifyx.ui.components.MonoBlock
import dev.modifyx.ui.components.ScreenHeader
import dev.modifyx.ui.components.SectionCard
import dev.modifyx.ui.formatDuration
import dev.modifyx.ui.MainViewModel

@Composable
fun ConsoleScreen(vm: MainViewModel, onBack: () -> Unit) {
    val privileges by vm.privileges.collectAsStateWithLifecycle()
    val console = vm.console
    var command by remember { mutableStateOf("pm list packages -3") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Shell console",
            subtitle = "Commands run through ${privileges.summary}",
            onBack = onBack,
        )
        Text(
            "Commands run through ${privileges.summary}. The exit code and raw output are shown unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Command") {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                label = { Text("shell command") },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.runCommand(command) },
                    enabled = !console.running,
                ) { Text("Run") }
                if (console.running) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QUICK_COMMANDS.forEach { quick ->
                    AssistChip(
                        onClick = {
                            command = quick
                            vm.runCommand(quick)
                        },
                        label = { Text(quick) },
                    )
                }
            }
        }

        SectionCard(title = "Output") {
            if (console.command.isBlank()) {
                Text(
                    "Nothing executed yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "$ ${console.command}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer { MonoBlock(console.output) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "exit ${console.exitCode ?: "?"} · ${formatDuration(console.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "Destructive commands are not filtered. You are responsible for what you type.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private val QUICK_COMMANDS = listOf(
    "pm list packages -3",
    "pm list packages -d --user 0",
    "pm list users",
    "dumpsys battery",
    "getprop ro.product.model",
    "getprop ro.build.version.release",
)
