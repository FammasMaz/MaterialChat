package com.materialchat.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Ownership rules for image files the app stores under `filesDir`.
 *
 * Picked attachments live in [PICKED_DIR]; generated images live in [GENERATED_DIR].
 * Only files inside those two directories are ever deleted.
 */
object AttachmentFiles {
    private const val TAG = "AttachmentFiles"
    const val PICKED_DIR = "chat_attachments"
    const val GENERATED_DIR = "generated_images"

    fun isOwned(context: Context, uriString: String): Boolean = ownedFile(context, uriString) != null

    /** Deletes the file behind [uriString] if it is one of ours. Returns true when a file was removed. */
    fun deleteIfOwned(context: Context, uriString: String): Boolean {
        val file = ownedFile(context, uriString) ?: return false
        if (!file.exists()) return false
        val deleted = file.delete()
        if (!deleted) Log.w(TAG, "Could not delete ${file.name}")
        return deleted
    }

    private fun ownedFile(context: Context, uriString: String): File? {
        if (uriString.isBlank()) return null
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val path = when (parsed.scheme?.lowercase()) {
            "file" -> parsed.path
            null, "" -> uriString
            else -> null
        } ?: return null
        val file = File(path)
        val parent = runCatching { file.parentFile?.canonicalPath }.getOrNull() ?: return null
        val filesDir = context.applicationContext.filesDir
        val owned = listOf(PICKED_DIR, GENERATED_DIR).any { dir ->
            runCatching { File(filesDir, dir).canonicalPath }.getOrNull() == parent
        }
        return file.takeIf { owned }
    }
}
