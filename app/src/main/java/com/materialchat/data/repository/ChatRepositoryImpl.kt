package com.materialchat.data.repository

import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.remote.api.ChatApiClient
import com.materialchat.data.remote.api.ModelListApiClient
import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ChatRepository] that handles communication with AI providers.
 *
 * Uses [ChatApiClient] for streaming chat completions and [ModelListApiClient] for
 * fetching available models. API keys are retrieved from [EncryptedPreferences].
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApiClient: ChatApiClient,
    private val modelListApiClient: ModelListApiClient,
    private val encryptedPreferences: EncryptedPreferences
) : ChatRepository {

    override fun sendMessage(
        provider: Provider,
        messages: List<Message>,
        model: String,
        reasoningEffort: ReasoningEffort,
        systemPrompt: String?,
        disableTools: Boolean
    ): Flow<StreamingState> = flow {
        // Use local state so parallel calls (e.g. arena) don't interfere
        val accumulatedContent = StringBuilder()
        val accumulatedThinking = StringBuilder()

        // Get API key if required
        val apiKey = if (provider.requiresApiKey) {
            encryptedPreferences.getApiKey(provider.id)
        } else {
            null
        }

        // Create a placeholder message ID for tracking
        val messageId = java.util.UUID.randomUUID().toString()

        // Emit starting state
        emit(StreamingState.Starting)

        // Collect streaming events from the API client
        chatApiClient.streamChat(
            provider = provider,
            messages = messages,
            model = model,
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            reasoningEffort = reasoningEffort,
            disableTools = disableTools
        ).collect { event ->
            android.util.Log.d("ChatRepository", "Received event: $event")
            when (event) {
                is StreamingEvent.Connected -> {
                    // Already emitted Starting state
                }

                is StreamingEvent.Content -> {
                    accumulatedContent.append(event.content)
                    event.thinking?.let { accumulatedThinking.append(it) }
                    android.util.Log.d("ChatRepository", "Accumulated content: ${accumulatedContent.length} chars")
                    emit(StreamingState.Streaming(
                        content = accumulatedContent.toString(),
                        thinkingContent = accumulatedThinking.toString().takeIf { it.isNotEmpty() },
                        messageId = messageId
                    ))
                }

                is StreamingEvent.Done -> {
                    android.util.Log.d("ChatRepository", "Stream done, final content: ${accumulatedContent.length} chars")
                    emit(StreamingState.Completed(
                        finalContent = accumulatedContent.toString(),
                        finalThinkingContent = accumulatedThinking.toString().takeIf { it.isNotEmpty() },
                        messageId = messageId
                    ))
                }

                is StreamingEvent.Error -> {
                    android.util.Log.e("ChatRepository", "Stream error: ${event.message}")
                    emit(StreamingState.Error(
                        error = Exception(event.message),
                        partialContent = accumulatedContent.toString().takeIf { it.isNotEmpty() },
                        messageId = messageId
                    ))
                }

                is StreamingEvent.KeepAlive -> {
                    // Ignore keep-alive events
                }
            }
        }
    }.catch { exception ->
        when (exception) {
            is CancellationException -> {
                emit(StreamingState.Cancelled())
            }
            else -> {
                emit(StreamingState.Error(
                    error = exception
                ))
            }
        }
    }

    override suspend fun fetchModels(
        provider: Provider,
        apiKeyOverride: String?
    ): Result<List<AiModel>> {
        // Get API key if required
        val apiKey = if (provider.requiresApiKey) {
            apiKeyOverride ?: encryptedPreferences.getApiKey(provider.id)
        } else {
            null
        }

        return modelListApiClient.fetchModels(provider, apiKey)
    }

    override fun cancelStreaming() {
        chatApiClient.cancelStreaming()
    }

    override suspend fun testConnection(provider: Provider): Result<Boolean> {
        // Get API key if required
        val apiKey = if (provider.requiresApiKey) {
            encryptedPreferences.getApiKey(provider.id)
        } else {
            null
        }

        return chatApiClient.testConnection(provider, apiKey)
    }

    override suspend fun generateSimpleCompletion(
        provider: Provider,
        prompt: String,
        model: String,
        systemPrompt: String?
    ): Result<String> {
        // Get API key if required
        val apiKey = if (provider.requiresApiKey) {
            encryptedPreferences.getApiKey(provider.id)
        } else {
            null
        }

        return chatApiClient.generateSimpleCompletion(provider, prompt, model, apiKey, systemPrompt)
    }

    override suspend fun generateImage(
        provider: Provider,
        prompt: String,
        model: String
    ): Result<Attachment> {
        val apiKey = if (provider.requiresApiKey) {
            encryptedPreferences.getApiKey(provider.id)
        } else {
            null
        }

        return chatApiClient.generateImage(provider, prompt, model, apiKey)
            .map { image ->
                Attachment(
                    uri = "data:${image.mimeType};base64,${image.base64Data}",
                    mimeType = image.mimeType,
                    base64Data = image.base64Data
                )
            }
    }
}
