package com.akshat.chargeverse.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays charging sounds via MediaPlayer. Single-instance playback: a new
 * sound always replaces the current one. Honors mute + volume.
 */
class SoundEngine(private val context: Context) {

    private var player: MediaPlayer? = null

    @Volatile
    var volume: Float = 0.8f

    @Volatile
    var muted: Boolean = false

    suspend fun play(uri: Uri, loop: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        stop()
        if (muted) return@withContext false
        runCatching {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            when (uri.scheme) {
                "asset" -> {
                    val assetPath = uri.pathSegments.joinToString("/")
                    if (assetPath.isEmpty()) throw IllegalArgumentException("bad asset uri")
                    val fd = context.assets.openFd(assetPath)
                    p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    fd.close()
                }
                "file" -> p.setDataSource(File(uri.path!!).absolutePath)
                else -> p.setDataSource(context, uri)
            }
            p.isLooping = loop
            p.setVolume(volume, volume)
            p.prepare()
            p.setOnCompletionListener { release() }
            p.start()
            player = p
            true
        }.getOrDefault(false)
    }

    /** Preview a sound in the app, ignoring mute so the user can hear it. */
    suspend fun preview(uri: Uri): Boolean {
        val wasMuted = muted
        muted = false
        return try {
            play(uri, loop = false)
        } finally {
            muted = wasMuted
        }
    }

    fun stop() {
        runCatching {
            player?.let { if (it.isPlaying) it.stop() }
            release()
        }
    }

    private fun release() {
        runCatching {
            player?.release()
            player = null
        }
    }
}
