package com.materialchat.domain.usecase

import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for generating AI-powered conversation titles.
 *
 * This use case generates a concise, meaningful title for a conversation
 * using the same AI model that's being used in the chat. The title is
 * limited to 6 words maximum.
 */
class GenerateConversationTitleUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository
) {
    companion object {
        private const val MAX_TITLE_WORDS = 6
        private const val MAX_TITLE_LENGTH = 60
        private const val FALLBACK_MAX_LENGTH = 40
    }

    /**
     * Generates an AI-powered title for a conversation.
     *
     * @param conversationId The ID of the conversation to update
     * @param userMessage The user's first message in the conversation
     * @param assistantResponse The AI's first response
     * @return Result containing the generated title or an error
     */
    suspend operator fun invoke(
        conversationId: String,
        userMessage: String,
        assistantResponse: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conversation = conversationRepository.getConversation(conversationId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Conversation not found: $conversationId")
                )

            val provider = providerRepository.getProvider(conversation.providerId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Provider not found: ${conversation.providerId}")
                )

            val prompt = buildTitlePrompt(userMessage, assistantResponse)

            val result = chatRepository.generateSimpleCompletion(
                provider = provider,
                prompt = prompt,
                model = conversation.modelName
            )

            result.fold(
                onSuccess = { generatedTitle ->
                    val cleanedTitle = cleanTitle(generatedTitle)
                    conversationRepository.updateConversationTitle(conversationId, cleanedTitle)
                    Result.success(cleanedTitle)
                },
                onFailure = { _ ->
                    val fallbackTitle = generateFallbackTitle(userMessage)
                    conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
                    Result.success(fallbackTitle)
                }
            )
        } catch (e: Exception) {
            val fallbackTitle = generateFallbackTitle(userMessage)
            try {
                conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
            } catch (_: Exception) { }
            Result.success(fallbackTitle)
        }
    }

    private fun buildTitlePrompt(userMessage: String, assistantResponse: String): String {
        val truncatedUser = userMessage.take(500)
        val truncatedAssistant = assistantResponse.take(500)

        return "Generate a concise title (maximum $MAX_TITLE_WORDS words) for this conversation. " +
            "The title should capture the main topic or purpose. " +
            "Only respond with the title itself, no quotes, no explanation, no punctuation at the end.\n\n" +
            "User: $truncatedUser\n\n" +
            "Assistant: $truncatedAssistant\n\n" +
            "Title:"
    }

    private fun cleanTitle(title: String): String {
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
        if (words.size > MAX_TITLE_WORDS) {
            cleaned = words.take(MAX_TITLE_WORDS).joinToString(" ")
        }

        if (cleaned.length > MAX_TITLE_LENGTH) {
            cleaned = cleaned.take(MAX_TITLE_LENGTH - 3) + "..."
        }

        return cleaned.ifBlank { "New Chat" }
    }

    private fun generateFallbackTitle(content: String): String {
        val cleaned = content
            .replace("\n", " ")
            .replace(Regex("[\\r\\t]"), "")
            .trim()

        return if (cleaned.length <= FALLBACK_MAX_LENGTH) {
            cleaned
        } else {
            cleaned.take(FALLBACK_MAX_LENGTH - 3) + "..."
        }
    }
}
