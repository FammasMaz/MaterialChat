package com.materialchat.data.remote.api

import com.materialchat.data.auth.AntigravityOAuth
import com.materialchat.data.auth.OAuthManager
import com.materialchat.data.remote.dto.AntigravityContent
import com.materialchat.data.remote.dto.AntigravityGenerationConfig
import com.materialchat.data.remote.dto.AntigravityInlineData
import com.materialchat.data.remote.dto.AntigravityPart
import com.materialchat.data.remote.dto.AntigravityRequest
import com.materialchat.data.remote.dto.AntigravityResponse
import com.materialchat.data.remote.dto.AntigravitySafetySetting
import com.materialchat.data.remote.dto.AntigravitySystemInstruction
import com.materialchat.data.remote.sse.SseEventParser
import com.materialchat.domain.model.AntigravityConfig
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API client for Antigravity (Google DeepMind's agentic AI service).
 *
 * Antigravity uses a Gemini-style API format, not OpenAI-compatible format.
 * Key differences:
 * - Uses `contents` array instead of `messages`
 * - Roles are `user`/`model` instead of `user`/`assistant`
 * - System prompt in `systemInstruction`, not in messages
 * - SSE streaming with `candidates` containing `content.parts`
 *
 * Requires OAuth authentication (handled by OAuthManager).
 */
@Singleton
class AntigravityApiClient @Inject constructor(
    private val oauthManager: OAuthManager,
    private val antigravityOAuth: AntigravityOAuth,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val sseEventParser: SseEventParser
) {
    private val activeCall = AtomicReference<Call?>(null)
    private val isCancelled = AtomicBoolean(false)

    /**
     * Cached project info to avoid re-resolving on every request.
     */
    @Volatile
    private var cachedProjectInfo: AntigravityOAuth.ProjectInfo? = null

    /**
     * Streams a chat completion from Antigravity.
     *
     * @param providerId The provider ID for OAuth token lookup
     * @param messages The conversation history
     * @param model The model to use (e.g., "antigravity-claude-opus-4-5-thinking")
     * @param systemPrompt Optional system prompt
     * @param temperature Sampling temperature (0.0-2.0)
     * @param reasoningEffort Thinking budget configuration
     * @return Flow of StreamingEvents
     */
    fun streamChat(
        providerId: String,
        messages: List<Message>,
        model: String,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH
    ): Flow<StreamingEvent> = callbackFlow {
        isCancelled.set(false)

        try {
            // Get valid access token
            val accessToken = oauthManager.getValidAccessToken(providerId)
            if (accessToken == null) {
                trySend(StreamingEvent.Error(
                    message = "Not authenticated with Antigravity. Please sign in.",
                    code = "AUTH_REQUIRED",
                    isRecoverable = true
                ))
                close()
                return@callbackFlow
            }

            // Get or resolve project info
            val projectInfo = cachedProjectInfo ?: run {
                val resolved = antigravityOAuth.resolveProjectInfo(accessToken)
                cachedProjectInfo = resolved
                resolved
            }

            // Build the request
            val request = buildAntigravityRequest(
                messages = messages,
                systemPrompt = systemPrompt,
                temperature = temperature,
                reasoningEffort = reasoningEffort
            )

            val requestBody = json.encodeToString(request)
                .toRequestBody(JSON_MEDIA_TYPE)

            // Build streaming URL
            val url = antigravityOAuth.buildStreamingChatUrl(
                endpoint = projectInfo.endpoint,
                projectId = projectInfo.projectId,
                modelId = mapModelId(model)
            )

            android.util.Log.d(TAG, "Antigravity streaming URL: $url")
            android.util.Log.d(TAG, "Antigravity request: ${json.encodeToString(request)}")

            // Build HTTP request with OAuth headers
            val headers = antigravityOAuth.buildRequestHeaders(accessToken, projectInfo.projectId)
            val httpRequestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            headers.forEach { (key, value) ->
                httpRequestBuilder.addHeader(key, value)
            }
            httpRequestBuilder.addHeader("Content-Type", "application/json")
            httpRequestBuilder.addHeader("Accept", "text/event-stream")

            val httpRequest = httpRequestBuilder.build()
            val call = streamingClient.newCall(httpRequest)
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
                        android.util.Log.d(TAG, "Antigravity response code: ${resp.code}")

                        if (!resp.isSuccessful) {
                            val errorBody = resp.body?.string() ?: "Unknown error"
                            android.util.Log.e(TAG, "Antigravity error: $errorBody")

                            // Check if auth error - clear cached project info
                            if (resp.code == 401 || resp.code == 403) {
                                cachedProjectInfo = null
                            }

                            trySend(StreamingEvent.fromHttpError(resp.code, errorBody))
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
                                processAntigravityStream(reader) { event ->
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
        } catch (e: Exception) {
            trySend(StreamingEvent.fromException(e))
            close()
        }

        awaitClose {
            cancelStreaming()
        }
    }

    /**
     * Generates a non-streaming completion from Antigravity.
     *
     * @param providerId The provider ID for OAuth token lookup
     * @param prompt The prompt to send
     * @param model The model to use
     * @return Result containing the generated text or an error
     */
    suspend fun generateSimpleCompletion(
        providerId: String,
        prompt: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get valid access token
            val accessToken = oauthManager.getValidAccessToken(providerId)
                ?: return@withContext Result.failure(IOException("Not authenticated with Antigravity"))

            // Get or resolve project info
            val projectInfo = cachedProjectInfo ?: run {
                val resolved = antigravityOAuth.resolveProjectInfo(accessToken)
                cachedProjectInfo = resolved
                resolved
            }

            // Build simple request
            val request = AntigravityRequest(
                contents = listOf(
                    AntigravityContent(
                        role = AntigravityContent.ROLE_USER,
                        parts = listOf(AntigravityPart(text = prompt))
                    )
                ),
                generationConfig = AntigravityGenerationConfig(
                    temperature = 0.7f,
                    maxOutputTokens = 1024
                )
            )

            val requestBody = json.encodeToString(request)
                .toRequestBody(JSON_MEDIA_TYPE)

            // Build non-streaming URL
            val url = antigravityOAuth.buildChatUrl(
                endpoint = projectInfo.endpoint,
                projectId = projectInfo.projectId,
                modelId = mapModelId(model)
            )

            val headers = antigravityOAuth.buildRequestHeaders(accessToken, projectInfo.projectId)
            val httpRequestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            headers.forEach { (key, value) ->
                httpRequestBuilder.addHeader(key, value)
            }
            httpRequestBuilder.addHeader("Content-Type", "application/json")

            val httpRequest = httpRequestBuilder.build()
            val response = okHttpClient.newCall(httpRequest).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(IOException("API error ${resp.code}: $errorBody"))
                }

                val body = resp.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response"))

                val antigravityResponse = json.decodeFromString<AntigravityResponse>(body)
                val content = antigravityResponse.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.thought != true }?.text

                if (content.isNullOrEmpty()) {
                    return@withContext Result.failure(IOException("No content in response"))
                }

                Result.success(content.trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
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
     * Clears cached project info (useful after logout).
     */
    fun clearCache() {
        cachedProjectInfo = null
    }

    // ============================================================================
    // Private Helper Methods
    // ============================================================================

    /**
     * Builds an Antigravity request from domain messages.
     */
    private fun buildAntigravityRequest(
        messages: List<Message>,
        systemPrompt: String?,
        temperature: Double,
        reasoningEffort: ReasoningEffort
    ): AntigravityRequest {
        val contents = mutableListOf<AntigravityContent>()

        // Convert messages to Antigravity format (skip system messages)
        messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
            val parts = mutableListOf<AntigravityPart>()

            // Add text content
            if (message.content.isNotBlank()) {
                parts.add(AntigravityPart(text = message.content))
            }

            // Add image attachments as inline data
            message.attachments.forEach { attachment ->
                parts.add(AntigravityPart(
                    inlineData = AntigravityInlineData(
                        mimeType = attachment.mimeType,
                        data = attachment.base64Data
                    )
                ))
            }

            if (parts.isNotEmpty()) {
                contents.add(AntigravityContent(
                    role = when (message.role) {
                        MessageRole.USER -> AntigravityContent.ROLE_USER
                        MessageRole.ASSISTANT -> AntigravityContent.ROLE_MODEL
                        MessageRole.SYSTEM -> AntigravityContent.ROLE_USER // Shouldn't happen
                    },
                    parts = parts
                ))
            }
        }

        // Build system instruction (combine Antigravity system prompt with user's)
        val combinedSystemPrompt = buildString {
            append(AntigravityConfig.SYSTEM_INSTRUCTION)
            if (!systemPrompt.isNullOrBlank()) {
                append("\n\n")
                append(systemPrompt)
            }
        }

        // Build thinking config based on reasoning effort
        val thinkingConfig = if (reasoningEffort.enablesThinking) {
            com.materialchat.data.remote.dto.AntigravityThinkingConfig(
                thinkingBudget = when (reasoningEffort) {
                    ReasoningEffort.LOW -> 8192
                    ReasoningEffort.MEDIUM -> 16384
                    ReasoningEffort.HIGH -> 32768
                    else -> null
                }
            )
        } else null

        return AntigravityRequest(
            contents = contents,
            systemInstruction = AntigravitySystemInstruction.fromText(combinedSystemPrompt),
            generationConfig = AntigravityGenerationConfig(
                temperature = temperature.toFloat(),
                maxOutputTokens = 64000,
                thinkingConfig = thinkingConfig
            ),
            safetySettings = AntigravitySafetySetting.PERMISSIVE_DEFAULTS
        )
    }

    /**
     * Maps MaterialChat model IDs to Antigravity API model names.
     */
    private fun mapModelId(model: String): String {
        return when {
            model.contains("claude-opus-4-5-thinking", ignoreCase = true) ->
                "claude-opus-4-5-20251101"
            model.contains("claude-sonnet-4-5-thinking", ignoreCase = true) ->
                "claude-sonnet-4-5-20251022"
            model.contains("claude-sonnet-4-5", ignoreCase = true) ->
                "claude-sonnet-4-5-20251022"
            model.contains("gemini-3-pro", ignoreCase = true) ->
                "gemini-3.0-pro"
            model.contains("gemini-3-flash", ignoreCase = true) ->
                "gemini-3.0-flash"
            else -> model // Pass through for custom model IDs
        }
    }

    /**
     * Processes an Antigravity SSE stream line by line.
     */
    private fun processAntigravityStream(
        reader: BufferedReader,
        onEvent: (StreamingEvent) -> Unit
    ) {
        var hasContent = false

        reader.lineSequence().forEach { line ->
            if (isCancelled.get()) return

            android.util.Log.d(TAG, "Antigravity SSE line: $line")
            val event = sseEventParser.parseAntigravityEvent(line)
            android.util.Log.d(TAG, "Parsed event: $event")

            when (event) {
                is StreamingEvent.Done -> {
                    onEvent(event)
                    return
                }
                is StreamingEvent.Error -> {
                    android.util.Log.e(TAG, "Stream error: ${event.message}")
                    onEvent(event)
                    return
                }
                is StreamingEvent.Content -> {
                    hasContent = true
                    android.util.Log.d(TAG, "Content chunk: '${event.content}'")
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

        // If stream ended naturally without explicit Done, emit Done
        if (hasContent && !isCancelled.get()) {
            android.util.Log.d(TAG, "Stream ended, emitting implicit Done")
            onEvent(StreamingEvent.Done())
        }
    }

    companion object {
        private const val TAG = "AntigravityApiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Extended timeout for streaming (Antigravity thinking can take a while).
         */
        private const val STREAMING_TIMEOUT_SECONDS = 300L

        /**
         * OkHttpClient configured for streaming with extended timeouts.
         */
        private val streamingClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(STREAMING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
