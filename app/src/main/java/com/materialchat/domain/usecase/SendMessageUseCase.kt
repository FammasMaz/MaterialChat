package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
 * 6. Generating AI-powered conversation title (if enabled)
 */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences,
    private val generateConversationTitleUseCase: GenerateConversationTitleUseCase
) {
    // Coroutine scope for non-blocking title generation
    private val titleGenerationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        var accumulatedThinking: String? = null
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
                    accumulatedThinking = state.thinkingContent
                    // Update the message content in the database (with thinking if available)
                    if (accumulatedThinking != null) {
                        conversationRepository.updateMessageContentWithThinking(
                            assistantMessageId,
                            accumulatedContent,
                            accumulatedThinking
                        )
                    } else {
                        conversationRepository.updateMessageContent(assistantMessageId, accumulatedContent)
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
                            assistantMessageId,
                            state.finalContent,
                            state.finalThinkingContent
                        )
                    } else {
                        conversationRepository.updateMessageContent(assistantMessageId, state.finalContent)
                    }
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

        // Update conversation title if this is the first message
        updateConversationTitleIfNeeded(conversation, userContent, accumulatedContent)
    }

    /**
     * Updates the conversation title based on the first message exchange.
     * Uses AI-generated title if enabled, otherwise falls back to truncation.
     */
    private suspend fun updateConversationTitleIfNeeded(
        conversation: Conversation,
        userContent: String,
        assistantResponse: String
    ) {
        if (conversation.title == Conversation.generateDefaultTitle()) {
            // Check if AI-generated titles are enabled
            val aiTitlesEnabled = appPreferences.aiGeneratedTitlesEnabled.first()
            
            if (aiTitlesEnabled && assistantResponse.isNotBlank()) {
                // Launch non-blocking AI title generation
                titleGenerationScope.launch {
                    generateConversationTitleUseCase(
                        conversationId = conversation.id,
                        userMessage = userContent,
                        assistantResponse = assistantResponse
                    )
                }
            } else {
                // Fall back to simple truncation
                val newTitle = generateTitleFromMessage(userContent)
                conversationRepository.updateConversationTitle(conversation.id, newTitle)
            }
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
