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
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.data.db.SoundItemEntity
import com.akshat.chargeverse.data.db.SoundRole
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.ConfirmDeleteDialog
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle

@Composable
fun SoundsScreen(viewModel: SoundsViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.stageImport(it) } }

    var deleteCandidate by remember { mutableStateOf<SoundItemEntity?>(null) }
    var roleEditorFor by remember { mutableStateOf<SoundItemEntity?>(null) }

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
                        isBuiltIn = sound.isBuiltIn,
                        roles = state.rolesBySound[sound.id].orEmpty(),
                        isAssignedTo = assignedRoleOf(sound.id, state),
                        onPreview = { viewModel.preview(sound) },
                        onEditRoles = { roleEditorFor = sound },
                        onDelete = if (sound.isBuiltIn) null else ({ deleteCandidate = sound }),
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

    // D2: importing asks for the role(s) before the file is written.
    state.pendingImportUri?.let { uri ->
        RoleChooserDialog(
            title = "Assign a role",
            subtitle = "Which charger events should this sound serve? You can change it later from the row.",
            initialRoles = emptySet(),
            onDismiss = viewModel::cancelImport,
            onConfirm = { roles -> viewModel.importAudio(uri, roles) },
        )
    }

    // D20: the role chip on each row reassigns that sound's roles.
    roleEditorFor?.let { item ->
        RoleChooserDialog(
            title = "Roles for “${item.name}”",
            subtitle = "Pick every event this sound should serve.",
            initialRoles = state.rolesBySound[item.id].orEmpty().toSet(),
            onDismiss = { roleEditorFor = null },
            onConfirm = { roles ->
                viewModel.setRoles(item, roles)
                roleEditorFor = null
            },
        )
    }

    // D9: sounds are never deleted without confirmation (built-ins are not deletable).
    deleteCandidate?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete sound",
            message = "“${item.name}” and its imported file will be removed. Its role assignments are cleared and events fall back to the built-in chime.",
            onConfirm = {
                viewModel.delete(item)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

private fun assignedRoleOf(id: Long, state: com.akshat.chargeverse.ui.sounds.SoundsUiState): SoundRole? = when (id) {
    state.connectSoundId -> SoundRole.CONNECT
    state.disconnectSoundId -> SoundRole.DISCONNECT
    state.fullSoundId -> SoundRole.FULL
    state.lowSoundId -> SoundRole.LOW
    else -> null
}

/** Multi-select role chooser used for both import and per-row reassignment. */
@Composable
private fun RoleChooserDialog(
    title: String,
    subtitle: String,
    initialRoles: Set<SoundRole>,
    onDismiss: () -> Unit,
    onConfirm: (Set<SoundRole>) -> Unit,
) {
    var selected by remember { mutableStateOf(initialRoles) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimText,
                )
                Spacer(Modifier.height(10.dp))
                SoundRole.entries.forEach { role ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (role in selected) selected - role else selected + role
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CvIcon(
                            if (role in selected) R.drawable.ic_util_check else R.drawable.ic_util_plus,
                            if (role in selected) NeonBlue else DimText,
                            Modifier.height(16.dp),
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            role.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SoundRow(
    name: String,
    isBuiltIn: Boolean,
    roles: List<SoundRole>,
    isAssignedTo: SoundRole?,
    onPreview: () -> Unit,
    onEditRoles: () -> Unit,
    onDelete: (() -> Unit)?,
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
                    if (isBuiltIn) " · built-in" else "",
                style = MaterialTheme.typography.labelMedium,
                color = DimText,
            )
        }
        // D20: role chip — opens the role chooser for this sound.
        Box(
            Modifier
                .background(NeonBlue.copy(alpha = 0.14f), MaterialTheme.shapes.small)
                .clickable(onClick = onEditRoles)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                if (roles.isEmpty()) "role +" else roles.joinToString(" · ") {
                    it.name.lowercase().replace('_', ' ')
                },
                style = MaterialTheme.typography.labelMedium,
                color = NeonBlue,
            )
        }
        CvIcon(R.drawable.ic_util_play, NeonBlue, Modifier.padding(6.dp).clickable(onClick = onPreview))
        // D9: built-in sounds are protected — no delete affordance at all.
        if (onDelete != null) {
            CvIcon(R.drawable.ic_util_trash, DimText, Modifier.padding(6.dp).clickable(onClick = onDelete))
        }
    }
}
