package com.materialchat.assistant.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.assistant.screen.AssistScreenshots
import com.materialchat.assistant.ui.AssistantViewModel
import com.materialchat.util.AttachmentFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real session's assist callbacks with a real bitmap (the privileged system path
 * that delivers them cannot be exercised from a test) and checks what reaches the ViewModel:
 * the downscaled capture, the owned attachment file the "Screen" chip stages, its deletion
 * when the chip is removed, and the release on hide.
 */
@RunWith(AndroidJUnit4::class)
class AssistantScreenContextCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T : Any> await(timeoutMillis: Long = 10_000, read: () -> T?): T? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            read()?.let { return it }
            Thread.sleep(50)
        }
        return read()
    }

    @Test fun screenshotReachesTheViewModelDownscaledAsAnOwnedFileAndIsReleasedOnHide() {
        lateinit var session: MaterialChatVoiceInteractionSession
        lateinit var viewModel: AssistantViewModel
        lateinit var original: Bitmap
        instrumentation.runOnMainSync {
            session = MaterialChatVoiceInteractionSession(instrumentation.targetContext)
            session.onCreate()
            session.onCreateContentView()
            // The session created the ViewModel under the default key; an empty factory proves
            // the lookup hits the store rather than building a second instance.
            viewModel = ViewModelProvider(session, viewModelFactory { })[AssistantViewModel::class.java]
            session.onShow(null, 0)
            original = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            session.onHandleScreenshot(original)
        }
        try {
            val context = requireNotNull(viewModel.screenContext.value) { "the capture did not reach the ViewModel" }
            val scaled = requireNotNull(context.screenshot) { "no screenshot in the capture" }
            assertEquals(1280, scaled.height)
            assertEquals(576, scaled.width)
            assertTrue("the full-size original must be released once downscaled", original.isRecycled)
            assertFalse(scaled.isRecycled)

            // A question about the screen stages the chip: the file is written on IO into the
            // owned attachments directory, at the downscaled size.
            instrumentation.runOnMainSync { viewModel.updateTextInput("what does this first tweet mean") }
            val staged = requireNotNull(await { viewModel.screenAttachment.value }) { "the Screen chip was never staged" }
            assertTrue(staged.uri, AttachmentFiles.isOwned(instrumentation.targetContext, staged.uri))
            val file = File(Uri.parse(staged.uri).path!!)
            assertTrue(file.exists() && file.length() > 0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertEquals("image/jpeg", bounds.outMimeType)
            assertEquals(AssistScreenshots.MAX_LONG_EDGE, maxOf(bounds.outWidth, bounds.outHeight))

            // Removing the chip deletes the file; nothing of the screen stays on disk.
            instrumentation.runOnMainSync { viewModel.removeScreenContext() }
            assertNull(viewModel.screenAttachment.value)
            assertFalse("removing the chip must delete its file", file.exists())

            // Hiding ends the invocation: the ViewModel forgets the capture and the bitmap dies.
            instrumentation.runOnMainSync { session.onHide() }
            assertNull(viewModel.screenContext.value)
            assertTrue(scaled.isRecycled)
        } finally {
            instrumentation.runOnMainSync { session.onDestroy() }
        }
    }

    @Test fun withheldAssistDataIsRecordedAsAnEmptyCapture() {
        lateinit var session: MaterialChatVoiceInteractionSession
        lateinit var viewModel: AssistantViewModel
        instrumentation.runOnMainSync {
            session = MaterialChatVoiceInteractionSession(instrumentation.targetContext)
            session.onCreate()
            session.onCreateContentView()
            viewModel = ViewModelProvider(session, viewModelFactory { })[AssistantViewModel::class.java]
            session.onShow(null, 0)
            // What the platform delivers when the digital-assistant switches are off or the
            // screen is secure: the callbacks fire with nothing in them.
            @Suppress("DEPRECATION")
            session.onHandleAssist(null, null, null)
            session.onHandleScreenshot(null)
        }
        try {
            val context = requireNotNull(viewModel.screenContext.value) { "an empty delivery must still be recorded" }
            assertTrue(context.isEmpty)
            assertNull(context.screenshot)
            assertNull(context.text)
            assertNull(context.webUri)
            // Nothing to stage: a question about the screen writes no file.
            instrumentation.runOnMainSync { viewModel.updateTextInput("what is this") }
            Thread.sleep(300)
            assertNull(viewModel.screenAttachment.value)
            instrumentation.runOnMainSync { session.onHide() }
            assertNull(viewModel.screenContext.value)
        } finally {
            instrumentation.runOnMainSync { session.onDestroy() }
        }
    }
}
