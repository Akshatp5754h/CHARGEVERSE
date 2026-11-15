package com.akshat.chargeverse.charging

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.db.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Plays the active profile's media on a dedicated ExoPlayer instance used by
 * the charging experience (v2/S4). Videos loop with REPEAT_MODE_ONE, or play
 * once and HOLD their last frame at STATE_ENDED (playWhenReady = false) so a
 * non-looping session never shows a frozen-looking screen — the Compose layer
 * adds a breathing overlay on top. GIFs/WebP animate via Coil; stills render
 * statically; Lottie is handled in the Compose layer — this engine drives
 * video audio/progress and exposes first-frame / end / error callbacks.
 *
 * Error recovery: on a playback error the same item is re-prepared exactly
 * once; if it fails again the caller is told via [onPlaybackError] and shows
 * the ambient fallback with a manual Retry.
 */
class ChargingMediaEngine(context: Context) {

    /** Exposed read-only for the PlayerView binding. */
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    /** Loop state — wired to the user's loop setting; changes apply live. */
    var looping: Boolean = true
        private set

    /** Playback speed multiplier from the profile. */
    var speed: Float = 1f
        set(value) {
            field = value
            player.setPlaybackSpeed(value)
        }

    /** Fires on the main thread when the video renders its first frame. */
    var onFirstFrame: (() -> Unit)? = null

    /** Fires when a non-looping video reaches its end (last frame held). */
    var onEnded: (() -> Unit)? = null

    /** Fires after the automatic single retry also failed (v2/S4 recovery). */
    var onPlaybackError: ((Throwable) -> Unit)? = null

    /** id of the MediaItemEntity currently prepared, for callback routing. */
    var currentVideoId: Long? = null
        private set

    /** The prepared item, for the automatic re-prepare retry. */
    private var lastItem: MediaItemEntity? = null

    /** One auto-retry is available per prepared item until it is spent. */
    private var retryAvailable = false

    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                onFirstFrame?.invoke()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !looping) {
                    // Hold the last frame: never loop a one-shot video.
                    player.playWhenReady = false
                    onEnded?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val item = lastItem
                if (item != null && retryAvailable) {
                    // Auto-retry once: re-prepare the same item and resume.
                    retryAvailable = false
                    reprepare(item, playWhenReady = true)
                } else {
                    onPlaybackError?.invoke(error)
                }
            }
        })
    }

    /** Updates the loop mode live (repeat mode follows when a video is loaded). */
    fun setLooping(value: Boolean) {
        looping = value
        player.repeatMode = if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Prepares a video media item. Returns true when the item is playable.
     * GIFs and stills don't need ExoPlayer (Coil renders them) — returns false
     * without touching the player, so callers can stop it explicitly.
     */
    suspend fun prepareVideo(item: MediaItemEntity): Boolean = withContext(Dispatchers.Main) {
        if (item.type != MediaType.VIDEO) {
            currentVideoId = null
            return@withContext false
        }
        lastItem = item
        retryAvailable = true
        reprepare(item, playWhenReady = false)
        true
    }

    /** Manual Retry from the fallback screen: re-prepare with a fresh auto-retry. */
    fun retry() {
        val item = lastItem ?: return
        retryAvailable = true
        reprepare(item, playWhenReady = true)
    }

    private fun reprepare(item: MediaItemEntity, playWhenReady: Boolean) {
        currentVideoId = item.id
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(item.uri))
            .build()
        player.setMediaItem(mediaItem)
        player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setPlaybackSpeed(speed)
        player.volume = 1f
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun play() {
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        currentVideoId = null
        lastItem = null
        retryAvailable = false
    }

    fun release() {
        player.release()
    }
}
