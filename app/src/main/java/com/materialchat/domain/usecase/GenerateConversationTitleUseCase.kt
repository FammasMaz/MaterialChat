package com.materialchat.domain.usecase

import android.util.Log
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Represents the result of AI title generation containing both emoji and title.
 */
data class TitleGenerationResult(
    val title: String,
    val icon: String?
)

/**
 * Use case for generating AI-powered conversation titles with emoji icons.
 *
 * This use case generates a concise, meaningful title for a conversation
 * using the AI model configured in settings (or falls back to the conversation's model).
 * The title is limited to 6 words maximum and includes a relevant emoji icon.
 */
class GenerateConversationTitleUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "GenerateTitleUseCase"
        private const val MAX_TITLE_WORDS = 6
        private const val MAX_TITLE_LENGTH = 60
        private const val FALLBACK_MAX_LENGTH = 40

        // Regex to match emoji at the start of a string
        private val EMOJI_PATTERN = Regex(
            "^[\\p{So}\\p{Cs}\\u200D\\uFE0F\\u2600-\\u26FF\\u2700-\\u27BF" +
            "\\U0001F300-\\U0001F5FF\\U0001F600-\\U0001F64F\\U0001F680-\\U0001F6FF" +
            "\\U0001F700-\\U0001F77F\\U0001F780-\\U0001F7FF\\U0001F800-\\U0001F8FF" +
            "\\U0001F900-\\U0001F9FF\\U0001FA00-\\U0001FA6F\\U0001FA70-\\U0001FAFF]+"
        )
    }

    /**
     * Generates an AI-powered title and emoji for a conversation.
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
        Log.d(TAG, "Starting title generation for conversation: $conversationId")
        try {
            val conversation = conversationRepository.getConversation(conversationId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Conversation not found: $conversationId")
                )

            val provider = providerRepository.getProvider(conversation.providerId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Provider not found: ${conversation.providerId}")
                )

            // Check if a custom model is configured for title generation
            val customModel = appPreferences.titleGenerationModel.first()
            val modelToUse = if (customModel.isNotBlank()) {
                Log.d(TAG, "Using custom title generation model: $customModel")
                customModel
            } else {
                conversation.modelName
            }

            Log.d(TAG, "Using provider: ${provider.name}, model: $modelToUse")
            val prompt = buildTitlePrompt(userMessage, assistantResponse)
            Log.d(TAG, "Prompt length: ${prompt.length}")

            val result = chatRepository.generateSimpleCompletion(
                provider = provider,
                prompt = prompt,
                model = modelToUse
            )

            result.fold(
                onSuccess = { generatedResponse ->
                    Log.d(TAG, "AI generated response: $generatedResponse")
                    val parsed = parseEmojiAndTitle(generatedResponse)
                    Log.d(TAG, "Parsed - icon: ${parsed.icon}, title: ${parsed.title}")
                    conversationRepository.updateConversationTitleAndIcon(
                        conversationId,
                        parsed.title,
                        parsed.icon
                    )
                    Result.success(parsed.title)
                },
                onFailure = { error ->
                    Log.e(TAG, "Title generation failed: ${error.message}", error)
                    val fallbackTitle = generateFallbackTitle(userMessage)
                    Log.d(TAG, "Using fallback title: $fallbackTitle")
                    conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
                    Result.success(fallbackTitle)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during title generation: ${e.message}", e)
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

        return "Generate a single emoji and a concise title (maximum $MAX_TITLE_WORDS words) for this conversation. " +
            "The emoji should represent the main topic. The title should capture the purpose. " +
            "Format: [emoji] [title] - for example: 💻 Python Code Review\n" +
            "Only respond with the emoji and title, no quotes, no explanation, no punctuation at the end.\n\n" +
            "User: $truncatedUser\n\n" +
            "Assistant: $truncatedAssistant\n\n" +
            "Response:"
    }

    /**
     * Parses the AI response to extract emoji and title.
     * Expected format: "🎯 Title Here" or just "Title Here"
     */
    private fun parseEmojiAndTitle(response: String): TitleGenerationResult {
        val trimmed = response
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^(Response|Title):\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // Try to find emoji at the start
        val emojiMatch = EMOJI_PATTERN.find(trimmed)

        return if (emojiMatch != null && emojiMatch.range.first == 0) {
            val emoji = emojiMatch.value
            val titlePart = trimmed.substring(emojiMatch.range.last + 1).trim()
            val cleanedTitle = cleanTitle(titlePart)
            TitleGenerationResult(
                title = cleanedTitle,
                icon = emoji
            )
        } else {
            // No emoji found at start, use the whole response as title
            TitleGenerationResult(
                title = cleanTitle(trimmed),
                icon = null
            )
        }
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
