package com.materialchat.data.repository

import android.content.Context
import com.materialchat.data.auth.NativeAuthCredential
import com.materialchat.data.auth.NativeAuthManager
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.remote.api.ChatApiClient
import com.materialchat.data.remote.api.ModelListApiClient
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderHttpException
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.LocalModelRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The repository used to wrap a provider's HTTP refusal in a plain [Exception], so the
 * assistant had to recognise a 429 from the body text. The status now travels typed:
 * [StreamingState.Error.httpStatus] and the [ProviderHttpException] carry it, the message
 * text stays the parsed body, and a re-wrap that copies only [StreamingState.Error.error]
 * keeps the status through the default argument.
 */
class ChatRepositoryHttpStatusTest {

    @Test
    fun `401 403 429 and 500 arrive typed on the streaming state`() = runBlocking {
        for (status in listOf(401, 403, 429, 500)) {
            for (type in listOf(ProviderType.OPENAI_COMPATIBLE, ProviderType.CODEX_NATIVE)) {
                val states = repository(refusingClient(status)).sendMessage(
                    provider = provider(type),
                    messages = listOf(userMessage()),
                    model = "model",
                    reasoningEffort = ReasoningEffort.NONE,
                    systemPrompt = null,
                    disableTools = false,
                    nativeWebSearch = false,
                    ownerId = null
                ).toList()

                val error = states.filterIsInstance<StreamingState.Error>().singleOrNull()
                    ?: error("$type/$status: expected one Error state, got $states")
                assertEquals("$type/$status state status", status, error.httpStatus)
                val cause = error.error
                assertTrue("$type/$status exception type", cause is ProviderHttpException)
                assertEquals("$type/$status exception status", status, (cause as ProviderHttpException).httpStatus)
                assertEquals("$type/$status message text unchanged", "refused with $status", cause.message)
                assertFalse("$type/$status must not read as connectivity", cause is IOException)
            }
        }
    }

    @Test
    fun `a re-wrap that copies only the exception keeps the status`() {
        val original = StreamingState.Error(error = ProviderHttpException(429, "busy"))
        assertEquals(429, original.httpStatus)

        val rewrapped = StreamingState.Error(error = original.error, partialContent = null, messageId = "m")
        assertEquals(429, rewrapped.httpStatus)

        val plain = StreamingState.Error(error = IllegalStateException("no messages"))
        assertNull(plain.httpStatus)
    }

    private fun repository(okHttpClient: OkHttpClient): ChatRepositoryImpl {
        val encrypted = mockk<EncryptedPreferences>()
        coEvery { encrypted.getApiKey(any()) } returns "key"
        val auth = mockk<NativeAuthManager>()
        coEvery { auth.getFreshCredential(any(), any()) } answers {
            NativeAuthCredential(providerType = secondArg<ProviderType>().name, accessToken = "token")
        }
        return ChatRepositoryImpl(
            chatApiClient = ChatApiClient(okHttpClient = okHttpClient),
            modelListApiClient = mockk<ModelListApiClient>(),
            encryptedPreferences = encrypted,
            nativeAuthManager = auth,
            localModelRepository = mockk<LocalModelRepository>(relaxed = true),
            context = mockk<Context>(relaxed = true)
        )
    }

    private fun refusingClient(status: Int): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("Refused")
                    .body(
                        """{"error":{"message":"refused with $status"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()
    }

    private fun provider(type: ProviderType) = Provider(
        id = "p-${type.name.lowercase()}",
        name = type.displayName,
        type = type,
        baseUrl = "https://example.test",
        defaultModel = "model",
        requiresApiKey = true,
        isActive = true
    )

    private fun userMessage() = Message(
        conversationId = "c",
        role = MessageRole.USER,
        content = "hi"
    )
}
