package com.materialchat.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

object GeneratedImageActions {
    fun share(context: Context, uriString: String, mimeType: String) {
        val shareUri = shareableUri(context, uriString)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share image"))
    }

    suspend fun saveToGallery(context: Context, uriString: String, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            val extension = extensionForMimeType(mimeType)
            val displayName = "MaterialChat_${System.currentTimeMillis()}.$extension"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MaterialChat")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val outputUri = context.contentResolver.insert(collection, values) ?: return@withContext false
            try {
                openInputStream(context, uriString).use { input ->
                    context.contentResolver.openOutputStream(outputUri).use { output ->
                        if (input == null || output == null) return@withContext false
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(outputUri, values, null, null)
                }
                true
            } catch (e: Exception) {
                context.contentResolver.delete(outputUri, null, null)
                false
            }
        }

    private fun openInputStream(context: Context, uriString: String) =
        if (uriString.startsWith("data:")) {
            val base64 = uriString.substringAfter("base64,", missingDelimiterValue = "")
            ByteArrayInputStream(Base64.decode(base64, Base64.DEFAULT))
        } else {
            Uri.parse(uriString).let { uri ->
                if (uri.scheme == "file") File(uri.path.orEmpty()).inputStream()
                else context.contentResolver.openInputStream(uri)
            }
        }

    private fun shareableUri(context: Context, uriString: String): Uri {
        val uri = Uri.parse(uriString)
        val file = if (uriString.startsWith("data:")) {
            val base64 = uriString.substringAfter("base64,", missingDelimiterValue = "")
            val shareDir = File(context.cacheDir, "generated_shares").apply { mkdirs() }
            val tempFile = File(shareDir, "generated_share_${System.currentTimeMillis()}.png")
            tempFile.outputStream().use { it.write(Base64.decode(base64, Base64.DEFAULT)) }
            tempFile
        } else if (uri.scheme == "file") {
            File(uri.path ?: return uri)
        } else {
            return uri
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun extensionForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
}
