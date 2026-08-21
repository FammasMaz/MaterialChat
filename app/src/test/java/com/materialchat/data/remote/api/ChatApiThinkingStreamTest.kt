package com.materialchat.data.remote.api

import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end reproduction: streams the exact SSE payload captured from
 * LLM-API-Key-Proxy (opencode/x-preview-f-free, reasoning_effort=max)
 * through ChatApiClient.streamOpenAiChat and verifies thinking survives.
 */
class ChatApiThinkingStreamTest {

    private val proxyStream = buildString {
        append("data: {\"id\": \"1\", \"object\": \"chat.completion.chunk\", \"created\": 1787304014, \"model\": \"x-preview-f-free\", \"choices\": [{\"index\": 0, \"delta\": {\"role\": \"assistant\", \"reasoning_content\": \"The user wants\", \"reasoning\": \"The user wants\"}, \"finish_reason\": null}]}\n\n")
        append("data: {\"id\": \"1\", \"object\": \"chat.completion.chunk\", \"created\": 1787304014, \"model\": \"x-preview-f-free\", \"choices\": [{\"index\": 0, \"delta\": {\"role\": \"assistant\", \"reasoning_content\": \" 15 * 17 = \", \"reasoning\": \" 15 * 17 = \"}, \"finish_reason\": null}]}\n\n")
        append("data: {\"id\": \"1\", \"object\": \"chat.completion.chunk\", \"created\": 1787304014, \"model\": \"x-preview-f-free\", \"choices\": [{\"index\": 0, \"delta\": {\"role\": \"assistant\", \"content\": \"**255**\"}, \"finish_reason\": null}]}\n\n")
        append("data: {\"id\": \"1\", \"object\": \"chat.completion.chunk\", \"created\": 1787304014, \"model\": \"x-preview-f-free\", \"choices\": [{\"index\": 0, \"finish_reason\": \"stop\", \"delta\": {\"role\": \"assistant\", \"content\": \"\"}}], \"usage\": {\"prompt_tokens\": 97, \"completion_tokens\": 55, \"total_tokens\": 152}}\n\n")
        append("data: [DONE]\n\n")
    }

    @Test
    fun `streamOpenAiChat surfaces reasoning_content as thinking`() = runTest {
        val client = streamingClient(proxyStream)
        val apiClient = ChatApiClient(okHttpClient = client)

        val events = apiClient.streamOpenAiChat(
            baseUrl = "https://example.test",
            model = "opencode/x-preview-f-free",
            messages = emptyList(),
            apiKey = "test-key",
            reasoningEffort = ReasoningEffort.MAX
        ).toList()

        val contentEvents = events.filterIsInstance<StreamingEvent.Content>()
        val thinking = contentEvents.mapNotNull { it.thinking }.joinToString("")
        val content = contentEvents.map { it.content }.joinToString("")

        assertEquals("The user wants 15 * 17 = ", thinking)
        assertEquals("**255**", content)

        val done = events.filterIsInstance<StreamingEvent.Done>()
        assertTrue(done.isNotEmpty())
    }

    private fun streamingClient(body: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()
    }
}
