package com.materialchat.domain.usecase

import com.materialchat.domain.repository.ConversationRepository
import javax.inject.Inject

/**
 * Use case for exporting conversations to different formats.
 *
 * Supports:
 * - JSON export (structured data)
 * - Markdown export (human-readable)
 */
class ExportConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    /**
     * Export format options.
     */
    enum class ExportFormat {
        JSON,
        MARKDOWN
    }

    /**
     * Exports a conversation to the specified format.
     *
     * @param conversationId The ID of the conversation to export
     * @param format The export format to use
     * @return The exported content as a string
     */
    suspend operator fun invoke(
        conversationId: String,
        format: ExportFormat
    ): Result<ExportResult> {
        return try {
            val conversation = conversationRepository.getConversation(conversationId)
                ?: return Result.failure(IllegalStateException("Conversation not found: $conversationId"))

            val content = when (format) {
                ExportFormat.JSON -> conversationRepository.exportToJson(conversationId)
                ExportFormat.MARKDOWN -> conversationRepository.exportToMarkdown(conversationId)
            }

            val extension = when (format) {
                ExportFormat.JSON -> "json"
                ExportFormat.MARKDOWN -> "md"
            }

            val mimeType = when (format) {
                ExportFormat.JSON -> "application/json"
                ExportFormat.MARKDOWN -> "text/markdown"
            }

            // Generate a filename from the conversation title
            val sanitizedTitle = sanitizeFilename(conversation.title)
            val filename = "${sanitizedTitle}.${extension}"

            Result.success(
                ExportResult(
                    content = content,
                    filename = filename,
                    mimeType = mimeType,
                    format = format
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a conversation to JSON format.
     *
     * @param conversationId The ID of the conversation to export
     * @return The JSON string representation
     */
    suspend fun toJson(conversationId: String): Result<String> {
        return try {
            Result.success(conversationRepository.exportToJson(conversationId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a conversation to Markdown format.
     *
     * @param conversationId The ID of the conversation to export
     * @return The Markdown string representation
     */
    suspend fun toMarkdown(conversationId: String): Result<String> {
        return try {
            Result.success(conversationRepository.exportToMarkdown(conversationId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sanitizes a filename by removing or replacing invalid characters.
     */
    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(50)
            .trimEnd('_')
            .ifEmpty { "conversation" }
    }
}

/**
 * Result of an export operation containing the content and metadata.
 *
 * @property content The exported content as a string
 * @property filename Suggested filename for the export
 * @property mimeType MIME type for the content
 * @property format The format used for export
 */
data class ExportResult(
    val content: String,
    val filename: String,
    val mimeType: String,
    val format: ExportConversationUseCase.ExportFormat
)
