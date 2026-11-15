package com.akshat.chargeverse.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Ask once when the screen opens on Android 13+.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val s = state.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 140.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Persistence, permissions and optional AI", style = MaterialTheme.typography.bodyMedium, color = DimText)
        Spacer(Modifier.height(20.dp))

        SectionTitle("Charging experience")
        GlassCard {
            SwitchRow("Open animation when charger connects", s.showOnChargerConnect) { viewModel.setShowOnConnect(it) }
            SwitchRow("Keep screen on while charging", s.keepScreenOnWhileCharging) { viewModel.setKeepScreenOn(it) }
            SwitchRow("Loop animations", s.loopAnimation) { viewModel.setLoop(it) }
            SwitchRow("Pick a random profile each charge", s.randomAnimationEnabled) { viewModel.setRandom(it) }
            SwitchRow("Haptic pulse on connect", s.hapticsEnabled) { viewModel.setHaptics(it) }
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Alerts")
        GlassCard {
            SwitchRow("Full battery alert", s.fullBatteryAlertEnabled) { viewModel.setFullAlert(it) }
            SwitchRow("Low battery alert", s.lowBatteryAlertEnabled) { viewModel.setLowAlert(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Low threshold: ${s.lowBatteryThreshold}%", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Slider(
                    value = s.lowBatteryThreshold.toFloat(),
                    onValueChange = { viewModel.setLowThreshold(it.toInt()) },
                    valueRange = 5f..50f,
                    modifier = Modifier.fillMaxWidth(0.55f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Optional AI studio (local only)")
        GlassCard {
            SwitchRow("Enable AI studio", s.aiEnabled) { viewModel.setAiEnabled(it) }
            Text(
                "Connects only to your own local ComfyUI server. The app works fully without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = DimText,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.aiServerUrl,
                onValueChange = viewModel::setAiUrl,
                label = { Text("ComfyUI server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::pingAi) { Text("Test connection") }
                Spacer(Modifier.padding(8.dp))
                val reachable = state.aiServerReachable
                if (reachable != null) {
                    Text(
                        if (reachable) "✓ reachable" else "✗ not reachable",
                        color = if (reachable) NeonBlue else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Storage")
        GlassCard {
            Text(
                "Imported media stored on-device: %.1f MB".format(state.storageUsedMb),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Media is kept in the app's private folder — no storage permission is used.",
                style = MaterialTheme.typography.bodyMedium,
                color = DimText,
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("About")
        GlassCard {
            Text("CHARGEVERSE 1.0.0", style = MaterialTheme.typography.titleMedium)
            Text(
                "Package com.akshat.chargeverse · 100% free, no ads, no tracking, no cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = DimText,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
