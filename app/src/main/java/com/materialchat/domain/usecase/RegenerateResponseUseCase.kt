package com.materialchat.domain.usecase

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
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
    private val providerRepository: ProviderRepository
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
        reasoningEffort: ReasoningEffort
    ): Flow<StreamingState> = flow {
        // Get the conversation and provider
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        val provider = providerRepository.getProvider(conversation.providerId)
            ?: throw IllegalStateException("Provider not found: ${conversation.providerId}")

        // Get current messages
        val messages = conversationRepository.getMessages(conversationId)
        if (messages.isEmpty()) {
            emit(StreamingState.Error(IllegalStateException("No messages to regenerate")))
            return@flow
        }

        // Find and remove the last assistant message
        val lastMessage = messages.last()
        if (lastMessage.role == MessageRole.ASSISTANT) {
            conversationRepository.deleteMessage(lastMessage.id)
        }

        // Get updated messages without the last assistant message
        val updatedMessages = conversationRepository.getMessages(conversationId)

        // Create a new placeholder assistant message for streaming
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)

        // Emit starting state
        emit(StreamingState.Starting)

        var hasError = false
        var accumulatedThinking: String? = null
        val streamStartTime = System.currentTimeMillis()
        var thinkingEndTime: Long? = null

        // Stream the response from the chat repository
        chatRepository.sendMessage(
            provider = provider,
            messages = updatedMessages,
            model = conversation.modelName,
            reasoningEffort = reasoningEffort,
            systemPrompt = systemPrompt
        ).onEach { state ->
            when (state) {
                is StreamingState.Streaming -> {
                    // Update the message content in the database
                    if (state.thinkingContent != null) {
                        accumulatedThinking = state.thinkingContent
                        conversationRepository.updateMessageContentWithThinking(
                            assistantMessageId, state.content, state.thinkingContent
                        )
                    } else {
                        conversationRepository.updateMessageContent(assistantMessageId, state.content)
                    }
                    // Track when thinking ends
                    if (thinkingEndTime == null && state.content.isNotEmpty() && !state.thinkingContent.isNullOrEmpty()) {
                        thinkingEndTime = System.currentTimeMillis()
                    }
                }
                is StreamingState.Error -> {
                    hasError = true
                    // Save partial content if available
                    state.partialContent?.let { content ->
                        conversationRepository.updateMessageContent(assistantMessageId, content)
                    }
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                }
                is StreamingState.Cancelled -> {
                    // Save partial content if available
                    state.partialContent?.let { content ->
                        conversationRepository.updateMessageContent(assistantMessageId, content)
                    }
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                }
                is StreamingState.Completed -> {
                    if (state.finalThinkingContent != null) {
                        conversationRepository.updateMessageContentWithThinking(
                            assistantMessageId, state.finalContent, state.finalThinkingContent
                        )
                    } else {
                        conversationRepository.updateMessageContent(assistantMessageId, state.finalContent)
                    }
                    conversationRepository.setMessageStreaming(assistantMessageId, false)

                    // Save duration data
                    val totalDurationMs = System.currentTimeMillis() - streamStartTime
                    val thinkingDurationMs = if (!state.finalThinkingContent.isNullOrEmpty()) {
                        (thinkingEndTime ?: System.currentTimeMillis()) - streamStartTime
                    } else null
                    conversationRepository.updateMessageDurations(assistantMessageId, thinkingDurationMs, totalDurationMs)
                }
                else -> { /* Ignore other states */ }
            }
        }.onCompletion { cause ->
            if (cause == null && !hasError) {
                // Mark streaming as complete
                conversationRepository.setMessageStreaming(assistantMessageId, false)
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
