package com.akshat.chargeverse.charging

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.db.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Plays the active profile's media on a dedicated ExoPlayer instance used by
 * the charging experience. Videos loop; GIFs/WebP animate via Coil; stills
 * render statically (all handled in the Compose layer — this engine drives
 * video audio/progress and exposes playback state).
 */
class ChargingMediaEngine(context: Context) {

    /** Exposed read-only for the PlayerView binding. */
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    /** Current loop state — wired to the user's loop setting. */
    var looping: Boolean = true

    /** Playback speed multiplier from the profile. */
    var speed: Float = 1f
        set(value) {
            field = value
            player.setPlaybackSpeed(value)
        }

    var onFirstFrame: (() -> Unit)? = null

    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                onFirstFrame?.invoke()
            }
        })
    }

    /**
     * Prepares a video media item. Returns true when the item is playable.
     * GIFs and stills don't need ExoPlayer (Coil renders them) — returns false.
     */
    suspend fun prepareVideo(item: MediaItemEntity): Boolean = withContext(Dispatchers.Main) {
        if (item.type != MediaType.VIDEO) return@withContext false
        suspendCancellableCoroutine { cont ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(item.uri))
                .build()
            player.setMediaItem(mediaItem)
            player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.setPlaybackSpeed(speed)
            player.volume = 1f
            player.prepare()
            player.playWhenReady = false
            cont.resume(true)
        }
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
    }

    fun release() {
        player.release()
    }
}

/** Effective media for a profile, resolving the Room media row. */
suspend fun mediaForProfile(profile: AnimationProfileEntity?): MediaItemEntity? {
    val db = com.akshat.chargeverse.core.di.ServiceLocator.db
    if (profile == null) return null
    return db.mediaItemDao().byId(profile.mediaItemId)
}
