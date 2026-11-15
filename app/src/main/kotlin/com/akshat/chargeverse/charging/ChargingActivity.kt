package com.akshat.chargeverse.charging

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.akshat.chargeverse.battery.ChargingEvent
import com.akshat.chargeverse.core.theme.ElectricGreen
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.batteryAccent
import com.akshat.chargeverse.data.db.MediaType
import com.akshat.chargeverse.ui.charging.ChargingViewModel

/**
 * Full-screen charging experience shown while plugged in (also over the lock
 * screen when the system launches it from the full-screen intent). Plays the
 * active profile's media (video/GIF/WebP/image) with the profile's transforms,
 * falling back to the native pulsing charge animation when no profile exists.
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
        val profile by vm.profile.collectAsState()
        val media by vm.media.collectAsState()
        val sessionSettings by vm.sessionSettings.collectAsState()

        val percent = reading?.levelPercent ?: -1
        val accent = batteryAccent(percent.coerceIn(0, 100))

        // Media engine (video) lives as long as this screen.
        val engine = remember { ChargingMediaEngine(this) }
        DisposableEffect(Unit) {
            onDispose { engine.release() }
        }

        LaunchedEffect(media, profile) {
            val m = media
            if (m != null && profile != null) {
                engine.looping = sessionSettings.loopAnimation
                engine.speed = profile?.playbackSpeed ?: 1f
                val isVideo = engine.prepareVideo(m)
                if (isVideo) engine.play()
            }
        }

        LaunchedEffect(Unit) {
            vm.events.collect { event ->
                if (event == ChargingEvent.DISCONNECTED) finish()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color((profile?.backgroundHex ?: 0xFF050510).toInt())),
            contentAlignment = Alignment.Center,
        ) {
            // Layer 1: profile media (video / animated / still)
            ProfileMediaLayer(media = media, profile = profile, engine = engine)

            // Layer 2: battery overlay (ring + percent), only if profile wants it
            if (profile?.showBatteryOverlay != false) {
                BatteryOverlay(
                    percent = percent,
                    accent = accent,
                    showPercent = profile?.showPercent != false,
                    isFull = reading?.isFull == true,
                    mediaPresent = media != null,
                )
            }

            // Layer 3: text overlay
            profile?.textOverlay?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY = (profile?.offsetY ?: 0f) * -100f
                        }
                        .padding(0.dp, 0.dp, 0.dp, 120.dp)
                )
            }
        }
    }

    @Composable
    private fun ProfileMediaLayer(
        media: com.akshat.chargeverse.data.db.MediaItemEntity?,
        profile: com.akshat.chargeverse.data.db.AnimationProfileEntity?,
        engine: ChargingMediaEngine,
    ) {
        if (media == null) return
        val scale = profile?.scale ?: 1f
        val offsetX = profile?.offsetX ?: 0f
        val offsetY = profile?.offsetY ?: 0f

        val transform = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX * size.width
                translationY = offsetY * size.height
            }

        when (media.type) {
            MediaType.VIDEO -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            player = engine.player
                        }
                    },
                    modifier = transform,
                )
            }
            MediaType.GIF, MediaType.WEBP, MediaType.IMAGE -> {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = transform,
                )
            }
            MediaType.LOTTIE -> {
                // Lottie composition support arrives with Sprint 7 asset import.
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.title,
                    contentScale = ContentScale.Fit,
                    modifier = transform,
                )
            }
        }
    }

    @Composable
    private fun BatteryOverlay(
        percent: Int,
        accent: Color,
        showPercent: Boolean,
        isFull: Boolean,
        mediaPresent: Boolean,
    ) {
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(if (mediaPresent) 200.dp else 320.dp)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = (size.minDimension / 2f - 24f) * ringScale
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = glowAlpha * 0.4f), Color.Transparent),
                        center = center,
                        radius = radius * 1.25f,
                    ),
                    radius = radius * 1.25f,
                    center = center,
                )
                drawCircle(
                    color = Color(0x33FFFFFF),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 14f, cap = StrokeCap.Round),
                )
                if (percent >= 0) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(NeonBlue, accent, NeonPurple), center = center),
                        startAngle = -90f,
                        sweepAngle = (percent.coerceIn(0, 100) / 100f) * 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 14f, cap = StrokeCap.Round),
                    )
                }
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
            if (showPercent) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${if (percent >= 0) percent else "—"}%",
                    fontSize = if (mediaPresent) 44.sp else 72.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    when {
                        percent < 0 -> ""
                        isFull -> "Fully charged"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
        }
    }
}
