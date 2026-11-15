package com.akshat.chargeverse.charging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.battery.ChargingEvent
import com.akshat.chargeverse.core.theme.ElectricGreen
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.Void
import com.akshat.chargeverse.core.theme.batteryAccent
import com.akshat.chargeverse.ui.charging.ChargingViewModel

/**
 * Full-screen charging experience shown while plugged in (also over the lock
 * screen when the system launches it from the full-screen intent). Native
 * pulsing charge animation; user media playback is layered in Sprint 4.
 */
class ChargingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            com.akshat.chargeverse.core.theme.ChargeverseTheme {
                ChargingScreen()
            }
        }
    }

    override fun onDestroy() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    @Composable
    private fun ChargingScreen() {
        val vm: ChargingViewModel = viewModel()
        val reading by vm.state.collectAsState()
        val accent = batteryAccent(reading?.levelPercent?.coerceIn(0, 100) ?: 0)

        val pulse = rememberInfiniteTransition(label = "pulse")
        val ringScale by pulse.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "ring",
        )
        val glowAlpha by pulse.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "glow",
        )

        Box(Modifier.fillMaxSize().background(Void), contentAlignment = Alignment.Center) {
            PulsingChargeCanvas(
                percent = reading?.levelPercent ?: -1,
                ringScale = ringScale,
                glowAlpha = glowAlpha,
                accent = accent,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${reading?.levelPercent?.takeIf { it >= 0 } ?: "—"}%",
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    when {
                        reading?.isFull == true -> "Fully charged"
                        reading?.isCharging == true -> "Charging"
                        else -> "Charger disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                reading?.minutesToFull?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Full in ${it / 60}h ${it % 60}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8A8AB0),
                    )
                }
            }
        }

        // Exit when unplugged or full after a delay.
        LaunchedEffect(Unit) {
            vm.events.collect { event ->
                if (event == ChargingEvent.DISCONNECTED) finish()
            }
        }
    }

    @Composable
    private fun PulsingChargeCanvas(
        percent: Int,
        ringScale: Float,
        glowAlpha: Float,
        accent: Color,
    ) {
        Canvas(Modifier.size(320.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.minDimension / 2f - 24f) * ringScale

            // Glow
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = glowAlpha * 0.5f), Color.Transparent),
                    center = center,
                    radius = radius * 1.25f,
                ),
                radius = radius * 1.25f,
                center = center,
            )
            // Track
            drawCircle(
                color = Color(0x22FFFFFF),
                radius = radius,
                center = center,
                style = Stroke(width = 14f, cap = StrokeCap.Round),
            )
            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(listOf(NeonBlue, accent, NeonPurple), center = center),
                startAngle = -90f,
                sweepAngle = ((percent.coerceIn(0, 100)) / 100f) * 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 14f, cap = StrokeCap.Round),
            )
            // Orbiting spark
            val angle = ((ringScale - 0.94f) / 0.12f) * Math.PI
            drawCircle(
                color = ElectricGreen.copy(alpha = glowAlpha),
                radius = 7f,
                center = Offset(
                    center.x + radius * kotlin.math.cos(angle).toFloat(),
                    center.y + radius * kotlin.math.sin(angle).toFloat(),
                ),
            )
        }
    }
}
