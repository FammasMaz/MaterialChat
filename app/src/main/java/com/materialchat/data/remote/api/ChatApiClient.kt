package com.materialchat.data.remote.api

import com.materialchat.data.remote.dto.OllamaChatRequest
import com.materialchat.data.remote.dto.OllamaChatResponse
import com.materialchat.data.remote.dto.OllamaMessage
import com.materialchat.data.remote.dto.OpenAiChatRequest
import com.materialchat.data.remote.dto.OpenAiChatResponse
import com.materialchat.data.remote.dto.OpenAiContent
import com.materialchat.data.remote.dto.OpenAiContentPart
import com.materialchat.data.remote.dto.OpenAiMessage
import com.materialchat.data.remote.dto.ImageUrl
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
        val url = "${normalizeBaseUrl(baseUrl)}/v1/chat/completions"
        android.util.Log.d("ChatApiClient", "OpenAI streaming URL: $url")
        android.util.Log.d("ChatApiClient", "OpenAI request body: ${json.encodeToString(request)}")
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
                    android.util.Log.d("ChatApiClient", "OpenAI response code: ${resp.code}")
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        android.util.Log.e("ChatApiClient", "OpenAI error response: $errorBody")
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

        // Create request body with thinking enabled
        val request = OllamaChatRequest(
            model = model,
            messages = ollamaMessages,
            stream = true,
            think = true,
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
     * Generates a simple non-streaming completion from the AI provider.
     * Useful for short tasks like generating conversation titles.
     *
     * @param provider The provider to use
     * @param prompt The prompt to send
     * @param model The model to use
     * @param apiKey The API key (if required)
     * @return Result containing the generated text or an error
     */
    suspend fun generateSimpleCompletion(
        provider: Provider,
        prompt: String,
        model: String,
        apiKey: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> generateOpenAiCompletion(
                    baseUrl = provider.baseUrl,
                    model = model,
                    prompt = prompt,
                    apiKey = apiKey ?: ""
                )
                ProviderType.OLLAMA_NATIVE -> generateOllamaCompletion(
                    baseUrl = provider.baseUrl,
                    model = model,
                    prompt = prompt
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a non-streaming completion from an OpenAI-compatible API.
     */
    private fun generateOpenAiCompletion(
        baseUrl: String,
        model: String,
        prompt: String,
        apiKey: String
    ): Result<String> {
        val messages = listOf(OpenAiMessage(role = "user", content = OpenAiContent.Text(prompt)))
        val request = OpenAiChatRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = 0.7
        )

        val requestBody = json.encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)

        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                return@use Result.failure(IOException("API error ${resp.code}: $errorBody"))
            }

            val body = resp.body?.string() ?: return@use Result.failure(IOException("Empty response"))
            try {
                val jsonResponse = json.decodeFromString<OpenAiChatResponse>(body)
                val content = jsonResponse.choices.firstOrNull()?.message?.content
                    ?: return@use Result.failure(IOException("No content in response"))
                Result.success(content.trim())
            } catch (e: Exception) {
                Result.failure(IOException("Failed to parse response: ${e.message}"))
            }
        }
    }

    /**
     * Generates a non-streaming completion from an Ollama server.
     * Uses streaming mode internally since some Ollama proxies don't support stream=false.
     */
    private fun generateOllamaCompletion(
        baseUrl: String,
        model: String,
        prompt: String
    ): Result<String> {
        val messages = listOf(OllamaMessage(role = "user", content = prompt))
        // Use streaming mode like the regular chat - some APIs don't support stream=false
        val request = OllamaChatRequest(
            model = model,
            messages = messages,
            stream = true,
            think = true,  // Same as regular chat
            options = com.materialchat.data.remote.dto.OllamaOptions(temperature = 0.7)
        )

        val jsonBody = json.encodeToString(request)
        android.util.Log.d("ChatApiClient", "Ollama request URL: ${baseUrl.trimEnd('/')}/api/chat")
        android.util.Log.d("ChatApiClient", "Ollama request body: $jsonBody")

        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val url = "${baseUrl.trimEnd('/')}/api/chat"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                android.util.Log.e("ChatApiClient", "Ollama error response: $errorBody")
                return@use Result.failure(IOException("API error ${resp.code}: $errorBody"))
            }

            // Collect streaming response chunks
            val reader = resp.body?.source()?.inputStream()?.bufferedReader()
                ?: return@use Result.failure(IOException("Empty response"))

            val contentBuilder = StringBuilder()
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    try {
                        val chunk = json.decodeFromString<OllamaChatResponse>(line)
                        chunk.message?.content?.let { contentBuilder.append(it) }
                        if (chunk.done) break
                    } catch (e: Exception) {
                        android.util.Log.w("ChatApiClient", "Failed to parse chunk: $line")
                    }
                }
            }

            val content = contentBuilder.toString().trim()
            android.util.Log.d("ChatApiClient", "Ollama collected response: $content")

            if (content.isEmpty()) {
                Result.failure(IOException("No content in response"))
            } else {
                Result.success(content)
            }
        }
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
                ProviderType.OPENAI_COMPATIBLE -> "${normalizeBaseUrl(provider.baseUrl)}/v1/models"
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
        var hasContent = false
        var lastModel: String? = null

        reader.lineSequence().forEach { line ->
            if (isCancelled.get()) return

            android.util.Log.d("ChatApiClient", "SSE line: $line")
            val event = sseEventParser.parseOpenAiEvent(line)
            android.util.Log.d("ChatApiClient", "Parsed event: $event")

            when (event) {
                is StreamingEvent.Done -> {
                    onEvent(event)
                    return
                }
                is StreamingEvent.Error -> {
                    android.util.Log.e("ChatApiClient", "Stream error: ${event.message}")
                    onEvent(event)
                    return
                }
                is StreamingEvent.Content -> {
                    hasContent = true
                    android.util.Log.d("ChatApiClient", "Content chunk: '${event.content}'")
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

        // If stream ended naturally without explicit Done (some APIs combine content+finish in one chunk)
        // emit Done to properly finalize the message
        if (hasContent && !isCancelled.get()) {
            android.util.Log.d("ChatApiClient", "Stream ended, emitting implicit Done")
            onEvent(StreamingEvent.Done(model = lastModel))
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
     * Handles multimodal messages with image attachments.
     */
    private fun buildOpenAiMessages(
        messages: List<Message>,
        systemPrompt: String?
    ): List<OpenAiMessage> {
        val result = mutableListOf<OpenAiMessage>()

        // Add system prompt if provided
        if (!systemPrompt.isNullOrBlank()) {
            result.add(OpenAiMessage(
                role = "system",
                content = OpenAiContent.Text(systemPrompt)
            ))
        }

        // Add conversation messages
        messages.forEach { message ->
            val content = if (message.attachments.isNotEmpty()) {
                // Build multimodal content with text and images
                val parts = mutableListOf<OpenAiContentPart>()

                // Add text content first
                if (message.content.isNotBlank()) {
                    parts.add(OpenAiContentPart.TextPart(text = message.content))
                }

                // Add image attachments
                message.attachments.forEach { attachment ->
                    val dataUrl = "data:${attachment.mimeType};base64,${attachment.base64Data}"
                    parts.add(OpenAiContentPart.ImageUrlPart(
                        imageUrl = ImageUrl(url = dataUrl)
                    ))
                }

                OpenAiContent.Parts(parts)
            } else {
                // Simple text content
                OpenAiContent.Text(message.content)
            }

            result.add(OpenAiMessage(
                role = message.role.toApiRole(),
                content = content
            ))
        }

        return result
    }

    /**
     * Converts domain Messages to Ollama format with optional system prompt.
     * Handles multimodal messages with image attachments.
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
            val images = if (message.attachments.isNotEmpty()) {
                // Ollama expects raw base64 strings (without data URL prefix)
                message.attachments.map { it.base64Data }
            } else {
                null
            }

            result.add(OllamaMessage(
                role = message.role.toApiRole(),
                content = message.content,
                images = images
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
         * Normalizes a base URL by removing trailing API version paths.
         * This prevents duplication when the user includes /v1 in their base URL.
         *
         * Examples:
         * - "https://api.example.com/v1" -> "https://api.example.com"
         * - "https://api.example.com/v1/" -> "https://api.example.com"
         * - "https://api.example.com" -> "https://api.example.com"
         */
        fun normalizeBaseUrl(url: String): String {
            return url.trimEnd('/')
                .removeSuffix("/v1")
                .removeSuffix("/api")
                .trimEnd('/')
        }

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
