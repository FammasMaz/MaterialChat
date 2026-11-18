package com.materialchat.assistant.screen

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import com.materialchat.domain.model.Attachment
import com.materialchat.util.AttachmentFiles
import java.io.File
import java.util.UUID

/**
 * The assist screenshot's two transformations: the in-memory downscale done the moment it
 * arrives (a full-screen bitmap is tens of megabytes; the model never needs more than 1280px),
 * and the JPEG written into the owned attachments directory only when it is actually about to
 * be sent, so it lives and dies like a picked photo.
 */
object AssistScreenshots {
    private const val TAG = "MaterialChatAssistant"
    const val MAX_LONG_EDGE = 1280
    const val JPEG_QUALITY = 85

    /** The size a [width] x [height] image gets when its long edge is capped at [maxLongEdge]. */
    fun targetSize(width: Int, height: Int, maxLongEdge: Int = MAX_LONG_EDGE): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxLongEdge || longest <= 0) return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        val scale = maxLongEdge.toFloat() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /**
     * Returns a software bitmap whose long edge is at most [MAX_LONG_EDGE]. The original is
     * recycled whenever a new bitmap replaced it; the caller must not touch it afterwards.
     */
    fun downscale(original: Bitmap): Bitmap {
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && original.config == Bitmap.Config.HARDWARE) {
            // A hardware bitmap cannot be scaled or compressed directly.
            val copy = original.copy(Bitmap.Config.ARGB_8888, false) ?: return original
            original.recycle()
            copy
        } else original
        val (width, height) = targetSize(source.width, source.height)
        if (width == source.width && height == source.height) return source
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    /**
     * Writes [bitmap] as a JPEG into the picked-attachments directory and returns it as an
     * [Attachment], or null when it could not be written (or was released meanwhile). The file
     * is owned by the app ([AttachmentFiles]), so removing the chip or deleting the message
     * deletes it. Blocking: call from an IO dispatcher.
     */
    fun writeAttachment(context: Context, bitmap: Bitmap): Attachment? {
        val directory = File(context.applicationContext.filesDir, AttachmentFiles.PICKED_DIR).apply { mkdirs() }
        val file = File(directory, "screen_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        val written = runCatching {
            synchronized(bitmap) {
                if (bitmap.isRecycled) return@runCatching false
                file.outputStream().buffered().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            }
        }.getOrElse {
            Log.w(TAG, "Could not write the screen capture", it)
            false
        }
        if (!written) {
            file.delete()
            return null
        }
        return Attachment(uri = Uri.fromFile(file).toString(), mimeType = "image/jpeg")
    }
}
