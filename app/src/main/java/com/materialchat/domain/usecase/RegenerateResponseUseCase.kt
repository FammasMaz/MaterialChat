package com.materialchat.domain.usecase

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
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
     * @return A Flow of StreamingState representing the response progress
     */
    operator fun invoke(
        conversationId: String,
        systemPrompt: String
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

        // Stream the response from the chat repository
        chatRepository.sendMessage(
            provider = provider,
            messages = updatedMessages,
            model = conversation.modelName,
            systemPrompt = systemPrompt
        ).onEach { state ->
            when (state) {
                is StreamingState.Streaming -> {
                    // Update the message content in the database
                    conversationRepository.updateMessageContent(assistantMessageId, state.content)
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
                    conversationRepository.updateMessageContent(assistantMessageId, state.finalContent)
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
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
                    messageId = assistantMessageId
                )
                is StreamingState.Completed -> StreamingState.Completed(
                    finalContent = state.finalContent,
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
