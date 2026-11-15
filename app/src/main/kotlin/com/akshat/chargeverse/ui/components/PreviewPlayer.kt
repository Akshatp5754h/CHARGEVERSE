package com.akshat.chargeverse.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.db.MediaType

/**
 * Real media rendering for S2 previews and posters.
 *
 * - VIDEO: a dedicated ExoPlayer, muted and looping; released the moment the
 *   composable leaves composition so no decoder/codec leaks behind Home.
 * - GIF / animated WEBP: Coil animates them via the app-wide
 *   [ImageDecoderDecoder] (registered in ChargeverseApp).
 * - IMAGE: plain Coil.
 * - LOTTIE: lottie-compose, looping per the user's [loop] preference.
 *
 * [CvMediaPoster] is the still variant used in grids/rows: videos render as
 * their first frame through coil-video's [VideoFrameDecoder] — a poster row
 * must never start six players.
 */

@Composable
fun CvMediaPreview(
    media: MediaItemEntity,
    modifier: Modifier = Modifier,
    contentDesc: String? = null,
    loop: Boolean = true,
) {
    when (media.type) {
        MediaType.VIDEO -> VideoPreview(media, modifier, contentDesc, loop)
        MediaType.LOTTIE -> LottiePreview(media, modifier, loop)
        MediaType.GIF, MediaType.WEBP, MediaType.IMAGE ->
            CoilStill(media, modifier, contentDesc)
    }
}

/** Still poster: video shows its first frame, never playback. */
@Composable
fun CvMediaPoster(
    media: MediaItemEntity,
    modifier: Modifier = Modifier,
    contentDesc: String? = null,
) {
    when (media.type) {
        MediaType.LOTTIE -> LottiePreview(media, modifier, loop = true)
        else -> CoilStill(media, modifier, contentDesc)
    }
}

@Composable
private fun VideoPreview(
    media: MediaItemEntity,
    modifier: Modifier,
    contentDesc: String?,
    loop: Boolean,
) {
    val context = LocalContext.current
    val player = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            // Inline preview is silent; the charging experience owns audio.
            volume = 0f
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(media.uri) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        modifier = modifier.semantics {
            if (contentDesc != null) this.contentDescription = contentDesc
        },
    )
}

@Composable
private fun LottiePreview(
    media: MediaItemEntity,
    modifier: Modifier,
    loop: Boolean,
) {
    val spec = remember(media.uri) {
        val path = Uri.parse(media.uri).path
        if (media.uri.startsWith("file://") && path != null) {
            LottieCompositionSpec.File(path)
        } else {
            LottieCompositionSpec.Url(media.uri)
        }
    }
    val composition by rememberLottieComposition(spec)
    LottieAnimation(
        composition = composition,
        isPlaying = true,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun CoilStill(
    media: MediaItemEntity,
    modifier: Modifier,
    contentDesc: String?,
) {
    AsyncImage(
        model = media.uri,
        contentDescription = contentDesc,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
