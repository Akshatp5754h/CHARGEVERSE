package com.akshat.chargeverse.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.batteryAccent
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle
import com.akshat.chargeverse.ui.components.StatRow
import com.akshat.chargeverse.ui.nav.CvDestination

@Composable
fun HomeScreen(
    onNavigate: (CvDestination) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val reading = state.battery
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 120.dp)
    ) {
        Text(
            "CHARGEVERSE",
            style = MaterialTheme.typography.labelLarge,
            color = DimText,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text("Charging universe", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // Hero battery ring
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BatteryRing(percent = reading?.levelPercent ?: -1, charging = reading?.isCharging == true)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                reading == null -> "Reading battery…"
                reading.isFull -> "Fully charged"
                reading.isCharging -> "Charging · ${state.chargingSourceLabel}"
                else -> "On battery"
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        reading?.minutesToFull?.let { minutes ->
            Text(
                "Full in ${formatMinutes(minutes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = DimText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(24.dp))

        SectionTitle("Live stats")
        GlassCard {
            reading?.let { r ->
                StatRow("Status", r.statusLabel)
                StatRow("Source", state.chargingSourceLabel)
                r.temperatureC?.let { StatRow("Temperature", "%.1f °C".format(it)) }
                r.voltageMv?.let { StatRow("Voltage", "%d mV".format(it)) }
                r.currentMicroAmp?.let {
                    StatRow("Current", "%.2f mA".format(it / 1000.0))
                }
                r.chargingPowerMw?.let { StatRow("Power", "%.2f W".format(it / 1000.0)) }
            } ?: Text("Waiting for battery data…", color = DimText)
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Charging experience")
        GlassCard(glow = true) {
            Text("Active profile", style = MaterialTheme.typography.labelMedium, color = DimText)
            Spacer(Modifier.height(4.dp))
            Text(
                state.activeProfile?.name ?: "No profile set",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(
                    text = if (state.settings.randomAnimationEnabled) "Random: on" else "Random: off",
                    accent = NeonPurple,
                )
                Pill(
                    text = if (state.settings.loopAnimation) "Loop: on" else "Loop: off",
                    accent = NeonBlue,
                )
                Pill(text = "${state.mediaCount} animations", accent = NeonBlue)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile("Animations", CvDestination.ANIMATIONS, onNavigate, Modifier.weight(1f))
            QuickTile("Studio", CvDestination.STUDIO, onNavigate, Modifier.weight(1f))
            QuickTile("Sounds", CvDestination.SOUNDS, onNavigate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BatteryRing(percent: Int, charging: Boolean) {
    val accent = batteryAccent(percent.coerceIn(0, 100))
    val sweep = (percent.coerceIn(0, 100) / 100f) * 360f
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(230.dp)) {
            val stroke = Stroke(width = 16f, cap = StrokeCap.Round)
            // track
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = stroke,
            )
            if (percent >= 0) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to NeonBlue, 1f to accent, center = Offset(size.width / 2, size.height / 2),
                    ),
                    startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    style = stroke,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (percent >= 0) "$percent%" else "—",
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
            )
            if (charging) {
                Text("⚡ charging", style = MaterialTheme.typography.labelMedium, color = NeonBlue)
            }
        }
    }
}

@Composable
private fun Pill(text: String, accent: Color) {
    Box(
        Modifier
            .background(accent.copy(alpha = 0.14f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

@Composable
private fun QuickTile(
    label: String,
    dest: CvDestination,
    onNavigate: (CvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(84.dp)
            .background(GlassFill, MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(listOf(NeonBlue.copy(alpha = 0.06f), NeonPurple.copy(alpha = 0.06f))),
                MaterialTheme.shapes.large,
            )
            .clickable { onNavigate(dest) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatMinutes(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
