package com.materialchat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.materialchat.domain.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Single entry point for turning a picked/shared image URI into a persisted [Attachment].
 *
 * Responsibilities:
 * - Accept every format the platform can decode (JPEG, PNG, WebP, GIF, HEIC/HEIF, BMP).
 * - Never load the original file fully into memory; decode with bounds + sample size.
 * - Honor EXIF orientation so portrait camera photos are not sent sideways.
 * - Downscale to [MAX_DIMENSION] and re-encode (JPEG, or PNG when the source has alpha).
 * - Store the result in app-private storage so messages can be restored and resent.
 * - Delete files it created when an attachment is discarded.
 */
object AttachmentImporter {
    private const val TAG = "AttachmentImporter"
    private const val ATTACHMENTS_DIR = "chat_attachments"
    const val MAX_DIMENSION = 1536
    private const val JPEG_QUALITY = 85

    sealed class ImportResult {
        data class Success(val attachment: Attachment) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            val resolver = appContext.contentResolver
            val declaredMime = resolver.getType(uri)?.lowercase()
            if (declaredMime != null && !declaredMime.startsWith("image/")) {
                return@withContext ImportResult.Failure("Only images can be attached")
            }

            val sizeBytes = querySize(appContext, uri)
            if (sizeBytes != null && sizeBytes > Attachment.MAX_FILE_SIZE_BYTES) {
                val mb = Attachment.MAX_FILE_SIZE_BYTES / (1024 * 1024)
                return@withContext ImportResult.Failure("Image is larger than $mb MB")
            }

            val bounds = decodeBounds(appContext, uri)
                ?: return@withContext ImportResult.Failure("Couldn't read this image")
            val sourceMime = bounds.outMimeType?.lowercase() ?: declaredMime ?: "image/jpeg"
            if (!Attachment.isSupportedImageType(sourceMime)) {
                return@withContext ImportResult.Failure("Unsupported image format")
            }

            val bitmap = decodeScaled(appContext, uri, bounds)
                ?: return@withContext ImportResult.Failure("Couldn't decode this image")

            val keepPng = sourceMime == "image/png" && bitmap.hasAlpha()
            val outMime = if (keepPng) "image/png" else "image/jpeg"
            val file = newAttachmentFile(appContext, if (keepPng) "png" else "jpg")
            file.outputStream().buffered().use { out ->
                val ok = bitmap.compress(
                    if (keepPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    JPEG_QUALITY,
                    out
                )
                if (!ok) {
                    bitmap.recycle()
                    file.delete()
                    return@withContext ImportResult.Failure("Couldn't save this image")
                }
            }
            Log.d(TAG, "Imported $sourceMime ${bounds.outWidth}x${bounds.outHeight} -> $outMime ${bitmap.width}x${bitmap.height} ${file.length()}B")
            bitmap.recycle()

            ImportResult.Success(
                Attachment(uri = Uri.fromFile(file).toString(), mimeType = outMime)
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read $uri", e)
            ImportResult.Failure("No permission to read this image")
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM importing $uri", e)
            ImportResult.Failure("Image is too large to process")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import $uri", e)
            ImportResult.Failure("Couldn't load this image")
        }
    }

    /** Deletes the backing file if it is one this importer created. */
    fun discard(context: Context, attachment: Attachment) {
        val path = Uri.parse(attachment.uri).takeIf { it.scheme == "file" }?.path ?: return
        val file = File(path)
        val dir = File(context.applicationContext.filesDir, ATTACHMENTS_DIR)
        if (file.parentFile?.canonicalPath == dir.canonicalPath && file.exists()) {
            if (!file.delete()) Log.w(TAG, "Could not delete ${file.name}")
        }
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        if (uri.scheme == "file") return uri.path?.let { File(it).length() }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) return c.getLong(idx)
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 }
    }

    private fun open(context: Context, uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)

    private fun decodeBounds(context: Context, uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        return options.takeIf { it.outWidth > 0 && it.outHeight > 0 }
    }

    private fun decodeScaled(context: Context, uri: Uri, bounds: BitmapFactory.Options): Bitmap? {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { decodeWithImageDecoder(context, uri) }
                .onFailure { Log.w(TAG, "ImageDecoder failed, falling back", it) }
                .getOrNull()
        } else {
            null
        } ?: decodeWithBitmapFactory(context, uri, bounds) ?: return null
        return fitWithin(decoded, MAX_DIMENSION)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies EXIF orientation itself.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > MAX_DIMENSION) {
                var sample = 1
                while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri, bounds: BitmapFactory.Options): Bitmap? {
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = open(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = open(context, uri)?.use { stream ->
            runCatching { ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun fitWithin(bitmap: Bitmap, maxDimension: Int): Bitmap {
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
        val scale = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun newAttachmentFile(context: Context, extension: String): File {
        val directory = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
        return File(directory, "attachment_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
    }
}
