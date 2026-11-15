package com.materialchat.assistant.attach

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.util.AttachmentFiles
import com.materialchat.util.AttachmentLimits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The attach activity's publish path, driven with plain files instead of the camera or the
 * system picker: picked URIs are imported into app-owned storage and land in the inbox, the
 * per-message room is honoured, and a bad image publishes nothing.
 */
@RunWith(AndroidJUnit4::class)
class AssistantAttachmentDeliveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun png(name: String, color: Int): Uri {
        val file = File(context.cacheDir, "$name.png")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return Uri.fromFile(file)
    }

    @Test fun importsAndPublishesPickedImages() = runBlocking<Unit> {
        val inbox = AssistantAttachmentInbox()
        val outcome = AssistantAttachmentDelivery.deliver(
            context, listOf(png("one", Color.RED), png("two", Color.BLUE)), room = 4, inbox = inbox
        )
        assertEquals(2, outcome.delivered)
        assertNull(outcome.notice)
        val published = inbox.drain()
        assertEquals(2, published.size)
        published.forEach { attachment ->
            // Each copy is a file the app owns under chat_attachments, so it can be deleted later.
            assertTrue(attachment.uri, AttachmentFiles.isOwned(context, attachment.uri))
            assertNotNull(AttachmentFiles.ownedPath(context, attachment.uri))
            assertTrue(File(Uri.parse(attachment.uri).path!!).length() > 0)
            assertTrue(AttachmentFiles.deleteIfOwned(context, attachment.uri))
        }
        assertTrue(inbox.drain().isEmpty())
    }

    @Test fun honoursRoomAndReportsSkipped() = runBlocking<Unit> {
        val inbox = AssistantAttachmentInbox()
        val outcome = AssistantAttachmentDelivery.deliver(
            context, listOf(png("a", Color.RED), png("b", Color.GREEN), png("c", Color.BLUE)), room = 1, inbox = inbox
        )
        assertEquals(1, outcome.delivered)
        assertEquals(AttachmentLimits.skippedNotice(2), outcome.notice)
        val published = inbox.drain()
        assertEquals(1, published.size)
        AttachmentFiles.deleteIfOwned(context, published.single().uri)
    }

    @Test fun noRoomPublishesNothing() = runBlocking<Unit> {
        val inbox = AssistantAttachmentInbox()
        val outcome = AssistantAttachmentDelivery.deliver(context, listOf(png("a", Color.RED)), room = 0, inbox = inbox)
        assertEquals(0, outcome.delivered)
        assertTrue(inbox.pending.value.isEmpty())
    }

    @Test fun unreadableImagePublishesNothingAndReportsWhy() = runBlocking<Unit> {
        val inbox = AssistantAttachmentInbox()
        val bogus = File(context.cacheDir, "bogus.png").apply { writeText("not an image") }
        val outcome = AssistantAttachmentDelivery.deliver(context, listOf(Uri.fromFile(bogus)), room = 4, inbox = inbox)
        assertEquals(0, outcome.delivered)
        assertNotNull(outcome.notice)
        assertTrue(inbox.pending.value.isEmpty())
        // Nothing was written into the attachment directory for it.
        val dir = File(context.filesDir, AttachmentFiles.PICKED_DIR)
        assertTrue(dir.listFiles().orEmpty().none { it.length() == 0L })
    }
}
