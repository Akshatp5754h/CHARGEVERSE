package com.akshat.chargeverse.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle
import com.akshat.chargeverse.ui.components.StatRow

@Composable
fun BatteryScreen(viewModel: BatteryViewModel = viewModel()) {
    val reading by viewModel.reading.collectAsState()
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 120.dp)
    ) {
        Text("Battery", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Live readings from Android BatteryManager",
            style = MaterialTheme.typography.bodyMedium,
            color = DimText,
        )
        Spacer(Modifier.height(20.dp))

        reading?.let { r ->
            LevelBar(r)
            Spacer(Modifier.height(20.dp))

            SectionTitle("State")
            GlassCard {
                StatRow("Status", r.statusLabel)
                StatRow("Health", r.healthLabel)
                StatRow("Technology", r.technology ?: "Not reported")
                r.cycleCount?.let { StatRow("Charge cycles", "$it") }
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Measurements")
            GlassCard {
                StatRow("Temperature", r.temperatureC?.let { "%.1f °C".format(it) } ?: "Not reported")
                StatRow("Voltage", r.voltageMv?.let { "%d mV".format(it) } ?: "Not reported")
                StatRow(
                    "Current",
                    r.currentMicroAmp?.let { "%.1f mA".format(it / 1000.0) } ?: "Not reported",
                )
                StatRow(
                    "Average current",
                    r.currentAverageMicroAmp?.let { "%.1f mA".format(it / 1000.0) } ?: "Not reported",
                )
                StatRow(
                    "Charge counter",
                    r.chargeCounterUah?.let { "%.0f mAh".format(it / 1000.0) } ?: "Not reported",
                )
                StatRow(
                    "Charging power",
                    r.chargingPowerMw?.let { "%.2f W".format(it / 1000.0) } ?: "Not reported",
                )
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Notes")
            GlassCard {
                Text(
                    text = buildString {
                        append("Values marked “Not reported” are not exposed by this device")
                        append(" — CHARGEVERSE never invents battery data.")
                        if (r.minutesToFull != null) {
                            append(" Estimated time to full is computed from the real charge counter and current.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimText,
                )
            }
        } ?: Text("Reading battery…", color = DimText)
    }
}

@Composable
private fun LevelBar(r: BatteryReading) {
    Column {
        Row {
            Text("${r.levelPercent}%", fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.align(androidx.compose.ui.Alignment.CenterVertically)) {
                Text(r.statusLabel, style = MaterialTheme.typography.titleMedium)
                if (r.isCharging) {
                    Text("⚡ plugged in", style = MaterialTheme.typography.labelMedium, color = NeonBlue)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(androidx.compose.ui.graphics.Color(0x22FFFFFF), MaterialTheme.shapes.small)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth(fraction = (r.levelPercent.coerceIn(0, 100) / 100f))
                    .height(14.dp)
                    .background(
                        Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)),
                        MaterialTheme.shapes.small,
                    )
            )
        }
    }
}
