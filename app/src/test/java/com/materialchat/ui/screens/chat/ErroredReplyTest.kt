package com.materialchat.ui.screens.chat

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ProviderHttpException
import com.materialchat.domain.model.StreamingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule that decides whether an assistant bubble shows the failure notice and Try again
 * or the action row (W8 chat part 4). A Stop after the first thinking token leaves a
 * thinking-only reply behind; that reply is cancelled, not failed.
 */
class ErroredReplyTest {
    private fun reply(
        content: String = "",
        thinking: String? = null,
        streaming: Boolean = false,
        role: MessageRole = MessageRole.ASSISTANT,
        id: String = "a2"
    ) = Message(id = id, conversationId = "c", role = role, content = content, thinkingContent = thinking, isStreaming = streaming)

    private val failed = StreamingState.Error(ProviderHttpException(503, "body"), messageId = "a2")

    @Test
    fun `an empty stopped reply with no thinking is errored`() {
        assertTrue(isErroredReply(reply(), isLastAssistantMessage = true, streamingState = StreamingState.Idle))
        assertTrue(isErroredReply(reply(), isLastAssistantMessage = false, streamingState = StreamingState.Idle))
    }

    @Test
    fun `a stopped thinking-only reply is cancelled, not errored`() {
        assertFalse(isErroredReply(reply(thinking = "hmm"), isLastAssistantMessage = true, streamingState = StreamingState.Idle))
        assertFalse(isErroredReply(reply(thinking = "hmm"), isLastAssistantMessage = true, streamingState = StreamingState.Cancelled(messageId = "a2")))
    }

    @Test
    fun `a thinking-only reply whose stream failed is errored`() {
        assertTrue(isErroredReply(reply(thinking = "hmm"), isLastAssistantMessage = true, streamingState = failed))
        // An error that carries no message id (the send path's catch-all) still names the last reply.
        val untyped = StreamingState.Error(IllegalStateException("boom"))
        assertTrue(isErroredReply(reply(thinking = "hmm"), isLastAssistantMessage = true, streamingState = untyped))
    }

    @Test
    fun `an older thinking-only reply never turns errored for a later failure`() {
        assertFalse(isErroredReply(reply(thinking = "hmm", id = "a1"), isLastAssistantMessage = false, streamingState = failed))
        val untyped = StreamingState.Error(IllegalStateException("boom"))
        assertFalse(isErroredReply(reply(thinking = "hmm", id = "a1"), isLastAssistantMessage = false, streamingState = untyped))
        // Last, but the error names another message: not this one.
        val other = StreamingState.Error(IllegalStateException("boom"), messageId = "a9")
        assertFalse(isErroredReply(reply(thinking = "hmm"), isLastAssistantMessage = true, streamingState = other))
    }

    @Test
    fun `content, attachments, streaming and other roles are never errored`() {
        assertFalse(isErroredReply(reply(content = "answer"), true, failed))
        assertFalse(isErroredReply(reply(streaming = true), true, failed))
        assertFalse(isErroredReply(reply(role = MessageRole.USER), true, failed))
        assertFalse(isErroredReply(reply(role = MessageRole.SYSTEM), true, failed))
    }
}
