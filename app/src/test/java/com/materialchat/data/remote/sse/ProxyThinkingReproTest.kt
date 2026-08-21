package com.materialchat.data.remote.sse

import com.materialchat.data.remote.api.StreamingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproduction test: LLM-API-Key-Proxy emits reasoning as
 * `delta.reasoning_content` chunks. The app must surface them as thinking.
 */
class ProxyThinkingReproTest {

    private val parser = SseEventParser()

    @Test
    fun `parses proxy reasoning_content delta chunk`() {
        val line = """data: {"id":"chatcmpl-123","created":1750000000,"model":"gpt-5.2","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"reasoning_content":"Let me think","role":"assistant"},"finish_reason":null}]}"""

        val event = parser.parseOpenAiEvent(line)

        assertTrue(event is StreamingEvent.Content)
        event as StreamingEvent.Content
        assertEquals("", event.content)
        assertEquals("Let me think", event.thinking)
    }

    @Test
    fun `parses proxy content delta chunk after reasoning`() {
        val line = """data: {"id":"chatcmpl-123","created":1750000000,"model":"gpt-5.2","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"Hello!","role":"assistant"},"finish_reason":null}]}"""

        val event = parser.parseOpenAiEvent(line)

        assertTrue(event is StreamingEvent.Content)
        event as StreamingEvent.Content
        assertEquals("Hello!", event.content)
        assertEquals(null, event.thinking)
    }

    @Test
    fun `parses proxy reasoning field variant`() {
        val line = """data: {"id":"c1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"reasoning":"hmm"}}]}"""

        val event = parser.parseOpenAiEvent(line)

        assertTrue(event is StreamingEvent.Content)
        event as StreamingEvent.Content
        assertEquals("hmm", event.thinking)
    }
}
