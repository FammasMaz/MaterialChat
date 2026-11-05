package com.materialchat.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.domain.model.Attachment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses Android's real codecs, URI resolver and private storage, not mocked bitmap APIs. */
@RunWith(AndroidJUnit4::class)
class AttachmentImporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sources: File
    private val imported = mutableListOf<Attachment>()

    @Before
    fun setUp() {
        sources = File(context.cacheDir, "import-test-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        imported.forEach { AttachmentImporter.discard(context, it) }
        sources.deleteRecursively()
    }

    @Test
    fun boundsOnlyDecodeReturnsNullButValidImageStillImports() = runBlocking {
        val source = writeImage("valid.jpg", 80, 40)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.inputStream().use {
            assertNull(BitmapFactory.decodeStream(it, null, bounds))
        }
        assertEquals(80, bounds.outWidth)
        assertEquals(40, bounds.outHeight)

        val attachment = importImage(source)
        val file = File(requireNotNull(Uri.parse(attachment.uri).path))
        assertTrue("Import must persist nonempty bytes", file.length() > 0)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals("", attachment.base64Data)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
        try {
            assertEquals(80, bitmap.width)
            assertEquals(40, bitmap.height)
        } finally {
            bitmap.recycle()
        }
        AttachmentImporter.discard(context, attachment)
        assertFalse("Discard must delete only the imported copy", file.exists())
        assertTrue(source.exists())
    }

    @Test
    fun largeImageIsDownscaledWithoutStretching() = runBlocking {
        val attachment = importImage(writeImage("large.jpg", 3072, 1536))
        val bitmap = decoded(attachment)
        try {
            assertEquals(AttachmentImporter.MAX_DIMENSION, bitmap.width)
            assertEquals(AttachmentImporter.MAX_DIMENSION / 2, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun transparentPngAndWebpKeepTheirAlpha() = runBlocking {
        for ((name, format) in listOf(
            "alpha.png" to Bitmap.CompressFormat.PNG,
            "alpha.webp" to Bitmap.CompressFormat.WEBP
        )) {
            val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
            val source = File(sources, name)
            try {
                bitmap.eraseColor(Color.TRANSPARENT)
                bitmap.setPixel(16, 8, Color.RED)
                source.outputStream().use { assertTrue(bitmap.compress(format, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            val attachment = importImage(source)
            assertEquals("Transparent $name must not become opaque JPEG", "image/png", attachment.mimeType)
            val result = decoded(attachment)
            try {
                assertEquals(0, Color.alpha(result.getPixel(0, 0)))
                assertEquals(255, Color.alpha(result.getPixel(16, 8)))
            } finally {
                result.recycle()
            }
        }
    }

    @Test
    fun exifOrientationsMatchPixelPositionsNotJustDimensions() = runBlocking {
        // Source quadrants: red/green above blue/yellow. Check every EXIF transform.
        val red = Color.RED
        val green = Color.GREEN
        val blue = Color.BLUE
        val yellow = Color.YELLOW
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to listOf(red, green, blue, yellow),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf(green, red, yellow, blue),
            ExifInterface.ORIENTATION_ROTATE_180 to listOf(yellow, blue, green, red),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf(blue, yellow, red, green),
            ExifInterface.ORIENTATION_TRANSPOSE to listOf(red, blue, green, yellow),
            ExifInterface.ORIENTATION_ROTATE_90 to listOf(blue, red, yellow, green),
            ExifInterface.ORIENTATION_TRANSVERSE to listOf(yellow, green, blue, red),
            ExifInterface.ORIENTATION_ROTATE_270 to listOf(green, yellow, red, blue)
        )
        for ((orientation, colors) in expected) {
            val source = writeImage("orientation-$orientation.jpg", 80, 40, quadrants = true)
            ExifInterface(source.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val result = decoded(importImage(source))
            try {
                val swapsAxes = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
                assertEquals(if (swapsAxes) 40 else 80, result.width)
                assertEquals(if (swapsAxes) 80 else 40, result.height)
                val actual = listOf(
                    result.getPixel(result.width / 4, result.height / 4),
                    result.getPixel(result.width * 3 / 4, result.height / 4),
                    result.getPixel(result.width / 4, result.height * 3 / 4),
                    result.getPixel(result.width * 3 / 4, result.height * 3 / 4)
                )
                colors.zip(actual).forEachIndexed { quadrant, (wanted, pixel) ->
                    val delta = kotlin.math.abs(Color.red(wanted) - Color.red(pixel)) +
                        kotlin.math.abs(Color.green(wanted) - Color.green(pixel)) +
                        kotlin.math.abs(Color.blue(wanted) - Color.blue(pixel))
                    assertTrue("EXIF $orientation quadrant $quadrant RGB error=$delta", delta <= 24)
                }
            } finally {
                result.recycle()
            }
        }
    }

    @Test
    fun corruptAndOversizedFilesFailWithoutCreatingAttachments() = runBlocking {
        val directory = File(context.filesDir, AttachmentFiles.PICKED_DIR)
        val before = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
        val corrupt = File(sources, "corrupt.jpg").apply { writeText("Not image bytes") }
        val result = AttachmentImporter.import(context, Uri.fromFile(corrupt))
        assertTrue(result.toString(), result is AttachmentImporter.ImportResult.Failure)
        val large = writeImage("oversized.jpg", 20, 10)
        java.io.RandomAccessFile(large, "rw").use { it.setLength(Attachment.MAX_FILE_SIZE_BYTES + 1) }
        val oversized = AttachmentImporter.import(context, Uri.fromFile(large))
        assertTrue(oversized.toString(), oversized is AttachmentImporter.ImportResult.Failure)
        assertEquals(before, directory.listFiles()?.map { it.name }?.toSet().orEmpty())
    }

    private suspend fun importImage(source: File): Attachment {
        val result = AttachmentImporter.import(context, Uri.fromFile(source))
        assertTrue("Valid ${source.name} must import: $result", result is AttachmentImporter.ImportResult.Success)
        return (result as AttachmentImporter.ImportResult.Success).attachment.also { imported += it }
    }

    private fun decoded(attachment: Attachment): Bitmap {
        val result = BitmapFactory.decodeFile(Uri.parse(attachment.uri).path)
        assertNotNull("Saved attachment must decode", result)
        return requireNotNull(result)
    }

    private fun writeImage(name: String, width: Int, height: Int, quadrants: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setHasAlpha(false)
            bitmap.eraseColor(Color.RED)
            if (quadrants) {
                for (y in 0 until height) for (x in 0 until width) {
                    bitmap.setPixel(x, y, when {
                        y < height / 2 && x < width / 2 -> Color.RED
                        y < height / 2 -> Color.GREEN
                        x < width / 2 -> Color.BLUE
                        else -> Color.YELLOW
                    })
                }
            }
            return File(sources, name).also { file ->
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
