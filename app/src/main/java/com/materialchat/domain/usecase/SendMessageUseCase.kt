package com.materialchat.domain.usecase

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
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
 * Use case for sending a message in a conversation and receiving a streaming response.
 *
 * This use case orchestrates:
 * 1. Adding the user message to the database
 * 2. Creating a placeholder assistant message
 * 3. Sending the message to the AI provider
 * 4. Updating the assistant message with streaming content
 * 5. Finalizing the message when streaming completes
 */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository
) {
    /**
     * Sends a message and returns a flow of streaming states.
     *
     * @param conversationId The ID of the conversation to send the message in
     * @param userContent The content of the user's message
     * @param systemPrompt The system prompt to use for the conversation
     * @return A Flow of StreamingState representing the response progress
     */
    operator fun invoke(
        conversationId: String,
        userContent: String,
        systemPrompt: String
    ): Flow<StreamingState> = flow {
        // Get the conversation and provider
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        val provider = providerRepository.getProvider(conversation.providerId)
            ?: throw IllegalStateException("Provider not found: ${conversation.providerId}")

        // Create and save the user message
        val userMessage = Message(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userContent
        )
        conversationRepository.addMessage(userMessage)

        // Create a placeholder assistant message for streaming
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)

        // Get all messages for the conversation to send to the AI
        val messages = conversationRepository.getMessages(conversationId)

        // Emit starting state
        emit(StreamingState.Starting)

        var accumulatedContent = ""
        var hasError = false

        // Stream the response from the chat repository
        chatRepository.sendMessage(
            provider = provider,
            messages = messages,
            model = conversation.modelName,
            systemPrompt = systemPrompt
        ).onEach { state ->
            when (state) {
                is StreamingState.Streaming -> {
                    accumulatedContent = state.content
                    // Update the message content in the database
                    conversationRepository.updateMessageContent(assistantMessageId, accumulatedContent)
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

        // Update conversation title if this is the first message
        updateConversationTitleIfNeeded(conversation, userContent)
    }

    /**
     * Updates the conversation title based on the first user message.
     */
    private suspend fun updateConversationTitleIfNeeded(
        conversation: Conversation,
        userContent: String
    ) {
        if (conversation.title == Conversation.generateDefaultTitle()) {
            val newTitle = generateTitleFromMessage(userContent)
            conversationRepository.updateConversationTitle(conversation.id, newTitle)
        }
    }

    /**
     * Generates a title from the user's first message.
     * Truncates long messages and removes special characters.
     */
    private fun generateTitleFromMessage(content: String): String {
        val maxLength = 40
        val cleaned = content
            .replace("\n", " ")
            .replace(Regex("[\\r\\t]"), "")
            .trim()

        return if (cleaned.length <= maxLength) {
            cleaned
        } else {
            cleaned.take(maxLength - 3) + "..."
        }
    }

    /**
     * Cancels any ongoing streaming request.
     */
    fun cancel() {
        chatRepository.cancelStreaming()
    }
}
