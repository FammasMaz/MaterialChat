package com.materialchat.data.remote.sse

import com.materialchat.data.remote.api.StreamingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SseEventParser].
 *
 * Tests cover:
 * - OpenAI SSE format parsing
 * - Ollama NDJSON format parsing
 * - Edge cases and error handling
 */
class SseEventParserTest {

    private lateinit var parser: SseEventParser

    @Before
    fun setup() {
        parser = SseEventParser()
    }

    // ============================================================================
    // OpenAI SSE Format Tests
    // ============================================================================

    @Test
    fun `parseOpenAiEvent - content chunk returns Content event`() {
        val line = """data: {"id":"chatcmpl-123","choices":[{"delta":{"content":"Hello"}}]}"""

        val result = parser.parseOpenAiEvent(line)

        assertTrue(result is StreamingEvent.Content)
        assertEquals("Hello", (result as StreamingEvent.Content).content)
    }

    @Test
    fun `parseOpenAiEvent - done marker returns Done event`() {
        val line = "data: [DONE]"

        val result = parser.parseOpenAiEvent(line)

        assertTrue(result is StreamingEvent.Done)
    }

    @Test
    fun `parseOpenAiEvent - empty line returns KeepAlive`() {
        val result = parser.parseOpenAiEvent("")

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOpenAiEvent - comment line returns KeepAlive`() {
        val result = parser.parseOpenAiEvent(": ping")

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOpenAiEvent - whitespace only returns KeepAlive`() {
        val result = parser.parseOpenAiEvent("   ")

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOpenAiEvent - role only delta returns Connected`() {
        val line = """data: {"id":"chatcmpl-123","choices":[{"delta":{"role":"assistant"}}]}"""

        val result = parser.parseOpenAiEvent(line)

        assertEquals(StreamingEvent.Connected, result)
    }

    @Test
    fun `parseOpenAiEvent - finish reason only chunk returns KeepAlive`() {
        val line = """data: {"id":"chatcmpl-123","choices":[{"delta":{},"finish_reason":"stop"}],"model":"gpt-4o"}"""

        val result = parser.parseOpenAiEvent(line)

        // Some providers send finish_reason on normal chunks. The parser waits for
        // the explicit [DONE] marker so it does not end streams prematurely.
        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOpenAiEvent - empty choices returns KeepAlive`() {
        val line = """data: {"id":"chatcmpl-123","choices":[]}"""

        val result = parser.parseOpenAiEvent(line)

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOpenAiEvent - line without data prefix returns null`() {
        val line = "event: message"

        val result = parser.parseOpenAiEvent(line)

        assertNull(result)
    }

    @Test
    fun `parseOpenAiEvent - invalid JSON returns Error`() {
        val line = "data: {invalid json}"

        val result = parser.parseOpenAiEvent(line)

        assertTrue(result is StreamingEvent.Error)
        assertTrue((result as StreamingEvent.Error).message.contains("Failed to parse"))
    }

    @Test
    fun `parseOpenAiEvent - well-formed error response returns KeepAlive since it parses as valid chunk`() {
        // Name is historical (W5 keeps test names stable); the behaviour it pinned was the W4
        // negative finding: a well-formed `{"error":{...}}` decoded as a chunk with empty
        // choices and passed as a keep-alive, so a 200 carrying an error ended the stream
        // silently. It now surfaces as an Error, typed with the status the envelope carries.
        val line = """data: {"error":{"message":"Rate limit exceeded","type":"rate_limit_error","code":"429"}}"""

        val result = parser.parseOpenAiEvent(line)

        assertTrue("expected Error, got $result", result is StreamingEvent.Error)
        val error = result as StreamingEvent.Error
        assertEquals("Rate limit exceeded", error.message)
        assertEquals("429", error.code)
        assertEquals(429, error.httpStatus)
        assertTrue(error.isRecoverable)
    }

    @Test
    fun `parseOpenAiEvent - error envelope with a non-numeric code has no HTTP status`() {
        val line = """data: {"error":{"message":"This model's maximum context length is 8192 tokens","type":"invalid_request_error","code":"context_length_exceeded"}}"""

        val result = parser.parseOpenAiEvent(line)

        assertTrue(result is StreamingEvent.Error)
        val error = result as StreamingEvent.Error
        assertEquals("context_length_exceeded", error.code)
        assertNull(error.httpStatus)
        assertEquals(false, error.isRecoverable)
    }

    @Test
    fun `parseOpenAiEvent - error envelope with a numeric status code is typed`() {
        // OpenRouter / LiteLLM relay the upstream status as a JSON number.
        val line = """data: {"error":{"message":"upstream unavailable","code":503}}"""

        val result = parser.parseOpenAiEvent(line)

        assertTrue("expected Error, got $result", result is StreamingEvent.Error)
        val error = result as StreamingEvent.Error
        assertEquals(503, error.httpStatus)
        assertTrue(error.isRecoverable)
    }

    @Test
    fun `parseOpenAiEvent - error envelope with a 4xx status is not recoverable`() {
        val line = """data: {"error":{"message":"invalid key","type":"authentication_error","code":"401"}}"""

        val error = parser.parseOpenAiEvent(line) as StreamingEvent.Error

        assertEquals(401, error.httpStatus)
        assertEquals(false, error.isRecoverable)
    }

    @Test
    fun `parseOpenAiEvent - choiceless chunk without an error envelope is still KeepAlive`() {
        val line = """data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[]}"""

        assertEquals(StreamingEvent.KeepAlive, parser.parseOpenAiEvent(line))
        assertEquals(StreamingEvent.KeepAlive, parser.parseOpenAiEvent("data: {}"))
        // A null error member is not an error either.
        assertEquals(StreamingEvent.KeepAlive, parser.parseOpenAiEvent("data: {\"error\":null}"))
    }

    @Test
    fun `parseOpenAiEvent - malformed JSON with error format falls back to error parsing`() {
        // This tests the error fallback path - when JSON is invalid but looks like an error
        val line = """data: not valid json at all"""

        val result = parser.parseOpenAiEvent(line)

        // Falls through to error parsing, but that also fails, so returns generic parse error
        assertTrue(result is StreamingEvent.Error)
        assertTrue((result as StreamingEvent.Error).message.contains("Failed to parse"))
    }

    @Test
    fun `parseOpenAiEvent - data with extra whitespace parsed correctly`() {
        val line = """data:   {"id":"chatcmpl-123","choices":[{"delta":{"content":"Hi"}}]}"""

        val result = parser.parseOpenAiEvent(line)

        assertTrue(result is StreamingEvent.Content)
        assertEquals("Hi", (result as StreamingEvent.Content).content)
    }

    @Test
    fun `parseOpenAiEvent - null content in delta returns KeepAlive`() {
        val line = """data: {"id":"chatcmpl-123","choices":[{"delta":{}}]}"""

        val result = parser.parseOpenAiEvent(line)

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOpenAiEvents - multiple lines parsed correctly`() {
        val content = """
            data: {"id":"1","choices":[{"delta":{"content":"Hello"}}]}
            data: {"id":"2","choices":[{"delta":{"content":" world"}}]}
            data: [DONE]
        """.trimIndent()

        val results = parser.parseOpenAiEvents(content)

        assertEquals(3, results.size)
        assertTrue(results[0] is StreamingEvent.Content)
        assertEquals("Hello", (results[0] as StreamingEvent.Content).content)
        assertTrue(results[1] is StreamingEvent.Content)
        assertEquals(" world", (results[1] as StreamingEvent.Content).content)
        assertTrue(results[2] is StreamingEvent.Done)
    }

    @Test
    fun `parseOpenAiEvents - filters out KeepAlive events`() {
        val content = """

            : comment
            data: {"id":"1","choices":[{"delta":{"content":"Hi"}}]}

        """.trimIndent()

        val results = parser.parseOpenAiEvents(content)

        assertEquals(1, results.size)
        assertTrue(results[0] is StreamingEvent.Content)
    }

    // ============================================================================
    // Ollama NDJSON Format Tests
    // ============================================================================

    @Test
    fun `parseOllamaEvent - content message returns Content event`() {
        val line = """{"model":"llama3.2","message":{"role":"assistant","content":"Hello"},"done":false}"""

        val result = parser.parseOllamaEvent(line)

        assertTrue(result is StreamingEvent.Content)
        assertEquals("Hello", (result as StreamingEvent.Content).content)
    }

    @Test
    fun `parseOllamaEvent - done true returns Done event`() {
        val line = """{"model":"llama3.2","message":{"role":"assistant","content":""},"done":true}"""

        val result = parser.parseOllamaEvent(line)

        assertTrue(result is StreamingEvent.Done)
        assertEquals("llama3.2", (result as StreamingEvent.Done).model)
    }

    @Test
    fun `parseOllamaEvent - empty line returns KeepAlive`() {
        val result = parser.parseOllamaEvent("")

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOllamaEvent - whitespace only returns KeepAlive`() {
        val result = parser.parseOllamaEvent("   ")

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOllamaEvent - null content returns KeepAlive`() {
        val line = """{"model":"llama3.2","message":{"role":"assistant"},"done":false}"""

        val result = parser.parseOllamaEvent(line)

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOllamaEvent - empty content returns KeepAlive`() {
        val line = """{"model":"llama3.2","message":{"role":"assistant","content":""},"done":false}"""

        val result = parser.parseOllamaEvent(line)

        assertEquals(StreamingEvent.KeepAlive, result)
    }

    @Test
    fun `parseOllamaEvent - invalid JSON returns Error`() {
        val line = "{invalid json}"

        val result = parser.parseOllamaEvent(line)

        assertTrue(result is StreamingEvent.Error)
        assertTrue((result as StreamingEvent.Error).message.contains("Failed to parse"))
    }

    @Test
    fun `parseOllamaEvent - well-formed error response returns KeepAlive since it parses as valid response`() {
        // Name is historical (W5 keeps test names stable). `{"error":"..."}` decoded as a
        // response with a null message and done=false and passed as a keep-alive; it now
        // surfaces as an Error carrying the server's text.
        val line = """{"error":"model not found"}"""

        val result = parser.parseOllamaEvent(line)

        assertTrue("expected Error, got $result", result is StreamingEvent.Error)
        assertEquals("model not found", (result as StreamingEvent.Error).message)
        assertNull(result.httpStatus)
    }

    @Test
    fun `parseOllamaEvent - messageless response without an error is still KeepAlive`() {
        assertEquals(StreamingEvent.KeepAlive, parser.parseOllamaEvent("""{"model":"llama3","done":false}"""))
        assertEquals(StreamingEvent.KeepAlive, parser.parseOllamaEvent("{}"))
    }

    @Test
    fun `parseOllamaEvent - done with stats returns Done`() {
        val line = """{"model":"llama3.2","done":true,"total_duration":1234567890,"eval_count":50}"""

        val result = parser.parseOllamaEvent(line)

        assertTrue(result is StreamingEvent.Done)
    }

    @Test
    fun `parseOllamaEvents - multiple lines parsed correctly`() {
        val content = """
            {"model":"llama3.2","message":{"role":"assistant","content":"Hi"},"done":false}
            {"model":"llama3.2","message":{"role":"assistant","content":" there"},"done":false}
            {"model":"llama3.2","done":true}
        """.trimIndent()

        val results = parser.parseOllamaEvents(content)

        assertEquals(3, results.size)
        assertTrue(results[0] is StreamingEvent.Content)
        assertEquals("Hi", (results[0] as StreamingEvent.Content).content)
        assertTrue(results[1] is StreamingEvent.Content)
        assertEquals(" there", (results[1] as StreamingEvent.Content).content)
        assertTrue(results[2] is StreamingEvent.Done)
    }

    @Test
    fun `parseOllamaEvents - filters out KeepAlive events`() {
        val content = """

            {"model":"llama3.2","message":{"role":"assistant","content":"Hello"},"done":false}

        """.trimIndent()

        val results = parser.parseOllamaEvents(content)

        assertEquals(1, results.size)
        assertTrue(results[0] is StreamingEvent.Content)
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun `parseOpenAiEvent - special characters in content handled`() {
        val line = """data: {"id":"1","choices":[{"delta":{"content":"Hello\nWorld\t!"}}]}"""

        val result = parser.parseOpenAiEvent(line)

        assertTrue(result is StreamingEvent.Content)
        assertEquals("Hello\nWorld\t!", (result as StreamingEvent.Content).content)
    }

    @Test
    fun `parseOllamaEvent - unicode characters in content handled`() {
        val line = """{"model":"llama3.2","message":{"role":"assistant","content":"Hello 世界 🌍"},"done":false}"""

        val result = parser.parseOllamaEvent(line)

        assertTrue(result is StreamingEvent.Content)
        assertEquals("Hello 世界 🌍", (result as StreamingEvent.Content).content)
    }

    @Test
    fun `default singleton instance works correctly`() {
        val result = SseEventParser.default.parseOpenAiEvent("data: [DONE]")

        assertTrue(result is StreamingEvent.Done)
    }

    @Test
    fun `parseOpenAiEvent - multiple content chunks in single response`() {
        val line = """data: {"id":"1","choices":[{"delta":{"content":"part1"}},{"delta":{"content":"part2"}}]}"""

        val result = parser.parseOpenAiEvent(line)

        // Should use first choice
        assertTrue(result is StreamingEvent.Content)
        assertEquals("part1", (result as StreamingEvent.Content).content)
    }
}
