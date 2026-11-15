package com.materialchat.assistant.attach

import com.materialchat.domain.model.Attachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAttachmentInboxTest {
    private fun photo(name: String) = Attachment(uri = "file:///data/chat_attachments/$name.jpg", mimeType = "image/jpeg")

    @Test fun `drain takes everything exactly once`() {
        val inbox = AssistantAttachmentInbox()
        inbox.publish(listOf(photo("a"), photo("b")))
        assertEquals(listOf("a", "b"), inbox.drain().map { it.uri.substringAfterLast('/').substringBefore('.') })
        assertTrue(inbox.drain().isEmpty())
        assertTrue(inbox.pending.value.isEmpty())
    }

    @Test fun `publishes append in order and an empty publish is a no-op`() {
        val inbox = AssistantAttachmentInbox()
        inbox.publish(emptyList())
        assertTrue(inbox.pending.value.isEmpty())
        inbox.publish(listOf(photo("a")))
        inbox.publish(listOf(photo("b"), photo("c")))
        assertEquals(3, inbox.pending.value.size)
        assertEquals(listOf("a", "b", "c"), inbox.drain().map { it.uri.substringAfterLast('/').substringBefore('.') })
    }

    @Test fun `photos published after a drain wait for the next drain`() {
        val inbox = AssistantAttachmentInbox()
        inbox.publish(listOf(photo("a")))
        inbox.drain()
        inbox.publish(listOf(photo("b")))
        assertEquals(1, inbox.pending.value.size)
        assertEquals(1, inbox.drain().size)
    }
}
