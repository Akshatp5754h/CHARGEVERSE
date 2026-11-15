package com.akshat.chargeverse.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle

/**
 * AI studio tab — optional, local-ComfyUI-only. Shown inside the Studio
 * screen; fully inert when the user has not enabled AI.
 */
@Composable
fun AiStudioSection(viewModel: AiViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()

    GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CvIcon(R.drawable.ic_util_sparkle, NeonPurple, Modifier.height(18.dp))
            Spacer(Modifier.padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text("AI studio — local ComfyUI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Optional. Uses only your own ComfyUI server at ${state.settings.aiServerUrl}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimText,
                )
            }
            Text(
                if (state.settings.aiEnabled) "ENABLED" else "OFF",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.settings.aiEnabled) NeonBlue else DimText,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (!state.settings.aiEnabled) {
            Text(
                "Enable the AI studio in Settings → Optional AI studio, and run ComfyUI on this network.",
                style = MaterialTheme.typography.bodyMedium,
                color = DimText,
            )
        } else {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text("Prompt — describe your charging animation frame") },
                placeholder = { Text("e.g. neon lightning bolt over a cyberpunk skyline, AMOLED black") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.checkpoint,
                onValueChange = viewModel::setCheckpoint,
                label = { Text("Checkpoint filename installed in ComfyUI") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            if (state.generating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(
                            Brush.gradient,
                            MaterialTheme.shapes.small,
                        )
                        .clickable(enabled = !state.generating) { viewModel.generate() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        if (state.generating) "Generating…" else "Generate image",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            state.statusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = NeonBlue)
            }
        }
    }
}

private object Brush {
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(NeonBlue, NeonPurple)
    )
}

/** Generation history list, rendered under the studio profiles. */
@Composable
fun AiHistoryList(viewModel: AiViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    if (state.history.isEmpty()) return
    SectionTitle("AI generation history", Modifier.padding(horizontal = 24.dp))
    LazyColumn(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.history, key = { it.id }) { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(GlassFill, MaterialTheme.shapes.small)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.prompt.take(64),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Text(
                        "${entry.workflow} · ${entry.status}" + (entry.errorMessage?.let { " — $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.status == "OK") NeonBlue else DimText,
                    )
                }
                CvIcon(
                    R.drawable.ic_util_trash,
                    DimText,
                    Modifier.height(15.dp).clickable { viewModel.deleteHistory(entry) },
                )
            }
        }
    }
}
