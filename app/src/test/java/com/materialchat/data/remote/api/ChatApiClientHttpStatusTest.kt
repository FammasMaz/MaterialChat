package com.materialchat.data.remote.api

import com.materialchat.data.auth.NativeAuthCredential
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every HTTP-backed wire format the client speaks must surface a non-2xx status typed on
 * [StreamingEvent.Error.httpStatus], with the parsed body text unchanged, so no consumer has
 * to recognise "429" from the message. In-stream provider errors (a 200 that later carries an
 * error object) are not HTTP refusals and keep a null status.
 */
class ChatApiClientHttpStatusTest {

    private val statuses = listOf(401, 403, 429, 500)

    private val httpProviders = listOf(
        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.OLLAMA_NATIVE,
        ProviderType.GITHUB_COPILOT_NATIVE,
        ProviderType.CODEX_NATIVE,
        ProviderType.ANTIGRAVITY_NATIVE
    )

    @Test
    fun `every wire format carries the HTTP status typed on the error event`() = runTest {
        for (type in httpProviders) {
            for (status in statuses) {
                val events = ChatApiClient(okHttpClient = refusingClient(status)).streamChat(
                    provider = provider(type),
                    messages = listOf(userMessage()),
                    model = "model",
                    apiKey = credentialFor(type),
                    reasoningEffort = ReasoningEffort.NONE
                ).toList()

                val error = events.filterIsInstance<StreamingEvent.Error>().singleOrNull()
                    ?: error("$type/$status: expected exactly one error event, got $events")
                assertEquals("$type/$status status", status, error.httpStatus)
                assertEquals("$type/$status code string", status.toString(), error.code)
                assertEquals("$type/$status message", "refused with $status", error.message)
            }
        }
    }

    @Test
    fun `recoverability still follows the status`() {
        assertEquals(false, StreamingEvent.fromHttpError(401, "m").isRecoverable)
        assertEquals(false, StreamingEvent.fromHttpError(403, "m").isRecoverable)
        assertEquals(true, StreamingEvent.fromHttpError(429, "m").isRecoverable)
        assertEquals(true, StreamingEvent.fromHttpError(500, "m").isRecoverable)
    }

    @Test
    fun `an in-stream provider error on a 200 response has no HTTP status`() = runTest {
        // A Responses-API stream that opens fine (200) and then reports an error event: the
        // failure is in-band, not an HTTP refusal, so no status is typed on it.
        val sse = "event: error\ndata: {\"type\":\"error\",\"message\":\"model overloaded\"}\n\n"
        val events = ChatApiClient(okHttpClient = respondingClient(200, sse, "text/event-stream"))
            .streamChat(
                provider = provider(ProviderType.CODEX_NATIVE),
                messages = listOf(userMessage()),
                model = "model",
                apiKey = credentialFor(ProviderType.CODEX_NATIVE),
                reasoningEffort = ReasoningEffort.NONE
            ).toList()

        val error = events.filterIsInstance<StreamingEvent.Error>().single()
        assertEquals("model overloaded", error.message)
        assertNull(error.httpStatus)
    }

    @Test
    fun `a connectivity failure has no HTTP status`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { throw java.net.ConnectException("refused") }
            .build()
        val events = ChatApiClient(okHttpClient = client).streamChat(
            provider = provider(ProviderType.OPENAI_COMPATIBLE),
            messages = listOf(userMessage()),
            model = "model",
            apiKey = "key",
            reasoningEffort = ReasoningEffort.NONE
        ).toList()

        val error = events.filterIsInstance<StreamingEvent.Error>().single()
        assertEquals(StreamingEvent.Companion.ErrorCode.CONNECTION_FAILED, error.code)
        assertNull(error.httpStatus)
    }

    private fun refusingClient(status: Int): OkHttpClient = respondingClient(
        status,
        """{"error":{"message":"refused with $status","type":"refusal"}}""",
        "application/json"
    )

    private fun respondingClient(status: Int, body: String, mediaType: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message(if (status == 200) "OK" else "Refused")
                    .body(body.toResponseBody(mediaType.toMediaType()))
                    .build()
            }
            .build()
    }

    private fun credentialFor(type: ProviderType): String? = when (type) {
        ProviderType.OPENAI_COMPATIBLE -> "key"
        ProviderType.OLLAMA_NATIVE -> null
        else -> NativeAuthCredential.encode(
            NativeAuthCredential(
                providerType = type.name,
                accessToken = "token",
                projectId = "project"
            )
        )
    }

    private fun provider(type: ProviderType) = Provider(
        id = "p-${type.name.lowercase()}",
        name = type.displayName,
        type = type,
        // Antigravity appends `:streamGenerateContent` to the base URL, which needs a path
        // segment to attach to (a bare host would read as a port).
        baseUrl = if (type == ProviderType.ANTIGRAVITY_NATIVE) "https://example.test/v1internal" else "https://example.test",
        defaultModel = "model",
        requiresApiKey = type != ProviderType.OLLAMA_NATIVE,
        isActive = true
    )

    private fun userMessage() = Message(
        conversationId = "c",
        role = MessageRole.USER,
        content = "hi"
    )
}
