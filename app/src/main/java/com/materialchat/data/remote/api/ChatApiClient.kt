package com.materialchat.data.remote.api

import com.materialchat.data.remote.dto.OllamaChatRequest
import com.materialchat.data.remote.dto.OllamaMessage
import com.materialchat.data.remote.dto.OpenAiChatRequest
import com.materialchat.data.remote.dto.OpenAiMessage
import com.materialchat.data.remote.sse.SseEventParser
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * API client for streaming chat completions from AI providers.
 *
 * Supports:
 * - OpenAI-compatible APIs (OpenAI, Groq, Together, etc.)
 * - Ollama local LLM server
 *
 * Uses OkHttp for HTTP requests and Kotlin Flow for streaming responses.
 */
class ChatApiClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val sseEventParser: SseEventParser = SseEventParser.default
) {

    private val activeCall = AtomicReference<Call?>(null)
    private val isCancelled = AtomicBoolean(false)

    /**
     * Streams a chat completion from the given provider.
     *
     * @param provider The AI provider to use
     * @param messages The conversation history
     * @param model The model to use for completion
     * @param apiKey The API key (required for OpenAI-compatible providers)
     * @param systemPrompt Optional system prompt to prepend
     * @param temperature The sampling temperature (0.0-2.0)
     * @return Flow of StreamingEvents
     */
    fun streamChat(
        provider: Provider,
        messages: List<Message>,
        model: String,
        apiKey: String?,
        systemPrompt: String? = null,
        temperature: Double = 0.7
    ): Flow<StreamingEvent> {
        return when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> streamOpenAiChat(
                baseUrl = provider.baseUrl,
                model = model,
                messages = messages,
                apiKey = apiKey ?: "",
                systemPrompt = systemPrompt,
                temperature = temperature
            )
            ProviderType.OLLAMA_NATIVE -> streamOllamaChat(
                baseUrl = provider.baseUrl,
                model = model,
                messages = messages,
                systemPrompt = systemPrompt,
                temperature = temperature
            )
        }
    }

    /**
     * Streams a chat completion from an OpenAI-compatible API.
     *
     * Uses Server-Sent Events (SSE) format for streaming.
     *
     * @param baseUrl The API base URL (e.g., "https://api.openai.com")
     * @param model The model ID (e.g., "gpt-4o")
     * @param messages The conversation history
     * @param apiKey The API key for authentication
     * @param systemPrompt Optional system prompt to prepend
     * @param temperature The sampling temperature
     * @return Flow of StreamingEvents
     */
    fun streamOpenAiChat(
        baseUrl: String,
        model: String,
        messages: List<Message>,
        apiKey: String,
        systemPrompt: String? = null,
        temperature: Double = 0.7
    ): Flow<StreamingEvent> = callbackFlow {
        isCancelled.set(false)

        // Build the message list with optional system prompt
        val openAiMessages = buildOpenAiMessages(messages, systemPrompt)

        // Create request body
        val request = OpenAiChatRequest(
            model = model,
            messages = openAiMessages,
            stream = true,
            temperature = temperature
        )

        val requestBody = json.encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)

        // Build HTTP request
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        activeCall.set(call)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isCancelled.get()) {
                    trySend(StreamingEvent.fromException(e))
                }
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        val errorMessage = parseErrorMessage(errorBody, resp.code)
                        trySend(StreamingEvent.fromHttpError(resp.code, errorMessage))
                        close()
                        return
                    }

                    // Emit connected event
                    trySend(StreamingEvent.Connected)

                    // Read streaming response
                    val body = resp.body ?: run {
                        trySend(StreamingEvent.Error("Empty response body"))
                        close()
                        return
                    }

                    try {
                        body.source().inputStream().bufferedReader().use { reader ->
                            processOpenAiStream(reader) { event ->
                                if (!isCancelled.get() && isActive) {
                                    trySend(event)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!isCancelled.get()) {
                            trySend(StreamingEvent.fromException(e))
                        }
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            cancelStreaming()
        }
    }

    /**
     * Streams a chat completion from an Ollama server.
     *
     * Uses NDJSON (Newline-Delimited JSON) format for streaming.
     *
     * @param baseUrl The Ollama server URL (e.g., "http://localhost:11434")
     * @param model The model name (e.g., "llama3.2")
     * @param messages The conversation history
     * @param systemPrompt Optional system prompt to prepend
     * @param temperature The sampling temperature
     * @return Flow of StreamingEvents
     */
    fun streamOllamaChat(
        baseUrl: String,
        model: String,
        messages: List<Message>,
        systemPrompt: String? = null,
        temperature: Double = 0.7
    ): Flow<StreamingEvent> = callbackFlow {
        isCancelled.set(false)

        // Build the message list with optional system prompt
        val ollamaMessages = buildOllamaMessages(messages, systemPrompt)

        // Create request body
        val request = OllamaChatRequest(
            model = model,
            messages = ollamaMessages,
            stream = true,
            options = com.materialchat.data.remote.dto.OllamaOptions(
                temperature = temperature
            )
        )

        val requestBody = json.encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)

        // Build HTTP request
        val url = "${baseUrl.trimEnd('/')}/api/chat"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        activeCall.set(call)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isCancelled.get()) {
                    trySend(StreamingEvent.fromException(e))
                }
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        val errorMessage = parseErrorMessage(errorBody, resp.code)
                        trySend(StreamingEvent.fromHttpError(resp.code, errorMessage))
                        close()
                        return
                    }

                    // Emit connected event
                    trySend(StreamingEvent.Connected)

                    // Read streaming response
                    val body = resp.body ?: run {
                        trySend(StreamingEvent.Error("Empty response body"))
                        close()
                        return
                    }

                    try {
                        body.source().inputStream().bufferedReader().use { reader ->
                            processOllamaStream(reader) { event ->
                                if (!isCancelled.get() && isActive) {
                                    trySend(event)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!isCancelled.get()) {
                            trySend(StreamingEvent.fromException(e))
                        }
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            cancelStreaming()
        }
    }

    /**
     * Cancels any active streaming request.
     */
    fun cancelStreaming() {
        isCancelled.set(true)
        activeCall.getAndSet(null)?.cancel()
    }

    /**
     * Tests connectivity to a provider by making a simple request.
     *
     * @param provider The provider to test
     * @param apiKey The API key (if required)
     * @return Result containing success or error
     */
    suspend fun testConnection(
        provider: Provider,
        apiKey: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> "${provider.baseUrl.trimEnd('/')}/v1/models"
                ProviderType.OLLAMA_NATIVE -> "${provider.baseUrl.trimEnd('/')}/api/tags"
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            if (provider.type == ProviderType.OPENAI_COMPATIBLE && !apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("Connection test failed: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Processes an OpenAI SSE stream line by line.
     */
    private fun processOpenAiStream(
        reader: BufferedReader,
        onEvent: (StreamingEvent) -> Unit
    ) {
        reader.lineSequence().forEach { line ->
            if (isCancelled.get()) return

            val event = sseEventParser.parseOpenAiEvent(line)

            when (event) {
                is StreamingEvent.Done -> {
                    onEvent(event)
                    return
                }
                is StreamingEvent.Error -> {
                    onEvent(event)
                    return
                }
                is StreamingEvent.Content -> {
                    onEvent(event)
                }
                is StreamingEvent.Connected,
                is StreamingEvent.KeepAlive -> {
                    // Ignore keep-alive events during streaming
                }
                null -> {
                    // Unknown line format, skip
                }
            }
        }
    }

    /**
     * Processes an Ollama NDJSON stream line by line.
     */
    private fun processOllamaStream(
        reader: BufferedReader,
        onEvent: (StreamingEvent) -> Unit
    ) {
        reader.lineSequence().forEach { line ->
            if (isCancelled.get()) return

            val event = sseEventParser.parseOllamaEvent(line)

            when (event) {
                is StreamingEvent.Done -> {
                    onEvent(event)
                    return
                }
                is StreamingEvent.Error -> {
                    onEvent(event)
                    return
                }
                is StreamingEvent.Content -> {
                    onEvent(event)
                }
                is StreamingEvent.Connected,
                is StreamingEvent.KeepAlive -> {
                    // Ignore keep-alive events during streaming
                }
                null -> {
                    // Unknown line format, skip
                }
            }
        }
    }

    /**
     * Converts domain Messages to OpenAI format with optional system prompt.
     */
    private fun buildOpenAiMessages(
        messages: List<Message>,
        systemPrompt: String?
    ): List<OpenAiMessage> {
        val result = mutableListOf<OpenAiMessage>()

        // Add system prompt if provided
        if (!systemPrompt.isNullOrBlank()) {
            result.add(OpenAiMessage(role = "system", content = systemPrompt))
        }

        // Add conversation messages
        messages.forEach { message ->
            result.add(OpenAiMessage(
                role = message.role.toApiRole(),
                content = message.content
            ))
        }

        return result
    }

    /**
     * Converts domain Messages to Ollama format with optional system prompt.
     */
    private fun buildOllamaMessages(
        messages: List<Message>,
        systemPrompt: String?
    ): List<OllamaMessage> {
        val result = mutableListOf<OllamaMessage>()

        // Add system prompt if provided
        if (!systemPrompt.isNullOrBlank()) {
            result.add(OllamaMessage(role = "system", content = systemPrompt))
        }

        // Add conversation messages
        messages.forEach { message ->
            result.add(OllamaMessage(
                role = message.role.toApiRole(),
                content = message.content
            ))
        }

        return result
    }

    /**
     * Parses an error message from API response body.
     */
    private fun parseErrorMessage(errorBody: String, statusCode: Int): String {
        return try {
            // Try to parse as JSON error
            val errorResponse = json.decodeFromString<ErrorWrapper>(errorBody)
            errorResponse.error?.message
                ?: errorResponse.error?.toString()
                ?: "HTTP $statusCode: $errorBody"
        } catch (e: Exception) {
            "HTTP $statusCode: $errorBody"
        }
    }

    /**
     * Extension function to convert MessageRole to API role string.
     */
    private fun MessageRole.toApiRole(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Default timeout for streaming requests.
         */
        private const val STREAMING_TIMEOUT_SECONDS = 120L

        /**
         * Creates a default OkHttpClient configured for streaming.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(STREAMING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Internal wrapper for parsing error responses.
 */
@kotlinx.serialization.Serializable
private data class ErrorWrapper(
    val error: ErrorDetail? = null,
    val message: String? = null
)

@kotlinx.serialization.Serializable
private data class ErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
