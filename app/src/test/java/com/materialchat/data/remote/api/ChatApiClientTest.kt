package com.materialchat.data.remote.api

import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApiClientTest {

    @Test
    fun `OpenAI simple completion omits reasoning effort when disabled`() = runTest {
        val capturedBodies = mutableListOf<String>()
        val apiClient = ChatApiClient(okHttpClient = capturingClient(capturedBodies))

        val result = apiClient.generateSimpleCompletion(
            provider = openAiProvider(),
            prompt = "Generate a title",
            model = "gpt-4o-mini",
            apiKey = "test-key",
            systemPrompt = "Only return a title",
            reasoningEffort = ReasoningEffort.NONE
        )

        assertTrue(result.isSuccess)
        assertEquals("💡 Compact Title", result.getOrThrow())
        val body = capturedBodies.single()
        assertFalse(body.contains("reasoning_effort"))
        assertFalse(body.contains(":null"))
    }

    @Test
    fun `OpenAI simple completion keeps reasoning effort when enabled`() = runTest {
        val capturedBodies = mutableListOf<String>()
        val apiClient = ChatApiClient(okHttpClient = capturingClient(capturedBodies))

        val result = apiClient.generateSimpleCompletion(
            provider = openAiProvider(),
            prompt = "Generate a title",
            model = "gpt-4o-mini",
            apiKey = "test-key",
            systemPrompt = "Only return a title",
            reasoningEffort = ReasoningEffort.HIGH
        )

        assertTrue(result.isSuccess)
        val body = capturedBodies.single()
        assertTrue(body.contains("\"reasoning_effort\":\"high\""))
    }

    private fun capturingClient(capturedBodies: MutableList<String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                capturedBodies += buffer.readUtf8()

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"choices":[{"message":{"content":"💡 Compact Title"}}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()
    }

    private fun openAiProvider(): Provider {
        return Provider(
            id = "openai-test",
            name = "OpenAI Test",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://example.test",
            defaultModel = "gpt-4o-mini",
            requiresApiKey = true,
            isActive = true
        )
    }
}
