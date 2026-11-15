package com.akshat.chargeverse.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.ui.ai.AiHistoryList
import com.akshat.chargeverse.ui.ai.AiStudioSection
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.ConfirmDeleteDialog
import com.akshat.chargeverse.ui.components.EmptyState
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle

@Composable
fun StudioScreen(viewModel: StudioViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    var deleteCandidate by remember { mutableStateOf<AnimationProfileEntity?>(null) }

    val editing = state.editing
    if (editing != null) {
        ProfileEditor(
            profile = editing,
            onClose = { viewModel.openEditor(null) },
            onSave = viewModel::saveEdits,
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 56.dp, bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Studio", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Build and tune charging profiles from your animations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimText,
                )
            }
        }

        item { SectionTitle("Profiles (${state.profiles.size})") }

        if (state.profiles.isEmpty()) {
            item {
                EmptyState(
                    iconRes = R.drawable.ic_nav_studio,
                    title = "No profiles yet",
                    body = "Pick an animation below and turn it into a profile.",
                )
            }
        } else {
            items(state.profiles, key = { "p${it.id}" }) { profile ->
                ProfileRow(
                    profile = profile,
                    onActivate = { viewModel.activate(profile) },
                    onEdit = { viewModel.openEditor(profile) },
                    onDelete = { deleteCandidate = profile },
                )
            }
        }

        // Start from an animation
        item { SectionTitle("Start from an animation (${state.media.size})") }
        if (state.media.isEmpty()) {
            item {
                Text(
                    "Import animations in the Animations tab first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimText,
                )
            }
        } else {
            items(state.media, key = { "m${it.id}" }) { media ->
                MediaSourceRow(media) { viewModel.createProfile(media) }
            }
        }

        // Optional AI studio
        item {
            Spacer(Modifier.height(12.dp))
            AiStudioSection()
        }
        item { AiHistoryList() }
    }

    // D9: profile deletion is confirmed; the underlying animation stays.
    deleteCandidate?.let { profile ->
        ConfirmDeleteDialog(
            title = "Delete profile",
            message = "Profile “${profile.name}” will be removed. The animation itself stays in your library.",
            onConfirm = {
                viewModel.delete(profile)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun MediaSourceRow(media: MediaItemEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassFill, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CvIcon(R.drawable.ic_nav_animations, NeonBlue, Modifier.height(20.dp))
        Spacer(Modifier.padding(6.dp))
        Column {
            Text(media.title, style = MaterialTheme.typography.titleMedium)
            Text(
                media.type.name.lowercase(),
                style = MaterialTheme.typography.labelMedium,
                color = DimText,
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: AnimationProfileEntity,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassFill, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                if (profile.isActive) {
                    Spacer(Modifier.padding(4.dp))
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = NeonBlue)
                }
            }
            Text(
                "scale %.1f · speed %.1fx".format(profile.scale, profile.playbackSpeed),
                style = MaterialTheme.typography.labelMedium,
                color = DimText,
            )
        }
        CvIcon(R.drawable.ic_util_play, NeonBlue, Modifier.padding(6.dp).clickable(onClick = onActivate))
        CvIcon(R.drawable.ic_nav_studio, DimText, Modifier.padding(6.dp).clickable(onClick = onEdit))
        CvIcon(R.drawable.ic_util_trash, DimText, Modifier.padding(6.dp).clickable(onClick = onDelete))
    }
}

@Composable
private fun ProfileEditor(
    profile: AnimationProfileEntity,
    onClose: () -> Unit,
    onSave: (AnimationProfileEntity) -> Unit,
) {
    var scale by remember { mutableStateOf(profile.scale) }
    var offsetX by remember { mutableStateOf(profile.offsetX) }
    var offsetY by remember { mutableStateOf(profile.offsetY) }
    var speed by remember { mutableStateOf(profile.playbackSpeed) }
    var text by remember { mutableStateOf(profile.textOverlay) }
    var showBattery by remember { mutableStateOf(profile.showBatteryOverlay) }
    var showPercent by remember { mutableStateOf(profile.showPercent) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 140.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CvIcon(R.drawable.ic_util_close, DimText, Modifier.clickable(onClick = onClose))
            Spacer(Modifier.padding(4.dp))
            Text("Editing “${profile.name}”", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            EditorSlider("Scale", scale, 0.2f..3f) { scale = it }
            EditorSlider("Position X", offsetX, -1f..1f) { offsetX = it }
            EditorSlider("Position Y", offsetY, -1f..1f) { offsetY = it }
            EditorSlider("Playback speed", speed, 0.25f..2f) { speed = it }
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text overlay") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("Battery overlay", showBattery) { showBattery = it }
            SwitchRow("Show percentage", showPercent) { showPercent = it }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = {
                onSave(
                    profile.copy(
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        playbackSpeed = speed,
                        textOverlay = text,
                        showBatteryOverlay = showBattery,
                        showPercent = showPercent,
                    )
                )
            }) {
                Text("Save profile")
            }
        }
    }
}

@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = DimText)
            Spacer(Modifier.weight(1f))
            Text("%.2f".format(value), style = MaterialTheme.typography.labelLarge, color = NeonBlue)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
