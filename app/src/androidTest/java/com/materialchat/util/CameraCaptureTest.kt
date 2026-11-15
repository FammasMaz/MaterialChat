package com.materialchat.util

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The camera hand-off, on a real device: the file the camera writes into, the URI it receives,
 * and the cleanup when the user backs out. The camera app itself is not driven — this covers the
 * contract around it, which is where the bugs live (wrong authority, wrong directory, a stray
 * empty file after a cancel).
 */
@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun captureDir(): File = File(context.filesDir, AttachmentFiles.PICKED_DIR)

    private fun filesInCaptureDir(): Set<String> =
        captureDir().listFiles()?.map { it.name }?.toSet() ?: emptySet()

    @Test
    fun prepareCreatesAnOwnedTargetAndAWriteableUri() {
        val before = filesInCaptureDir()
        val capture = CameraCapture.prepare(context)
        if (capture == null) {
            // No camera app on this device: prepare must clean up after itself rather than leave
            // an empty file behind for the next capture to trip over.
            assertFalse("No camera app must be reported, not guessed", CameraCapture.isAvailable(context))
            assertEquals("A refused capture must not leave a file", before, filesInCaptureDir())
            return
        }
        try {
            assertTrue("prepare must report a camera app", CameraCapture.isAvailable(context))
            assertTrue("The camera target must exist before the intent is sent", capture.file.exists())
            assertTrue(
                "The target must live in the attachment directory",
                capture.file.parentFile?.name == AttachmentFiles.PICKED_DIR
            )
            assertEquals("content", capture.uri.scheme)
            assertEquals(context.packageName + ".fileprovider", capture.uri.authority)
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, capture.intent.action)
            assertEquals(capture.uri, capture.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT))
            val flags = capture.intent.flags
            assertTrue("The camera needs write access", flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            assertTrue("The camera needs read access", flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            // The provider must actually serve the file, or the camera would fail to write.
            context.contentResolver.openOutputStream(capture.uri)?.use { it.write(byteArrayOf(1, 2, 3)) }
            assertEquals(3L, context.contentResolver.openInputStream(capture.uri)?.use { it.readBytes() }?.size?.toLong())
        } finally {
            CameraCapture.discard(capture.file)
        }
        assertFalse("discard must remove the target", capture.file.exists())
        assertEquals("discard must leave the directory as it was", before, filesInCaptureDir())
    }

    @Test
    fun discardToleratesAMissingFileAndNull() {
        CameraCapture.discard(null)
        val gone = File(captureDir(), "camera_does-not-exist.jpg")
        assertFalse(gone.exists())
        CameraCapture.discard(gone)
        assertFalse("discard must not invent the file", gone.exists())
    }

    @Test
    fun everyPreparedCaptureGetsItsOwnFile() {
        val first = CameraCapture.prepare(context) ?: return
        val second = CameraCapture.prepare(context) ?: run { CameraCapture.discard(first.file); return }
        try {
            assertTrue("Two captures must never share a target", first.file.absolutePath != second.file.absolutePath)
            assertTrue(first.uri != second.uri)
        } finally {
            CameraCapture.discard(first.file)
            CameraCapture.discard(second.file)
        }
    }

    @Test
    fun captureUriIsNotAShareableFileUri() {
        val capture = CameraCapture.prepare(context) ?: return
        try {
            // A file:// URI handed to another app throws FileUriExposedException on API 24+.
            assertTrue("The camera must receive a content URI", capture.uri != Uri.fromFile(capture.file))
        } finally {
            CameraCapture.discard(capture.file)
        }
    }
}
