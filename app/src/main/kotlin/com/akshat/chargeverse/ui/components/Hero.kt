package com.akshat.chargeverse.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.core.theme.Ink
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.OutlineDim
import com.akshat.chargeverse.core.theme.batteryAccent
import com.akshat.chargeverse.core.theme.cvColors
import com.akshat.chargeverse.data.db.MediaItemEntity

/**
 * Cinematic hero for Home (v2/S2 §5): a compact card (≤ 38% of screen height)
 * carrying the live battery ring over the active profile's media as a
 * low-alpha backdrop. Text never sits on raw imagery — the contract's ≥70%
 * black scrim (0xCC here, stronger than the 0xB3 minimum) keeps even a
 * pure-white video frame above the 4.5:1 text floor for textSecondary.
 */

@Composable
fun CvHeroCard(
    reading: BatteryReading?,
    stateLabel: String,
    etaLabel: String?,
    heroHeight: Dp,
    backdropMedia: MediaItemEntity?,
    modifier: Modifier = Modifier,
) {
    val cv = MaterialTheme.cvColors
    Box(
        modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink)
            .border(1.dp, OutlineDim.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
    ) {
        if (backdropMedia != null) {
            CvMediaPoster(
                media = backdropMedia,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.30f },
            )
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)))
        }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CvBatteryRing(
                    percent = reading?.levelPercent ?: -1,
                    charging = reading?.isCharging == true,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cv.textSecondary,
                )
                etaLabel?.let { eta ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = eta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cv.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Live battery ring: token track, progress arc tinted by [batteryAccent], and
 * an ambient pulse ring ONLY while charging (1600 ms FastOutSlowIn, subtle
 * alpha/scale, paused under battery saver per §4/S10).
 */
@Composable
fun CvBatteryRing(
    percent: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val cv = MaterialTheme.cvColors
    // Unknown level (-1) stays neutral; batteryAccent(0) would lie "low".
    val accent = if (percent < 0) NeonBlue else batteryAccent(percent.coerceIn(0, 100))
    val description = when {
        percent < 0 -> "Battery level unknown"
        charging -> "Battery level $percent percent, charging"
        else -> "Battery level $percent percent"
    }
    Box(
        modifier
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (charging) {
            ChargingPulse(accent = accent, modifier = Modifier.matchParentSize())
        }
        Canvas(Modifier.matchParentSize().padding(8.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f * 0.97f
            val stroke = Stroke(width = size.minDimension * 0.05f, cap = StrokeCap.Round)
            drawCircle(
                color = OutlineDim,
                radius = radius,
                center = center,
                style = stroke,
            )
            if (percent >= 0) {
                drawArc(
                    brush = Brush.sweepGradient(0f to NeonBlue, 1f to accent, center = center),
                    startAngle = -90f,
                    sweepAngle = (percent.coerceIn(0, 100) / 100f) * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = stroke,
                )
            }
        }
        Text(
            text = if (percent >= 0) "$percent%" else "—",
            style = MaterialTheme.typography.displayLarge,
            color = cv.textPrimary,
            // The ring's contentDescription already announces the level.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun ChargingPulse(accent: Color, modifier: Modifier) {
    val context = LocalContext.current
    // §4: ambient loops pause under battery saver. S10 adds a reactive
    // observer; this snapshot already pauses loops entered while saving.
    val powerSaver = remember {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }
    if (powerSaver) return
    val transition = rememberInfiniteTransition(label = "hero-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-glow",
    )
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = glow), Color.Transparent),
                center = center,
                radius = radius * 1.25f,
            ),
            radius = radius * 1.25f,
            center = center,
        )
    }
}
