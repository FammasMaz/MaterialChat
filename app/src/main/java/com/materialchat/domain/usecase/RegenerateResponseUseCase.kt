package com.materialchat.domain.usecase

import android.content.Context
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WebSearchRepository
import com.materialchat.notifications.ImageGenerationForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Use case for regenerating the last AI response in a conversation.
 *
 * This use case orchestrates:
 * 1. Deleting the last assistant message
 * 2. Creating a new placeholder assistant message
 * 3. Re-sending the conversation to the AI provider
 * 4. Updating the assistant message with streaming content
 */
class RegenerateResponseUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val personaRepository: PersonaRepository,
    private val webSearchRepository: WebSearchRepository,
    @ApplicationContext private val context: Context
) {
    /**
     * Regenerates the last response in a conversation.
     *
     * @param conversationId The ID of the conversation to regenerate the response for
     * @param systemPrompt The system prompt to use for the conversation
     * @param reasoningEffort The reasoning effort setting for compatible models
     * @return A Flow of StreamingState representing the response progress
     */
    operator fun invoke(
        conversationId: String,
        systemPrompt: String,
        reasoningEffort: ReasoningEffort,
        overrideModelName: String? = null,
        webSearchConfig: WebSearchConfig = WebSearchConfig()
    ): Flow<StreamingState> = flow {
        // Get the conversation and provider
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        val provider = providerRepository.getProvider(conversation.providerId)
            ?: throw IllegalStateException("Provider not found: ${conversation.providerId}")

        // Resolve the effective system prompt: persona overrides global
        val effectiveSystemPrompt = if (conversation.personaId != null) {
            val persona = personaRepository.getPersonaById(conversation.personaId)
            persona?.systemPrompt ?: systemPrompt
        } else {
            systemPrompt
        }

        // Get current messages
        val messages = conversationRepository.getMessages(conversationId)
        if (messages.isEmpty()) {
            emit(StreamingState.Error(IllegalStateException("No messages to regenerate")))
            return@flow
        }

        // Find and remove the last assistant message (skip if last is USER, e.g. auto-send after redo)
        val lastMessage = messages.last()
        if (lastMessage.role == MessageRole.ASSISTANT) {
            conversationRepository.deleteMessage(lastMessage.id)
        }

        // Get updated messages without the last assistant message
        val updatedMessages = conversationRepository.getMessages(conversationId)

        val webSearchContext = resolveWebSearchPromptContext(
            basePrompt = effectiveSystemPrompt,
            messages = updatedMessages,
            webSearchConfig = webSearchConfig,
            webSearchRepository = webSearchRepository
        )

        // Determine model to use (override for redo-with-model, otherwise conversation default)
        val modelToUse = overrideModelName ?: conversation.modelName

        // Create a new placeholder assistant message for streaming
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelName = modelToUse
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)

        // Emit starting state
        emit(StreamingState.Starting)

        var hasError = false
        val streamStartTime = System.currentTimeMillis()
        var thinkingEndTime: Long? = null
        val contentUpdater = StreamingMessageContentUpdater(
            conversationRepository = conversationRepository,
            messageId = assistantMessageId
        )

        ImageGenerationForegroundService.startChat(context, modelToUse)

        // Stream the response from the chat repository
        chatRepository.sendMessage(
            provider = provider,
            messages = updatedMessages,
            model = modelToUse,
            reasoningEffort = reasoningEffort,
            systemPrompt = webSearchContext.systemPrompt,
            disableTools = webSearchContext.metadata != null
        ).onEach { state ->
            when (state) {
                is StreamingState.Streaming -> {
                    // Coalesce Room writes so each token does not invalidate the whole chat list.
                    contentUpdater.onStreaming(state.content, state.thinkingContent)
                    // Track when thinking ends
                    if (thinkingEndTime == null && state.content.isNotEmpty() && !state.thinkingContent.isNullOrEmpty()) {
                        thinkingEndTime = System.currentTimeMillis()
                        // Persist thinking duration immediately so UI shows "Thought for Xs" on collapse
                        val thinkingDurationMs = thinkingEndTime!! - streamStartTime
                        conversationRepository.updateMessageDurations(assistantMessageId, thinkingDurationMs, null)
                    }
                }
                is StreamingState.Error -> {
                    hasError = true
                    // Save partial content if available
                    state.partialContent?.let { content ->
                        contentUpdater.persistPartial(content)
                    } ?: contentUpdater.flush()
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                }
                is StreamingState.Cancelled -> {
                    // Save partial content if available
                    state.partialContent?.let { content ->
                        contentUpdater.persistPartial(content)
                    } ?: contentUpdater.flush()
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                }
                is StreamingState.Completed -> {
                    contentUpdater.persistFinal(state.finalContent, state.finalThinkingContent)
                    conversationRepository.setMessageStreaming(assistantMessageId, false)

                    // Save duration data
                    val totalDurationMs = System.currentTimeMillis() - streamStartTime
                    val thinkingDurationMs = if (!state.finalThinkingContent.isNullOrEmpty()) {
                        (thinkingEndTime ?: System.currentTimeMillis()) - streamStartTime
                    } else null
                    conversationRepository.updateMessageDurations(assistantMessageId, thinkingDurationMs, totalDurationMs)

                    webSearchContext.metadata?.let { meta ->
                        conversationRepository.updateMessageWebSearchMetadata(
                            assistantMessageId,
                            Json.encodeToString(meta)
                        )
                    }
                }
                else -> { /* Ignore other states */ }
            }
        }.onCompletion { cause ->
            // Always mark streaming as complete, regardless of success, error, or cancellation.
            // This prevents messages from getting stuck with isStreaming=true forever
            // (e.g. when the coroutine is cancelled by navigation or user action).
            withContext(NonCancellable) {
                try {
                    contentUpdater.flush()
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                    ImageGenerationForegroundService.stop(context)
                } catch (_: Exception) { }
            }
        }.collect { state ->
            // Re-emit with the correct message ID for the UI
            val mappedState = when (state) {
                is StreamingState.Streaming -> StreamingState.Streaming(
                    content = state.content,
                    thinkingContent = state.thinkingContent,
                    messageId = assistantMessageId
                )
                is StreamingState.Completed -> StreamingState.Completed(
                    finalContent = state.finalContent,
                    finalThinkingContent = state.finalThinkingContent,
                    messageId = assistantMessageId
                )
                is StreamingState.Error -> StreamingState.Error(
                    error = state.error,
                    partialContent = state.partialContent,
                    messageId = assistantMessageId
                )
                is StreamingState.Cancelled -> StreamingState.Cancelled(
                    partialContent = state.partialContent,
                    messageId = assistantMessageId
                )
                else -> state
            }
            emit(mappedState)
        }
    }

    /**
     * Cancels any ongoing streaming request.
     */
    fun cancel() {
        chatRepository.cancelStreaming()
    }
}
