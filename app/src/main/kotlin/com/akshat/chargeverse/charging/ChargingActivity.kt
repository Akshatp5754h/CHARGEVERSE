package com.akshat.chargeverse.charging

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.akshat.chargeverse.battery.ChargeSource
import com.akshat.chargeverse.battery.ChargingEvent
import com.akshat.chargeverse.core.theme.ElectricGreen
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonMagenta
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.Surface
import com.akshat.chargeverse.core.theme.Void
import com.akshat.chargeverse.core.theme.batteryAccent
import com.akshat.chargeverse.core.theme.cvColors
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.db.MediaType
import com.akshat.chargeverse.data.prefs.ChargingOverlay
import com.akshat.chargeverse.ui.charging.ChargingViewModel
import com.akshat.chargeverse.ui.components.CvSecondaryButton
import com.akshat.chargeverse.ui.components.cvPressScale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Design-system scrim for text-bearing overlays on imagery (contract §1):
 * 70% black minimum. All overlay pills use this — never raw Color.White.
 */
private val OverlayScrim = Color(0xB3000000)

/**
 * Full-screen charging experience shown while plugged in (also over the lock
 * screen when the system launches it from the full-screen intent). Plays the
 * active profile's media (video/GIF/WebP/image/Lottie) with the profile's
 * transforms and fit/fill mode, renders the settings-driven overlay pills
 * (clock/date/status/temperature/battery ring), and falls back to the
 * animated ambient gradient + ring when no media exists or playback failed.
 *
 * v2/S4: single tap toggles a control bar (exit / fit-fill / profile chips)
 * that auto-hides after 3 s; hidden controls leave pure immersive media.
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
        val settings by vm.sessionSettings.collectAsState()
        val profiles by vm.profiles.collectAsState()

        val percent = reading?.levelPercent ?: -1
        val accent = batteryAccent(percent.coerceIn(0, 100))
        val colors = MaterialTheme.cvColors
        val overlays = settings.chargingOverlays

        // Media engine (video) lives as long as this screen.
        val engine = remember { ChargingMediaEngine(this) }
        DisposableEffect(Unit) {
            onDispose { engine.release() }
        }

        // ---- control bar (single tap toggles, auto-hides after 3 s) ----
        var controlsVisible by remember { mutableStateOf(false) }
        LaunchedEffect(controlsVisible) {
            if (controlsVisible) {
                delay(CONTROLS_HIDE_DELAY_MS)
                controlsVisible = false
            }
        }

        // ---- playback error state (one auto-retry lives in the engine) ----
        var playbackFailed by remember { mutableStateOf(false) }
        var retryToken by remember { mutableStateOf(0) }

        // ---- media crossfade state ----
        var frontMedia by remember { mutableStateOf<MediaItemEntity?>(null) }
        var ghostMedia by remember { mutableStateOf<MediaItemEntity?>(null) }
        val frontAlpha = remember { Animatable(0f) }
        val ghostAlpha = remember { Animatable(1f) }
        var readyId by remember { mutableStateOf<Long?>(null) }
        var mediaEnded by remember { mutableStateOf(false) }

        // Engine callbacks → composable state (all on the main thread).
        LaunchedEffect(Unit) {
            engine.onFirstFrame = {
                engine.currentVideoId?.let { id ->
                    if (id == frontMedia?.id) readyId = id
                }
            }
            engine.onEnded = { mediaEnded = true }
            engine.onPlaybackError = { playbackFailed = true }
        }

        // Media switch choreography. Videos: fade the layer out while the old
        // clip still plays, then swap the (single) player to the new item and
        // fade back in on its first frame — no black flash. Independent
        // renderers (Coil/Lottie) are kept as a "ghost" layer and crossfaded
        // out only once the new content has rendered.
        LaunchedEffect(media) {
            val target = media
            if (target?.id == frontMedia?.id) return@LaunchedEffect
            val current = frontMedia
            if (current == null) {
                if (target == null) {
                    engine.stop()
                    return@LaunchedEffect
                }
                engine.prepareAndPlay(target)
                frontMedia = target
                frontAlpha.snapTo(0f)
                readyId = null
            } else if (current.type == MediaType.VIDEO) {
                frontAlpha.animateTo(0f, tween(FADE_OUT_MS))
                engine.prepareAndPlay(target)
                frontMedia = target
                frontAlpha.snapTo(0f)
                readyId = null
            } else {
                engine.prepareAndPlay(target)
                ghostMedia = current
                ghostAlpha.snapTo(frontAlpha.value)
                frontMedia = target
                frontAlpha.snapTo(0f)
                readyId = null
            }
            playbackFailed = false
            mediaEnded = false
        }

        // Fade the front layer in on first frame; crossfade the ghost out at
        // the same time so a slow loader never exposes a black gap.
        LaunchedEffect(frontMedia, readyId) {
            val front = frontMedia
            val ghost = ghostMedia
            if (front == null) {
                if (ghost != null) {
                    ghostAlpha.animateTo(0f, tween(FADE_OUT_MS))
                    ghostMedia = null
                }
                return@LaunchedEffect
            }
            if (readyId != front.id) return@LaunchedEffect
            coroutineScope {
                if (ghost != null) {
                    launch {
                        ghostAlpha.animateTo(0f, tween(FADE_OUT_MS))
                        ghostMedia = null
                    }
                }
                frontAlpha.animateTo(1f, tween(FADE_IN_MS))
            }
        }

        // Manual retry (fallback screen): re-prepare / re-decode the same item.
        LaunchedEffect(retryToken) {
            if (retryToken == 0) return@LaunchedEffect
            val m = media ?: return@LaunchedEffect
            playbackFailed = false
            mediaEnded = false
            engine.prepareAndPlay(m)
        }

        // Loop + speed settings apply live (no re-prepare, no restart).
        LaunchedEffect(settings.loopAnimation) { engine.setLooping(settings.loopAnimation) }
        LaunchedEffect(profile?.playbackSpeed) {
            profile?.let { engine.speed = it.playbackSpeed }
        }

        // Disconnect ends the experience (service stops itself separately).
        LaunchedEffect(Unit) {
            vm.events.collect { event ->
                if (event == ChargingEvent.DISCONNECTED) finish()
            }
        }

        // ---- overlay content ----
        var clockText by remember { mutableStateOf("--:--") }
        LaunchedEffect(Unit) {
            val format = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            while (true) {
                clockText = format.format(LocalTime.now())
                delay(CLOCK_TICK_MS)
            }
        }
        val dateText = remember {
            DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(LocalDate.now())
        }
        val statusText = run {
            val label = reading?.statusLabel ?: "Charging"
            when (reading?.source) {
                ChargeSource.AC -> "$label · AC"
                ChargeSource.USB -> "$label · USB"
                ChargeSource.WIRELESS -> "$label · Wireless"
                ChargeSource.DOCK -> "$label · Dock"
                ChargeSource.OTHER, ChargeSource.NONE, null -> label
            }
        }
        val tempText = reading?.temperatureC?.let { "%.1f °C".format(it) } ?: "Not reported"

        Box(
            Modifier
                .fillMaxSize()
                .background(Color((profile?.backgroundHex ?: 0xFF050510).toInt()))
                .pointerInput(Unit) {
                    detectTapGestures { controlsVisible = !controlsVisible }
                },
        ) {
            // Layer 0: ambient fallback (animated gradient + ring), visible
            // whenever nothing is on screen or playback failed after retry.
            if (frontMedia == null || playbackFailed) {
                AmbientFallback(
                    percent = percent,
                    accent = accent,
                    showRing = ChargingOverlay.BATTERY in overlays &&
                        profile?.showBatteryOverlay != false,
                    showPercent = profile?.showPercent != false,
                    isFull = reading?.isFull == true,
                    showRetry = playbackFailed,
                    onRetry = { retryToken++ },
                )
            }

            // Layer 1a: ghost of the previous still/gif/lottie, fading out.
            ghostMedia?.let { ghost ->
                MediaLayer(
                    media = ghost,
                    profile = profile,
                    engine = engine,
                    loop = settings.loopAnimation,
                    alpha = ghostAlpha.value,
                    fill = profile?.fillMode ?: true,
                    retryToken = retryToken,
                    onReady = { },
                    onError = { _, _ -> },
                )
            }

            // Layer 1b: the media actually being shown.
            frontMedia?.let { front ->
                MediaLayer(
                    media = front,
                    profile = profile,
                    engine = engine,
                    loop = settings.loopAnimation,
                    alpha = frontAlpha.value,
                    fill = profile?.fillMode ?: true,
                    retryToken = retryToken,
                    onReady = { id -> if (id == frontMedia?.id) readyId = id },
                    onError = { id, _ -> if (id == frontMedia?.id) playbackFailed = true },
                )
            }

            // Layer 2: subtle breathing overlay once a non-looping video ends.
            if (mediaEnded && frontMedia != null && !playbackFailed) {
                BreathingOverlay()
            }

            // Layer 3: battery ring + numeral, gated by settings and profile.
            if (ChargingOverlay.BATTERY in overlays && profile?.showBatteryOverlay != false) {
                BatteryOverlay(
                    percent = percent,
                    accent = accent,
                    showPercent = profile?.showPercent != false,
                    isFull = reading?.isFull == true,
                    mediaPresent = frontMedia != null,
                )
            }

            // Layer 4: settings-driven overlay pills (top corners).
            if (ChargingOverlay.CLOCK in overlays) {
                OverlayPill(
                    clockText,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 32.dp, start = 20.dp),
                )
            }
            if (ChargingOverlay.DATE in overlays) {
                OverlayPill(
                    dateText,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 32.dp, end = 20.dp),
                )
            }
            if (ChargingOverlay.STATUS in overlays) {
                OverlayPill(
                    statusText,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 76.dp, start = 20.dp),
                )
            }
            if (ChargingOverlay.TEMP in overlays) {
                OverlayPill(
                    tempText,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = 20.dp),
                )
            }

            // Layer 5: profile text overlay — D28 fix: placed with the SAME
            // fraction-of-size convention as the media layer (layout
            // placement, positive = down), never the old -100f magic.
            val p = profile
            p?.textOverlay?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .textFractionPlacement(
                            offsetX = p.offsetX,
                            offsetY = p.offsetY,
                        )
                        .padding(bottom = 120.dp),
                )
            }

            // Layer 6: control bar (exit / fit-fill / profile chips).
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(FADE_OUT_MS)) +
                    slideInVertically(tween(FADE_OUT_MS)) { it / 3 },
                exit = fadeOut(tween(FADE_OUT_MS)) +
                    slideOutVertically(tween(FADE_OUT_MS)) { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(OverlayScrim, RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CvSecondaryButton(label = "Exit") { finish() }
                        Spacer(Modifier.weight(1f))
                        val fillNow = profile?.fillMode ?: true
                        CvSecondaryButton(
                            label = if (fillNow) "Fill" else "Fit",
                            onClick = { vm.setFillMode(!fillNow) },
                        )
                    }
                    if (profiles.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(profiles, key = { it.id }) { p ->
                                ProfileChip(
                                    profile = p,
                                    isSelected = p.id == profile?.id,
                                    onSelect = { vm.selectProfile(p) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * One media renderer (video / Coil still-or-GIF / Lottie) for one media
     * item, positioned with the profile transforms and [alpha]-faded by the
     * crossfade machinery. [onReady] fires when the content has rendered its
     * first frame; [onError] when the media failed to decode/play.
     */
    @Composable
    private fun MediaLayer(
        media: MediaItemEntity,
        profile: AnimationProfileEntity?,
        engine: ChargingMediaEngine,
        loop: Boolean,
        alpha: Float,
        fill: Boolean,
        retryToken: Int,
        onReady: (Long) -> Unit,
        onError: (Long, Throwable?) -> Unit,
    ) {
        val transform = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = profile?.scale ?: 1f
                scaleY = profile?.scale ?: 1f
                translationX = (profile?.offsetX ?: 0f) * size.width
                translationY = (profile?.offsetY ?: 0f) * size.height
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
                            player = engine.player
                        }
                    },
                    update = { view ->
                        // v2/S4: honour the profile's fit/fill choice.
                        view.resizeMode =
                            if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        // SurfaceView ignores ancestor layer alpha; set it on
                        // the surface view itself so crossfades reach video.
                        setVideoAlpha(view, alpha)
                    },
                    modifier = transform,
                )
            }
            MediaType.GIF, MediaType.WEBP, MediaType.IMAGE -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val painter = rememberAsyncImagePainter(
                    remember(media.id, media.uri, retryToken) {
                        ImageRequest.Builder(context).data(media.uri).build()
                    },
                )
                LaunchedEffect(painter.state) {
                    when (val state = painter.state) {
                        is AsyncImagePainter.State.Success -> onReady(media.id)
                        is AsyncImagePainter.State.Error -> onError(media.id, state.result.throwable)
                        else -> Unit
                    }
                }
                Image(
                    painter = painter,
                    contentDescription = media.title,
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                    modifier = transform.graphicsLayer { this.alpha = alpha },
                )
            }
            MediaType.LOTTIE -> {
                val spec = remember(media.uri, retryToken) {
                    val path = android.net.Uri.parse(media.uri).path
                    if (path != null && media.uri.startsWith("file://")) {
                        LottieCompositionSpec.File(path)
                    } else {
                        LottieCompositionSpec.Url(media.uri)
                    }
                }
                val result = rememberLottieComposition(spec)
                LaunchedEffect(result.value, result.error) {
                    if (result.value != null) onReady(media.id)
                    result.error?.let { onError(media.id, it) }
                }
                LottieAnimation(
                    composition = result.value,
                    isPlaying = true,
                    speed = profile?.playbackSpeed ?: 1f,
                    iterations = if (loop) LottieConstants.IterateForever else 1,
                    alignment = Alignment.Center,
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                    modifier = transform.graphicsLayer { this.alpha = alpha },
                )
            }
        }
    }

    /**
     * Chip in the control bar's profile row. Contract §3: Surface fill,
     * 12.dp radius, textSecondary label; selected = NeonBlue fill + OnAccent
     * label, plus a `selected` semantic so colour is never the only signal.
     */
    @Composable
    private fun ProfileChip(
        profile: AnimationProfileEntity,
        isSelected: Boolean,
        onSelect: () -> Unit,
    ) {
        val colors = MaterialTheme.cvColors
        Box(
            Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) NeonBlue else Surface, RoundedCornerShape(12.dp))
                .cvPressScale(onClick = onSelect)
                .semantics { selected = isSelected }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) colors.onAccent else colors.textSecondary,
                maxLines = 1,
            )
        }
    }

    /** Real Lottie/Coil-independent pill: scrim background + ramp text. */
    @Composable
    private fun OverlayPill(text: String, modifier: Modifier = Modifier) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.cvColors.textPrimary,
            modifier = modifier
                .background(OverlayScrim, CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }

    /**
     * Ambient fallback: animated gradient + battery ring (and a Retry button
     * after a playback failure). Never a bare black screen (v2/S4 item 7).
     */
    @Composable
    private fun AmbientFallback(
        percent: Int,
        accent: Color,
        showRing: Boolean,
        showPercent: Boolean,
        isFull: Boolean,
        showRetry: Boolean,
        onRetry: () -> Unit,
    ) {
        Box(Modifier.fillMaxSize().background(Void)) {
            AnimatedAmbientGradient(Modifier.matchParentSize())
            if (showRing) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    BatteryOverlay(
                        percent = percent,
                        accent = accent,
                        showPercent = showPercent,
                        isFull = isFull,
                        mediaPresent = false,
                    )
                }
            }
            if (showRetry) {
                CvSecondaryButton(
                    label = "Retry",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp),
                    onClick = onRetry,
                )
            }
        }
    }

    /** Slow two-tone sweep behind the fallback screen (contract §4 cadence). */
    @Composable
    private fun AnimatedAmbientGradient(modifier: Modifier = Modifier) {
        val transition = rememberInfiniteTransition(label = "ambient")
        val angle by transition.animateFloat(
            initialValue = -30f,
            targetValue = 30f,
            animationSpec = infiniteRepeatable(
                tween(2000, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "ambientAngle",
        )
        Canvas(modifier) {
            val rad = Math.toRadians(angle.toDouble())
            val cx = size.width / 2f
            val cy = size.height / 2f
            val reach = size.width + size.height
            drawRect(
                brush = Brush.linearGradient(
                    listOf(NeonBlue.copy(alpha = 0.10f), NeonPurple.copy(alpha = 0.10f), Color.Transparent),
                    start = Offset(cx, cy),
                    end = Offset(cx + (cos(rad) * reach).toFloat(), cy + (sin(rad) * reach).toFloat()),
                ),
            )
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, NeonMagenta.copy(alpha = 0.06f), Color.Transparent),
                    start = Offset(cx, cy),
                    end = Offset(cx - (cos(rad) * reach).toFloat(), cy - (sin(rad) * reach).toFloat()),
                ),
            )
        }
    }

    /** Gentle luminance breathing over held media (decorative, no text). */
    @Composable
    private fun BreathingOverlay() {
        val transition = rememberInfiniteTransition(label = "breath")
        val alpha by transition.animateFloat(
            initialValue = 0.05f,
            targetValue = 0.14f,
            animationSpec = infiniteRepeatable(
                tween(2000, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "breathAlpha",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(NeonBlue.copy(alpha = alpha), Color.Transparent, NeonPurple.copy(alpha = alpha))
                    )
                ),
        )
    }

    /** Battery ring + percent, only when the profile and settings want it. */
    @Composable
    private fun BatteryOverlay(
        percent: Int,
        accent: Color,
        showPercent: Boolean,
        isFull: Boolean,
        mediaPresent: Boolean,
    ) {
        val colors = MaterialTheme.cvColors
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
                    .then(
                        if (percent >= 0) {
                            Modifier.semantics {
                                contentDescription = "Battery $percent percent"
                            }
                        } else {
                            Modifier
                        }
                    )
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
                    color = colors.textPrimary,
                    modifier = Modifier
                        .background(OverlayScrim, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                val statusLine = when {
                    percent < 0 -> ""
                    isFull -> "Fully charged"
                    else -> ""
                }
                if (statusLine.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        statusLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        modifier = Modifier
                            .background(OverlayScrim, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    /**
     * D28 fix: place a node by the same fraction-of-size convention the media
     * layer uses (offset × the full-screen size, positive = down), via layout
     * placement instead of the old `offsetY * -100f` translation.
     */
    private fun Modifier.textFractionPlacement(offsetX: Float, offsetY: Float): Modifier =
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val dx = (offsetX * constraints.maxWidth).roundToInt()
            val dy = (offsetY * constraints.maxHeight).roundToInt()
            layout(placeable.width, placeable.height) {
                placeable.place(dx, dy)
            }
        }

    private suspend fun ChargingMediaEngine.prepareAndPlay(item: MediaItemEntity?) {
        if (item == null || item.type != MediaType.VIDEO) {
            // The player only ever backs videos: drop any loaded video when
            // switching to Coil/Lottie content or nothing, so stale audio
            // never lingers after the PlayerView unmounts.
            stop()
            return
        }
        prepareVideo(item)
        play()
    }

    /**
     * PlayerView hosts a SurfaceView, which ignores ancestor RenderNode
     * alpha — walk down to it (or the leaf view) and set the alpha directly
     * (supported on SurfaceView since API 24; our minSdk is 29).
     */
    private fun setVideoAlpha(view: View, alpha: Float) {
        view.alpha = alpha
        var child: View? = view
        while (child is ViewGroup && child.childCount > 0) {
            child = child.getChildAt(0)
        }
        child?.alpha = alpha
    }

    private companion object {
        /** Control bar re-tap window (S4 item 6). */
        const val CONTROLS_HIDE_DELAY_MS = 3_000L
        /** Corner overlay pill refresh cadence — HH:mm only needs a minute. */
        const val CLOCK_TICK_MS = 15_000L
        /** Crossfade timings (S4 item 3). */
        const val FADE_IN_MS = 280
        const val FADE_OUT_MS = 150
    }
}
