package com.materialchat.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Hands a photo to the system camera app and takes it back.
 *
 * The camera writes straight into the app's own attachment directory through a [FileProvider]
 * URI, so the result is already a file we own: no copy from a content URI, no size limit from a
 * thumbnail, and nothing to clean up on the way in. The file is created before the camera opens
 * (the camera needs a target) and deleted again when the user backs out, so a cancelled capture
 * never leaves an empty image behind.
 *
 * No CAMERA permission is involved: `ACTION_IMAGE_CAPTURE` runs in the camera app, which holds
 * that permission itself. The app must not declare `android.permission.CAMERA`, or Android would
 * start requiring it for this intent.
 */
object CameraCapture {
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /** Where a capture lands, inside the directory the attachment importer already owns. */
    private const val CAPTURE_DIR = AttachmentFiles.PICKED_DIR

    /** True when some app can actually take a picture; false on a device with no camera app. */
    fun isAvailable(context: Context): Boolean = runCatching {
        context.packageManager
            .resolveActivity(captureIntent(null), 0) != null
    }.getOrDefault(false)

    /**
     * Creates the file the camera will write to and returns both it and the intent to launch.
     * Returns null when no camera app can handle the intent, so callers can tell the user
     * instead of launching into nothing.
     */
    fun prepare(context: Context): Capture? = runCatching {
        val dir = File(context.filesDir, CAPTURE_DIR).apply { mkdirs() }
        val file = File(dir, "camera_${UUID.randomUUID()}.jpg")
        if (!file.createNewFile()) return null
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val intent = captureIntent(uri) ?: run {
            file.delete()
            return null
        }
        if (context.packageManager.resolveActivity(intent, 0) == null) {
            file.delete()
            return null
        }
        Capture(file, uri, intent)
    }.getOrNull()

    /** Removes a capture the user abandoned. Safe to call on a file that no longer exists. */
    fun discard(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
    }

    private fun captureIntent(uri: Uri?): Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        if (uri != null) {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** A prepared capture: the empty file, the URI the camera writes to, and the intent to send. */
    data class Capture(val file: File, val uri: Uri, val intent: Intent)
}
