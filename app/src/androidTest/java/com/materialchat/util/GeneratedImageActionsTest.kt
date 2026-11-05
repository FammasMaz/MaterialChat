package com.materialchat.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Production share/save actions, real FileProvider grants and real MediaStore bytes. */
@RunWith(AndroidJUnit4::class)
class GeneratedImageActionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val galleryItems = mutableListOf<Uri>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
        galleryItems.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun pickedAndGeneratedImagesShareReadableContentUrisWithGrants() {
        for (directory in listOf(AttachmentFiles.PICKED_DIR, AttachmentFiles.GENERATED_DIR)) {
            val source = imageFile(directory)
            val recordingContext = RecordingContext(context)
            GeneratedImageActions.share(recordingContext, Uri.fromFile(source).toString(), "image/jpeg")

            val send = sharedIntent(recordingContext)
            val uri = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.fileprovider", uri.authority)
            assertEquals("image/jpeg", send.type)
            assertEquals(uri, requireNotNull(send.clipData).getItemAt(0).uri)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(uri)?.use { it.readBytes() })
        }
    }

    @Test
    fun inlineJpegSharesAsJpegNotPng() {
        val source = imageFile(AttachmentFiles.GENERATED_DIR)
        val data = Base64.encodeToString(source.readBytes(), Base64.NO_WRAP)
        val recordingContext = RecordingContext(context)
        GeneratedImageActions.share(recordingContext, "data:image/jpeg;base64,$data", "image/jpeg")
        val send = sharedIntent(recordingContext)
        val uri = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val name = requireNotNull(uri.lastPathSegment)
        files += File(context.cacheDir, "generated_shares/$name")
        assertTrue(name, name.endsWith(".jpg"))
        assertEquals("image/jpeg", context.contentResolver.getType(uri))
        assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(uri)?.use { it.readBytes() })
    }

    @Test
    fun unrelatedPrivateFilesCannotBeSharedThroughTheImageProvider() {
        val privateFile = File(context.filesDir, "private-${UUID.randomUUID()}.txt").apply {
            writeText("must remain private")
            files += this
        }
        val result = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", privateFile)
        }
        assertTrue("Only the explicit image directories may be exposed", result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun gallerySavePublishesCompleteImageWithExactBytes() = runBlocking {
        // On older Android versions gallery writes require an interactive storage grant.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val source = imageFile(AttachmentFiles.PICKED_DIR)
        val before = galleryIds()
        assertTrue(GeneratedImageActions.saveToGallery(context, Uri.fromFile(source).toString(), "image/jpeg"))
        val added = galleryIds() - before
        galleryItems += added.map { Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.toString()) }
        assertEquals("Exactly one complete image must be published", 1, added.size)
        val uri = galleryItems.single()
        assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(uri)?.use { it.readBytes() })
        context.contentResolver.query(uri, arrayOf(
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.IS_PENDING,
            MediaStore.Images.Media.RELATIVE_PATH
        ), null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertTrue(requireNotNull(cursor).moveToFirst())
            assertEquals("image/jpeg", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("Pictures/MaterialChat/", cursor.getString(2))
        }
    }

    @Test
    fun missingSourceDoesNotLeaveAnEmptyGalleryEntry() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val before = galleryIds()
        val missing = File(context.cacheDir, "missing-${UUID.randomUUID()}.jpg")
        assertFalse(GeneratedImageActions.saveToGallery(context, Uri.fromFile(missing).toString(), "image/jpeg"))
        assertEquals(before, galleryIds())
    }

    private fun galleryIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("MaterialChat_%"),
            null
        ).use { cursor ->
            if (cursor != null) while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    private fun imageFile(directory: String): File {
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            return File(context.filesDir, "$directory/actions-test-${UUID.randomUUID()}.jpg").also { file ->
                requireNotNull(file.parentFile).mkdirs()
                files += file
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun sharedIntent(context: RecordingContext): Intent {
        val chooser = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        return requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)).also {
            assertEquals(Intent.ACTION_SEND, it.action)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null
        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }
}
