package com.materialchat.data.remote.api

import com.materialchat.data.auth.NativeAuthCredential
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
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
 * Verifies the Codex (Responses API) stream surfaces the FULL reasoning text,
 * not just headline-style summaries. Some GPT-5.x streams only expose raw
 * thinking on the terminal reasoning item, so the client must reconcile it.
 */
class CodexThinkingStreamTest {

    @Test
    fun `reconciles full reasoning from terminal reasoning item`() = runTest {
        // Stream shape: summary deltas stream live, then output_item.done carries
        // the complete RAW content that was never streamed as deltas.
        val sse = buildString {
            append("event: response.reasoning_summary_text.delta\n")
            append("data: {\"type\":\"response.reasoning_summary_text.delta\",\"output_index\":0,\"summary_index\":0,\"delta\":\"**Scanning**\"}\n\n")
            append("event: response.reasoning_summary_text.done\n")
            append("data: {\"type\":\"response.reasoning_summary_text.done\",\"output_index\":0,\"summary_index\":0,\"text\":\"**Scanning** the request\"}\n\n")
            append("event: response.output_item.done\n")
            append("data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"reasoning\",\"content\":[{\"type\":\"reasoning_text\",\"text\":\"The user asked 15*17. I recall 15*17=255 from multiplication tables. Double-check: 15*17 = 15*10 + 15*7 = 150+105 = 255. Correct.\"}]}}\n\n")
            append("event: response.output_text.delta\n")
            append("data: {\"type\":\"response.output_text.delta\",\"delta\":\"**255**\"}\n\n")
            append("event: response.completed\n")
            append("data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"reasoning\",\"content\":[{\"type\":\"reasoning_text\",\"text\":\"The user asked 15*17. I recall 15*17=255 from multiplication tables. Double-check: 15*17 = 15*10 + 15*7 = 150+105 = 255. Correct.\"}]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"**255**\"}]}]}}\n\n")
        }

        val events = stream(sse)

        val thinking = events.filterIsInstance<StreamingEvent.Content>()
            .mapNotNull { it.thinking }.joinToString("")
        val content = events.filterIsInstance<StreamingEvent.Content>()
            .map { it.content }.joinToString("")

        val rawThinking = "The user asked 15*17. I recall 15*17=255 from multiplication tables. " +
            "Double-check: 15*17 = 15*10 + 15*7 = 150+105 = 255. Correct."
        assertEquals("**Scanning** the request\n\n$rawThinking", thinking)
        assertEquals("**255**", content)
    }

    @Test
    fun `emits missing summary parts on completed without duplicating streamed ones`() = runTest {
        val sse = buildString {
            append("event: response.reasoning_summary_text.delta\n")
            append("data: {\"type\":\"response.reasoning_summary_text.delta\",\"output_index\":0,\"summary_index\":0,\"delta\":\"Part one\"}\n\n")
            append("event: response.reasoning_summary_text.done\n")
            append("data: {\"type\":\"response.reasoning_summary_text.done\",\"output_index\":0,\"summary_index\":0,\"text\":\"Part one\"}\n\n")
            append("event: response.completed\n")
            append("data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"Part one\"},{\"type\":\"summary_text\",\"text\":\"<!-- -->\"},{\"type\":\"summary_text\",\"text\":\"Part two\"}]}]}}\n\n")
        }

        val events = stream(sse)

        val thinking = events.filterIsInstance<StreamingEvent.Content>()
            .mapNotNull { it.thinking }.joinToString("")
        assertEquals("Part one\n\nPart two", thinking)
    }

    @Test
    fun `streams raw reasoning deltas directly`() = runTest {
        val sse = buildString {
            append("event: response.reasoning_text.delta\n")
            append("data: {\"type\":\"response.reasoning_text.delta\",\"delta\":\"raw thought \"}\n\n")
            append("event: response.reasoning_text.delta\n")
            append("data: {\"type\":\"response.reasoning_text.delta\",\"delta\":\"continues\"}\n\n")
            append("event: response.output_text.delta\n")
            append("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Answer\"}\n\n")
            append("data: [DONE]\n\n")
        }

        val events = stream(sse)

        val thinking = events.filterIsInstance<StreamingEvent.Content>()
            .mapNotNull { it.thinking }.joinToString("")
        assertEquals("raw thought continues", thinking)
    }

    private suspend fun stream(body: String): List<StreamingEvent> {
        val apiClient = ChatApiClient(okHttpClient = streamingClient(body))
        val credential = NativeAuthCredential(
            providerType = ProviderType.CODEX_NATIVE.name,
            accessToken = "test-token",
            accountId = "acc-1"
        )
        return apiClient.streamChat(
            provider = codexProvider(),
            messages = listOf(
                Message(conversationId = "c", role = MessageRole.USER, content = "hi")
            ),
            model = "gpt-5.6",
            apiKey = NativeAuthCredential.encode(credential),
            systemPrompt = "sys",
            reasoningEffort = ReasoningEffort.MAX
        ).toList()
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

    private fun codexProvider(): Provider {
        return Provider(
            id = "codex-test",
            name = "Codex Test",
            type = ProviderType.CODEX_NATIVE,
            baseUrl = "https://example.test",
            defaultModel = "gpt-5.6",
            requiresApiKey = true,
            isActive = true
        )
    }
}
