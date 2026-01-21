package com.materialchat.data.repository

import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.remote.api.ChatApiClient
import com.materialchat.data.remote.api.ModelListApiClient
import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

    // Track the current streaming message for state management
    private var currentMessageId: String? = null
    private var accumulatedContent = StringBuilder()
    private var accumulatedThinking = StringBuilder()

    override fun sendMessage(
        provider: Provider,
        messages: List<Message>,
        model: String,
        systemPrompt: String?
    ): Flow<StreamingState> = flow {
        // Reset state for new message
        currentMessageId = null
        accumulatedContent.clear()
        accumulatedThinking.clear()

        // Get API key if required
        val apiKey = if (provider.requiresApiKey) {
            encryptedPreferences.getApiKey(provider.id)
        } else {
            null
        }

        // Create a placeholder message ID for tracking
        val messageId = java.util.UUID.randomUUID().toString()
        currentMessageId = messageId

        // Emit starting state
        emit(StreamingState.Starting)

        // Collect streaming events from the API client
        chatApiClient.streamChat(
            provider = provider,
            messages = messages,
            model = model,
            apiKey = apiKey,
            systemPrompt = systemPrompt
        ).collect { event ->
            when (event) {
                is StreamingEvent.Connected -> {
                    // Already emitted Starting state
                }

                is StreamingEvent.Content -> {
                    accumulatedContent.append(event.content)
                    event.thinking?.let { accumulatedThinking.append(it) }
                    emit(StreamingState.Streaming(
                        content = accumulatedContent.toString(),
                        thinkingContent = accumulatedThinking.toString().takeIf { it.isNotEmpty() },
                        messageId = messageId
                    ))
                }

                is StreamingEvent.Done -> {
                    emit(StreamingState.Completed(
                        finalContent = accumulatedContent.toString(),
                        finalThinkingContent = accumulatedThinking.toString().takeIf { it.isNotEmpty() },
                        messageId = messageId
                    ))
                }

                is StreamingEvent.Error -> {
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
                emit(StreamingState.Cancelled(
                    partialContent = accumulatedContent.toString().takeIf { it.isNotEmpty() },
                    messageId = currentMessageId
                ))
            }
            else -> {
                emit(StreamingState.Error(
                    error = exception,
                    partialContent = accumulatedContent.toString().takeIf { it.isNotEmpty() },
                    messageId = currentMessageId
                ))
            }
        }
    }

    override suspend fun fetchModels(provider: Provider): Result<List<AiModel>> {
        // Get API key if required
        val apiKey = if (provider.requiresApiKey) {
            encryptedPreferences.getApiKey(provider.id)
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
}
