package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.di.ApplicationScope
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.CoroutineScope
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
 * 1. Adding the user message to the database (with optional image attachments)
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
    private val generateConversationTitleUseCase: GenerateConversationTitleUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    /**
     * Sends a message and returns a flow of streaming states.
     *
     * @param conversationId The ID of the conversation to send the message in
     * @param userContent The content of the user's message
     * @param attachments Optional list of image attachments to include with the message
     * @param systemPrompt The system prompt to use for the conversation
     * @param reasoningEffort The reasoning effort setting for compatible models
     * @return A Flow of StreamingState representing the response progress
     */
    operator fun invoke(
        conversationId: String,
        userContent: String,
        attachments: List<Attachment> = emptyList(),
        systemPrompt: String,
        reasoningEffort: ReasoningEffort
    ): Flow<StreamingState> = flow {
        // Get the conversation and provider
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        val provider = providerRepository.getProvider(conversation.providerId)
            ?: throw IllegalStateException("Provider not found: ${conversation.providerId}")

        // Create and save the user message (with attachments if any)
        val userMessage = Message(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userContent,
            attachments = attachments
        )
        conversationRepository.addMessage(userMessage)

        // Get all messages for the conversation to send to the AI
        // IMPORTANT: Get messages BEFORE adding the placeholder to avoid sending empty assistant message
        val messages = conversationRepository.getMessages(conversationId)

        // Create a placeholder assistant message for streaming (after getting messages for API)
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)

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
            reasoningEffort = reasoningEffort,
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
                    // Capture final content for title generation
                    accumulatedContent = state.finalContent
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
     * For branches, uses a specialized prompt that focuses on what makes the branch different.
     */
    private suspend fun updateConversationTitleIfNeeded(
        conversation: Conversation,
        userContent: String,
        assistantResponse: String
    ) {
        // For new conversations: title is "New Chat"
        // For branches: title is "New Branch"
        val needsTitle = conversation.title == Conversation.generateDefaultTitle() ||
                         conversation.title == Conversation.generateDefaultBranchTitle()

        if (needsTitle) {
            // Check if AI-generated titles are enabled
            val aiTitlesEnabled = appPreferences.aiGeneratedTitlesEnabled.first()

            if (aiTitlesEnabled && assistantResponse.isNotBlank()) {
                // Launch non-blocking AI title generation in application scope
                applicationScope.launch {
                    if (conversation.isBranch) {
                        // For branches, use specialized title generation
                        generateBranchTitle(
                            conversationId = conversation.id,
                            userMessage = userContent,
                            assistantResponse = assistantResponse
                        )
                    } else {
                        generateConversationTitleUseCase(
                            conversationId = conversation.id,
                            userMessage = userContent,
                            assistantResponse = assistantResponse
                        )
                    }
                }
            } else {
                // Fall back to simple truncation
                val newTitle = generateTitleFromMessage(userContent)
                conversationRepository.updateConversationTitle(conversation.id, newTitle)
            }
        }
    }

    /**
     * Generates a specialized title for branch conversations.
     * The title focuses on what makes this branch different from the original.
     */
    private suspend fun generateBranchTitle(
        conversationId: String,
        userMessage: String,
        assistantResponse: String
    ) {
        try {
            val conversation = conversationRepository.getConversation(conversationId) ?: return

            val provider = providerRepository.getProvider(conversation.providerId) ?: return

            // Check if a custom model is configured for title generation
            val customModel = appPreferences.titleGenerationModel.first()
            val modelToUse = if (customModel.isNotBlank()) customModel else conversation.modelName

            val prompt = buildBranchTitlePrompt(userMessage, assistantResponse)

            val result = chatRepository.generateSimpleCompletion(
                provider = provider,
                prompt = prompt,
                model = modelToUse
            )

            result.onSuccess { generatedResponse ->
                val parsed = parseBranchTitleResponse(generatedResponse)
                conversationRepository.updateConversationTitleAndIcon(
                    conversationId,
                    parsed.title,
                    parsed.icon
                )
            }.onFailure {
                // Fall back to simple truncation
                val fallbackTitle = generateTitleFromMessage(userMessage)
                conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
            }
        } catch (e: Exception) {
            val fallbackTitle = generateTitleFromMessage(userMessage)
            try {
                conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
            } catch (_: Exception) { }
        }
    }

    /**
     * Builds the prompt for generating a branch title.
     */
    private fun buildBranchTitlePrompt(userMessage: String, assistantResponse: String): String {
        val truncatedUser = userMessage.take(500)
        val truncatedAssistant = assistantResponse.take(500)

        return "Generate a single emoji and a concise title (maximum 6 words) for this conversation branch. " +
            "The title should capture what makes this branch different - the new direction or topic explored. " +
            "Focus on the NEW content, not the original conversation. " +
            "Format: [emoji] [title] - for example: 🔀 Alternative API approach\n" +
            "Only respond with the emoji and title, no quotes, no explanation, no punctuation at the end.\n\n" +
            "New user message: $truncatedUser\n\n" +
            "Assistant response: $truncatedAssistant\n\n" +
            "Response:"
    }

    /**
     * Parses the AI response to extract emoji and title for a branch.
     */
    private fun parseBranchTitleResponse(response: String): BranchTitleResult {
        val trimmed = response
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^(Response|Title):\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // Regex to match emoji at the start
        val emojiPattern = Regex(
            "^[\\p{So}\\p{Cs}\\u200D\\uFE0F\\u2600-\\u26FF\\u2700-\\u27BF]+"
        )
        val emojiMatch = emojiPattern.find(trimmed)

        return if (emojiMatch != null && emojiMatch.range.first == 0) {
            val emoji = emojiMatch.value
            val titlePart = trimmed.substring(emojiMatch.range.last + 1).trim()
            val cleanedTitle = cleanBranchTitle(titlePart)
            BranchTitleResult(title = cleanedTitle, icon = emoji)
        } else {
            BranchTitleResult(title = cleanBranchTitle(trimmed), icon = null)
        }
    }

    private fun cleanBranchTitle(title: String): String {
        var cleaned = title
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^Title:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[.!?]+$"), "")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words = cleaned.split(" ")
        if (words.size > 6) {
            cleaned = words.take(6).joinToString(" ")
        }

        if (cleaned.length > 60) {
            cleaned = cleaned.take(57) + "..."
        }

        return cleaned.ifBlank { "Branch" }
    }

    private data class BranchTitleResult(val title: String, val icon: String?)

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
