package com.akshat.chargeverse.ui.sounds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.data.db.SoundRole
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle

@Composable
fun SoundsScreen(viewModel: SoundsViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importAudio(it, SoundRole.CONNECT) } }

    Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Sounds", style = MaterialTheme.typography.headlineMedium)
            Text("Charging sound studio", style = MaterialTheme.typography.bodyMedium, color = DimText)
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("Playback", Modifier.padding(horizontal = 24.dp))
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text("${(state.volume * 100).toInt()}%", color = NeonBlue, style = MaterialTheme.typography.labelLarge)
                }
                Slider(value = state.volume, onValueChange = viewModel::setVolume)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mute all", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = state.muted, onCheckedChange = viewModel::setMuted)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat charging sound", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = state.repeat, onCheckedChange = viewModel::setRepeat)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Sound library", Modifier.padding(horizontal = 24.dp))
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .background(NeonPurple.copy(alpha = 0.16f), MaterialTheme.shapes.small)
                    .clickable { audioPicker.launch(arrayOf("audio/*")) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CvIcon(R.drawable.ic_util_import, NeonPurple, Modifier.height(15.dp))
                    Spacer(Modifier.padding(4.dp))
                    Text("Import audio", style = MaterialTheme.typography.labelLarge, color = NeonPurple)
                }
            }
        }
        if (state.sounds.isEmpty()) {
            Text(
                "No sounds yet. Import an audio file to assign it to charger events.",
                style = MaterialTheme.typography.bodyMedium,
                color = DimText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 140.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.sounds, key = { it.id }) { sound ->
                    SoundRow(
                        name = sound.name,
                        role = sound.role,
                        isAssignedTo = assignedRoleOf(sound.id, state),
                        onPreview = { viewModel.preview(sound) },
                        onDelete = { viewModel.delete(sound) },
                    )
                }
            }
        }
        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

private fun assignedRoleOf(id: Long, state: com.akshat.chargeverse.ui.sounds.SoundsUiState): SoundRole? = when (id) {
    state.connectSoundId -> SoundRole.CONNECT
    state.disconnectSoundId -> SoundRole.DISCONNECT
    state.fullSoundId -> SoundRole.FULL
    state.lowSoundId -> SoundRole.LOW
    else -> null
}

@Composable
private fun SoundRow(
    name: String,
    role: SoundRole,
    isAssignedTo: SoundRole?,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassFill, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CvIcon(
            R.drawable.ic_util_volume,
            if (isAssignedTo != null) NeonBlue else DimText,
            Modifier.height(20.dp),
        )
        Spacer(Modifier.padding(6.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                (isAssignedTo?.name?.lowercase()?.replace('_', ' ') ?: "unassigned") +
                    " · ${role.name.lowercase().replace('_', ' ')}",
                style = MaterialTheme.typography.labelMedium,
                color = DimText,
            )
        }
        CvIcon(R.drawable.ic_util_play, NeonBlue, Modifier.padding(6.dp).clickable(onClick = onPreview))
        CvIcon(R.drawable.ic_util_trash, DimText, Modifier.padding(6.dp).clickable(onClick = onDelete))
    }
}
