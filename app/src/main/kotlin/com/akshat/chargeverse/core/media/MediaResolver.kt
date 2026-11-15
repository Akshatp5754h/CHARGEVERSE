package com.akshat.chargeverse.core.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.akshat.chargeverse.data.db.AnimationCategory
import com.akshat.chargeverse.data.db.MediaType
import java.io.File
import java.util.UUID

/**
 * Copies SAF-imported media into the app's private storage and extracts
 * metadata. Private copies keep animations working after reboots without
 * requiring any storage permission.
 */
class MediaResolver(private val context: Context) {

    data class Resolved(
        val uri: String,
        val type: MediaType,
        val title: String,
        val sizeBytes: Long,
        val durationMs: Long,
        val widthPx: Int,
        val heightPx: Int,
    )

    /** Accepted mime types for the SAF picker. */
    val acceptedMimeTypes = arrayOf(
        "video/*", "image/gif", "image/webp", "image/png", "image/jpeg",
    )

    fun resolveAndCopy(source: Uri, category: AnimationCategory): Resolved {
        val resolver = context.contentResolver
        resolver.openInputStream(source)?.use { input ->
            val dir = mediaDir()
            val name = "cv_${UUID.randomUUID()}${extensionFor(source, resolver)}"
            val out = File(dir, name)
            out.outputStream().use { input.copyTo(it) }
            return inspect(Uri.fromFile(out), out, category)
        } ?: throw IllegalStateException("Cannot open selected file")
    }

    fun mediaDir(): File = File(context.filesDir, "media").apply { mkdirs() }

    private fun extensionFor(uri: Uri, resolver: ContentResolver): String {
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return if (!ext.isNullOrEmpty() && ext.length <= 5) ".$ext" else ".bin"
    }

    fun inspect(uri: Uri, file: File, category: AnimationCategory): Resolved {
        val mime = context.contentResolver.getType(uri) ?: ""
        val type = when {
            mime.startsWith("video/") || mime == "video" -> MediaType.VIDEO
            mime == "image/gif" || file.extension == "gif" -> MediaType.GIF
            mime == "image/webp" || file.extension == "webp" -> MediaType.WEBP
            mime.startsWith("image/") -> MediaType.IMAGE
            else -> MediaType.IMAGE
        }
        var durationMs = 0L
        var width = 0
        var height = 0
        if (type == MediaType.VIDEO || type == MediaType.GIF || type == MediaType.WEBP) {
            runCatching {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(context, uri)
                    durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                }
            }
        } else {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                width = opts.outWidth
                height = opts.outHeight
            }
        }
        val title = file.nameWithoutExtension
            .removePrefix("cv_")
            .take(24)
            .ifEmpty { category.name.lowercase().replace('_', ' ') }
        return Resolved(
            uri = uri.toString(),
            type = type,
            title = title,
            sizeBytes = file.length(),
            durationMs = durationMs,
            widthPx = width,
            heightPx = height,
        )
    }

    /** Deletes the private copy of an imported media item. */
    fun deleteCopy(uri: String) {
        if (uri.startsWith("file://")) {
            runCatching { File(Uri.parse(uri).path!!).delete() }
        }
    }

    private inline fun MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> Unit) {
        try {
            block(this)
        } finally {
            runCatching { release() }
        }
    }

    /** Total bytes used by imported media (for storage stats in settings). */
    fun totalBytesUsed(): Long = mediaDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Used by the audio importer (Sprint: sound studio). */
    fun resolveAudio(source: Uri, name: String): File {
        val dir = File(context.filesDir, "sounds").apply { mkdirs() }
        val out = File(dir, "snd_${UUID.randomUUID()}.audio")
        context.contentResolver.openInputStream(source)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Cannot open selected audio")
        return out
    }
}
