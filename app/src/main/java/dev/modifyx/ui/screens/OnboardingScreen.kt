package dev.modifyx.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.modifyx.data.ScanRoot

/**
 * First-run entry: an animated title, a short explanation of the model, then the one thing
 * the app cannot do for the user — grant folders. Nothing is requested that is not needed.
 */
@Composable
fun OnboardingScreen(
    roots: List<ScanRoot>,
    onAddFolder: () -> Unit,
    onRemoveRoot: (ScanRoot) -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "entry")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val surface = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        surface,
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    ),
                    start = Offset(0f, 1400f * phase),
                    end = Offset(1200f, 200f + 400f * phase),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Modify-X",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (step < 2) {
                TextButton(onClick = onFinish) { Text("Skip") }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size((104 + 6 * phase).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent, MaterialTheme.colorScheme.primaryContainer),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "MX",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(20.dp))

            Crossfade(targetState = step, label = "step") { current ->
                when (current) {
                    0 -> IntroStep()
                    1 -> HowItWorksStep()
                    else -> FoldersStep(
                        roots = roots,
                        onAddFolder = onAddFolder,
                        onRemoveRoot = onRemoveRoot,
                        onFinish = onFinish,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == step) 22.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index == step) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(
                onClick = { if (step < 2) step++ else onFinish() },
                enabled = step < 2 || roots.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (step < 2) "Continue" else if (roots.isEmpty()) "Pick a folder" else "Start scanning")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun IntroStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Reclaim your storage",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Modify-X finds the files you forgot about — junk left by other apps, duplicate photos, and anything else eating space — and deletes only what you confirm.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HowItWorksStep() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("How it works", style = MaterialTheme.typography.headlineSmall)
        FeatureRow(
            icon = Icons.Default.Search,
            title = "1 · You choose folders",
            detail = "Android hands the app read and write access only to the folders you pick. No storage permission is requested and nothing outside them is visible.",
        )
        FeatureRow(
            icon = Icons.Default.List,
            title = "2 · It reads what is really there",
            detail = "Junk is matched by name and extension. Duplicates are proven with a full SHA-256 hash of the file contents, not by name or size alone.",
        )
        FeatureRow(
            icon = Icons.Default.Delete,
            title = "3 · You review, then delete",
            detail = "Every deletion is re-checked afterwards. If Android still serves the file, it is reported as a failure instead of counted as freed space.",
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FoldersStep(
    roots: List<ScanRoot>,
    onAddFolder: () -> Unit,
    onRemoveRoot: (ScanRoot) -> Unit,
    onFinish: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pick what to scan", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Start with Pictures, DCIM, Download or a messaging app's media folder. You can add or remove folders at any time in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (roots.isEmpty()) {
            Text(
                "No folders granted yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            roots.forEach { root ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(root.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemoveRoot(root) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${root.label}")
                    }
                }
            }
        }
        OutlinedButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) {
            Text("Add a folder")
        }
        TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Maybe later")
        }
        Text(
            "Modify-X cannot delete anything outside the folders above, and it never uploads file data.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
