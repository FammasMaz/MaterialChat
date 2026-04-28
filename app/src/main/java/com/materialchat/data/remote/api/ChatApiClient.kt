package com.materialchat.data.remote.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.materialchat.data.remote.dto.OllamaChatRequest
import com.materialchat.data.remote.dto.OllamaChatResponse
import com.materialchat.data.remote.dto.OllamaMessage
import com.materialchat.data.remote.dto.OpenAiChatRequest
import com.materialchat.data.remote.dto.OpenAiChatResponse
import com.materialchat.data.remote.dto.OpenAiContent
import com.materialchat.data.remote.dto.OpenAiContentPart
import com.materialchat.data.remote.dto.OpenAiImageGenerationRequest
import com.materialchat.data.remote.dto.OpenAiImageGenerationResponse
import com.materialchat.data.remote.dto.OpenAiMessage
import com.materialchat.data.remote.dto.ImageUrl
import com.materialchat.data.remote.sse.SseEventParser
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
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
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    private val appContext: Context? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    },
    private val sseEventParser: SseEventParser = SseEventParser.default
) {

    private val activeCalls = java.util.concurrent.CopyOnWriteArraySet<Call>()

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
        temperature: Double = 0.7,
        reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH,
        disableTools: Boolean = false,
        nativeWebSearch: Boolean = false
    ): Flow<StreamingEvent> {
        return when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> streamOpenAiChat(
                baseUrl = provider.baseUrl,
                model = model,
                messages = messages,
                apiKey = apiKey ?: "",
                systemPrompt = systemPrompt,
                temperature = temperature,
                reasoningEffort = reasoningEffort,
                disableTools = disableTools,
                nativeWebSearch = nativeWebSearch
            )
            ProviderType.OLLAMA_NATIVE -> streamOllamaChat(
                baseUrl = provider.baseUrl,
                model = model,
                messages = messages,
                systemPrompt = systemPrompt,
                temperature = temperature,
                reasoningEffort = reasoningEffort
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
        temperature: Double = 0.7,
        reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH,
        disableTools: Boolean = false,
        nativeWebSearch: Boolean = false
    ): Flow<StreamingEvent> = callbackFlow {
        // Per-flow cancel flag so parallel streams don't interfere
        val cancelled = AtomicBoolean(false)

        // Build the message list with optional system prompt
        val openAiMessages = buildOpenAiMessages(messages, systemPrompt)

        // Create request body
        val request = OpenAiChatRequest(
            model = model,
            messages = openAiMessages,
            stream = true,
            reasoningEffort = reasoningEffort.apiValue,
            tools = if (disableTools) emptyList() else null,
            toolChoice = if (disableTools) "none" else null
        )

        val requestJson = json.encodeToString(request)
        val requestBody = requestJson.toRequestBody(JSON_MEDIA_TYPE)

        // Build HTTP request
        val url = buildChatCompletionsUrl(
            baseUrl = baseUrl,
            forcedVersion = if (nativeWebSearch) "v2" else null
        )
        android.util.Log.d("ChatApiClient", "OpenAI streaming URL: $url")
        android.util.Log.d(
            "ChatApiClient",
            "OpenAI request: model=$model messages=${openAiMessages.size} bytes=${requestJson.length} hasImages=${messages.any { it.attachments.isNotEmpty() }}"
        )
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        activeCalls.add(call)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cancelled.get()) {
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
                            processOpenAiStream(reader, cancelled) { event ->
                                if (!cancelled.get() && isActive) {
                                    trySend(event)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!cancelled.get()) {
                            trySend(StreamingEvent.fromException(e))
                        }
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            cancelled.set(true)
            activeCalls.remove(call)
            call.cancel()
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
        temperature: Double = 0.7,
        reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH
    ): Flow<StreamingEvent> = callbackFlow {
        // Per-flow cancel flag so parallel streams don't interfere
        val cancelled = AtomicBoolean(false)

        // Build the message list with optional system prompt
        val ollamaMessages = buildOllamaMessages(messages, systemPrompt)

        // Create request body with thinking enabled
        val request = OllamaChatRequest(
            model = model,
            messages = ollamaMessages,
            stream = true,
            think = reasoningEffort.enablesThinking,
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
        activeCalls.add(call)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cancelled.get()) {
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
                            processOllamaStream(reader, cancelled) { event ->
                                if (!cancelled.get() && isActive) {
                                    trySend(event)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!cancelled.get()) {
                            trySend(StreamingEvent.fromException(e))
                        }
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            cancelled.set(true)
            activeCalls.remove(call)
            call.cancel()
        }
    }

    /**
     * Cancels any active streaming request.
     */
    fun cancelStreaming() {
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
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
        apiKey: String?,
        systemPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> generateOpenAiCompletion(
                    baseUrl = provider.baseUrl,
                    model = model,
                    prompt = prompt,
                    apiKey = apiKey ?: "",
                    systemPrompt = systemPrompt
                )
                ProviderType.OLLAMA_NATIVE -> generateOllamaCompletion(
                    baseUrl = provider.baseUrl,
                    model = model,
                    prompt = prompt,
                    systemPrompt = systemPrompt
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates an image using an OpenAI-compatible /v1/images/generations endpoint.
     * Returns base64 image data so callers can persist generated images alongside chats.
     */
    suspend fun generateImage(
        provider: Provider,
        prompt: String,
        model: String,
        apiKey: String?,
        size: String = "1024x1024",
        quality: String? = null,
        outputFormat: String = "png"
    ): Result<GeneratedImageData> = withContext(Dispatchers.IO) {
        try {
            when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> generateOpenAiImage(
                    baseUrl = provider.baseUrl,
                    model = model,
                    prompt = prompt,
                    apiKey = apiKey ?: "",
                    size = size,
                    quality = quality,
                    outputFormat = outputFormat
                )
                ProviderType.OLLAMA_NATIVE -> Result.failure(
                    IOException("Image generation requires an OpenAI-compatible provider")
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
        apiKey: String,
        systemPrompt: String? = null
    ): Result<String> {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(OpenAiMessage(role = "system", content = OpenAiContent.Text(systemPrompt)))
            }
            add(OpenAiMessage(role = "user", content = OpenAiContent.Text(prompt)))
        }
        val request = OpenAiChatRequest(
            model = model,
            messages = messages,
            stream = false
        )

        val requestBody = json.encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)

        val url = buildChatCompletionsUrl(baseUrl)
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
     * Generates a base64 image from an OpenAI-compatible image endpoint.
     */
    private fun generateOpenAiImage(
        baseUrl: String,
        model: String,
        prompt: String,
        apiKey: String,
        size: String,
        quality: String?,
        outputFormat: String
    ): Result<GeneratedImageData> {
        val request = OpenAiImageGenerationRequest(
            model = model,
            prompt = prompt,
            n = 1,
            size = size,
            quality = quality,
            outputFormat = outputFormat.lowercase()
        )

        val imageClient = okHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.MINUTES)
            .build()

        var lastError: IOException? = null
        val urls = buildImagesGenerationsUrlsWithFallback(baseUrl)
        for ((index, url) in urls.withIndex()) {
            val requestBody = json.encodeToString(request)
                .toRequestBody(JSON_MEDIA_TYPE)
            val httpRequestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                httpRequestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val call = imageClient.newCall(httpRequestBuilder.build())
            activeCalls.add(call)
            try {
                val response = call.execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        lastError = IOException("Image API error ${resp.code}: $errorBody")
                        if (resp.code != 404 || index == urls.lastIndex) {
                            return Result.failure(lastError!!)
                        }
                        return@use
                    }

                    val body = resp.body?.string()
                        ?: return Result.failure(IOException("Empty image response"))
                    try {
                        val imageResponse = json.decodeFromString<OpenAiImageGenerationResponse>(body)
                        val image = imageResponse.data.firstOrNull()
                            ?: return Result.failure(IOException("Image response contained no data"))
                        val base64 = image.b64Json
                            ?: return Result.failure(IOException("Image response did not include b64_json"))
                        return Result.success(
                            GeneratedImageData(
                                base64Data = base64,
                                mimeType = outputFormat.toMimeType(),
                                model = imageResponse.model ?: model
                            )
                        )
                    } catch (e: Exception) {
                        return Result.failure(IOException("Failed to parse image response: ${e.message}"))
                    }
                }
            } finally {
                activeCalls.remove(call)
            }
        }

        return Result.failure(lastError ?: IOException("Image API request failed"))
    }

    /**
     * Generates a non-streaming completion from an Ollama server.
     * Uses streaming mode internally since some Ollama proxies don't support stream=false.
     */
    private fun generateOllamaCompletion(
        baseUrl: String,
        model: String,
        prompt: String,
        systemPrompt: String? = null
    ): Result<String> {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(OllamaMessage(role = "system", content = systemPrompt))
            }
            add(OllamaMessage(role = "user", content = prompt))
        }
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
            val urls = when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> buildModelsUrlsWithFallback(provider.baseUrl)
                ProviderType.OLLAMA_NATIVE -> listOf("${provider.baseUrl.trimEnd('/')}/api/tags")
            }

            var lastError: Exception? = null
            for (url in urls) {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .get()

                if (provider.type == ProviderType.OPENAI_COMPATIBLE && !apiKey.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext Result.success(true)
                    }
                    lastError = IOException("Connection test failed: ${resp.code}")
                    // If 404, try the next fallback URL
                    if (resp.code != 404) {
                        return@withContext Result.failure(lastError!!)
                    }
                }
            }
            Result.failure(lastError ?: IOException("Connection test failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Processes an OpenAI SSE stream line by line.
     */
    private fun processOpenAiStream(
        reader: BufferedReader,
        cancelled: AtomicBoolean,
        onEvent: (StreamingEvent) -> Unit
    ) {
        var hasContent = false
        var lastModel: String? = null

        reader.lineSequence().forEach { line ->
            if (cancelled.get()) return

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
        if (hasContent && !cancelled.get()) {
            android.util.Log.d("ChatApiClient", "Stream ended, emitting implicit Done")
            onEvent(StreamingEvent.Done(model = lastModel))
        }
    }

    /**
     * Processes an Ollama NDJSON stream line by line.
     */
    private fun processOllamaStream(
        reader: BufferedReader,
        cancelled: AtomicBoolean,
        onEvent: (StreamingEvent) -> Unit
    ) {
        reader.lineSequence().forEach { line ->
            if (cancelled.get()) return

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

        if (!systemPrompt.isNullOrBlank()) {
            result.add(OpenAiMessage(
                role = "system",
                content = OpenAiContent.Text(systemPrompt)
            ))
        }

        messages.forEach { message ->
            if (message.role == MessageRole.ASSISTANT && message.attachments.isNotEmpty()) {
                addGeneratedImageReferenceForOpenAi(result, message)
                return@forEach
            }

            val imageParts = if (message.role == MessageRole.USER) {
                buildOpenAiImageParts(message.attachments)
            } else {
                emptyList()
            }
            val content = if (imageParts.isNotEmpty()) {
                val parts = mutableListOf<OpenAiContentPart>()
                parts.add(OpenAiContentPart.TextPart(
                    text = message.content.ifBlank { "Please consider the attached image." }
                ))
                parts.addAll(imageParts)
                OpenAiContent.Parts(parts)
            } else {
                OpenAiContent.Text(message.content)
            }

            result.add(OpenAiMessage(
                role = message.role.toApiRole(),
                content = content
            ))
        }

        return result
    }

    private fun addGeneratedImageReferenceForOpenAi(
        result: MutableList<OpenAiMessage>,
        message: Message
    ) {
        result.add(OpenAiMessage(
            role = "assistant",
            content = OpenAiContent.Text(message.content.ifBlank { "Generated image." })
        ))

        val imageParts = buildOpenAiImageParts(message.attachments)
        if (imageParts.isEmpty()) return

        val parts = mutableListOf<OpenAiContentPart>()
        parts.add(OpenAiContentPart.TextPart(
            text = "Reference image generated by the assistant earlier in this conversation. Use it as visual context for the user's next request."
        ))
        parts.addAll(imageParts)
        result.add(OpenAiMessage(
            role = "user",
            content = OpenAiContent.Parts(parts)
        ))
    }

    private fun buildOpenAiImageParts(attachments: List<Attachment>): List<OpenAiContentPart> {
        return attachments.mapNotNull { attachment ->
            val base64 = attachment.resolveBase64Data()
            if (base64.isNullOrBlank()) {
                android.util.Log.w("ChatApiClient", "Skipping image attachment with unavailable bytes: ${attachment.uri}")
                null
            } else {
                val dataUrl = "data:${attachment.mimeType};base64,$base64"
                OpenAiContentPart.ImageUrlPart(imageUrl = ImageUrl(url = dataUrl))
            }
        }
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

        if (!systemPrompt.isNullOrBlank()) {
            result.add(OllamaMessage(role = "system", content = systemPrompt))
        }

        messages.forEach { message ->
            if (message.role == MessageRole.ASSISTANT && message.attachments.isNotEmpty()) {
                result.add(OllamaMessage(
                    role = "assistant",
                    content = message.content.ifBlank { "Generated image." }
                ))
                val generatedImages = buildOllamaImages(message.attachments)
                if (generatedImages.isNotEmpty()) {
                    result.add(OllamaMessage(
                        role = "user",
                        content = "Reference image generated by the assistant earlier in this conversation. Use it as visual context for the user's next request.",
                        images = generatedImages
                    ))
                }
                return@forEach
            }

            val images = if (message.role == MessageRole.USER) {
                buildOllamaImages(message.attachments).takeIf { it.isNotEmpty() }
            } else {
                null
            }

            result.add(OllamaMessage(
                role = message.role.toApiRole(),
                content = if (message.content.isBlank() && images != null) {
                    "Please consider the attached image."
                } else {
                    message.content
                },
                images = images
            ))
        }

        return result
    }

    private fun buildOllamaImages(attachments: List<Attachment>): List<String> {
        return attachments.mapNotNull { attachment ->
            attachment.resolveBase64Data().also { base64 ->
                if (base64.isNullOrBlank()) {
                    android.util.Log.w("ChatApiClient", "Skipping image attachment with unavailable bytes: ${attachment.uri}")
                }
            }?.takeIf { it.isNotBlank() }
        }
    }

    private fun Attachment.resolveBase64Data(): String? {
        if (base64Data.isNotBlank()) return base64Data
        val bytes = readAttachmentBytes(uri) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun readAttachmentBytes(uriString: String): ByteArray? {
        if (uriString.isBlank()) return null
        return runCatching {
            val parsed = Uri.parse(uriString)
            when (parsed.scheme?.lowercase()) {
                "file" -> parsed.path?.let { path ->
                    File(path).takeIf { it.exists() }?.readBytes()
                }
                "content" -> appContext?.contentResolver
                    ?.openInputStream(parsed)
                    ?.use { it.readBytes() }
                null, "" -> File(uriString).takeIf { it.exists() }?.readBytes()
                else -> null
            }
        }.onFailure { error ->
            android.util.Log.w("ChatApiClient", "Failed to read attachment bytes from $uriString", error)
        }.getOrNull()
    }

    /**
     * Parses an error message from API response body.
     */
    private fun parseErrorMessage(errorBody: String, statusCode: Int): String {
        return try {
            // Try to parse as JSON error
            val errorResponse = json.decodeFromString<ErrorWrapper>(errorBody)

            // First, try to extract nested error from metadata.raw (OpenRouter style)
            val nestedMessage = errorResponse.error?.metadata?.raw?.let { rawJson ->
                try {
                    val nestedError = json.decodeFromString<ErrorWrapper>(rawJson)
                    nestedError.error?.message
                } catch (e: Exception) {
                    null
                }
            }

            // Use nested message if available, otherwise use top-level message
            nestedMessage
                ?: errorResponse.error?.message?.takeIf { it != "Provider returned error" }
                ?: errorResponse.error?.message
                ?: errorResponse.message
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
         * Regex matching a version path segment like /v1, /v2, /v3, etc.
         */
        private val VERSION_SUFFIX_REGEX = Regex("/v\\d+$")

        /**
         * Builds the models endpoint URL, handling various base URL formats.
         * Respects existing version prefixes (v1, v2, …) instead of forcing /v1.
         *
         * Examples:
         * - "https://api.openai.com" -> "https://api.openai.com/v1/models"
         * - "https://api.openai.com/v1" -> "https://api.openai.com/v1/models"
         * - "https://openrouter.ai/api/v1" -> "https://openrouter.ai/api/v1/models"
         * - "https://api.example.com/v2" -> "https://api.example.com/v2/models"
         */
        fun buildModelsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')
            return if (VERSION_SUFFIX_REGEX.containsMatchIn(trimmed)) {
                "$trimmed/models"
            } else {
                "$trimmed/v1/models"
            }
        }

        /**
         * Returns a list of models endpoint URLs to try, with fallbacks.
         * If the base URL has a non-v1 version (e.g. /v2), the primary URL
         * uses that version and /v1/models is added as a fallback, since many
         * servers only serve the models list on /v1.
         */
        fun buildModelsUrlsWithFallback(baseUrl: String): List<String> {
            val trimmed = baseUrl.trimEnd('/')
            val match = VERSION_SUFFIX_REGEX.find(trimmed)
            val primary = buildModelsUrl(trimmed)
            return if (match != null && match.value != "/v1") {
                val fallback = "${normalizeBaseUrl(trimmed)}/v1/models"
                listOf(primary, fallback)
            } else {
                listOf(primary)
            }
        }

        /**
         * Normalizes a base URL for chat completions endpoint.
         * Removes any version suffix (/v1, /v2, …) so the caller can append
         * the correct versioned path via [buildChatCompletionsUrl].
         *
         * Examples:
         * - "https://api.openai.com/v1" -> "https://api.openai.com"
         * - "https://openrouter.ai/api/v1" -> "https://openrouter.ai/api"
         * - "https://api.example.com/v2" -> "https://api.example.com"
         * - "https://api.example.com" -> "https://api.example.com"
         */
        fun normalizeBaseUrl(url: String): String {
            return url.trimEnd('/')
                .replace(VERSION_SUFFIX_REGEX, "")
                .trimEnd('/')
        }

        /**
         * Builds the full chat/completions URL, respecting the version in the
         * original base URL. Falls back to /v1 when no version is present.
         *
         * Examples:
         * - "https://api.openai.com"        -> "https://api.openai.com/v1/chat/completions"
         * - "https://api.openai.com/v1"     -> "https://api.openai.com/v1/chat/completions"
         * - "https://api.example.com/v2"    -> "https://api.example.com/v2/chat/completions"
         */
        fun buildChatCompletionsUrl(baseUrl: String, forcedVersion: String? = null): String {
            val trimmed = baseUrl.trimEnd('/')
            val match = VERSION_SUFFIX_REGEX.find(trimmed)
            val version = forcedVersion ?: match?.value?.removePrefix("/") ?: "v1"
            return "${normalizeBaseUrl(trimmed)}/$version/chat/completions"
        }

        /**
         * Builds the full image generation URL, respecting any version suffix.
         *
         * Examples:
         * - "https://api.openai.com"    -> "https://api.openai.com/v1/images/generations"
         * - "https://api.example.com/v1" -> "https://api.example.com/v1/images/generations"
         */
        fun buildImagesGenerationsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')
            val match = VERSION_SUFFIX_REGEX.find(trimmed)
            val version = match?.value?.removePrefix("/") ?: "v1"
            return "${normalizeBaseUrl(trimmed)}/$version/images/generations"
        }

        /**
         * Builds image-generation URLs with a v1 fallback for providers where
         * chat uses /v2 but image generation remains exposed under /v1.
         */
        fun buildImagesGenerationsUrlsWithFallback(baseUrl: String): List<String> {
            val trimmed = baseUrl.trimEnd('/')
            val match = VERSION_SUFFIX_REGEX.find(trimmed)
            val primary = buildImagesGenerationsUrl(trimmed)
            return if (match != null && match.value != "/v1") {
                val fallback = "${normalizeBaseUrl(trimmed)}/v1/images/generations"
                listOf(primary, fallback).distinct()
            } else {
                listOf(primary)
            }
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
    val code: kotlinx.serialization.json.JsonElement? = null,
    val metadata: ErrorMetadata? = null
)

@kotlinx.serialization.Serializable
private data class ErrorMetadata(
    val raw: String? = null,
    @kotlinx.serialization.SerialName("provider_name")
    val providerName: String? = null
)

private fun String.toMimeType(): String = when (lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> "image/png"
}

/**
 * Parsed image data returned by an OpenAI-compatible image endpoint.
 */
data class GeneratedImageData(
    val base64Data: String,
    val mimeType: String,
    val model: String
)
