package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a single message in a conversation.
 *
 * @property id Unique identifier for the message
 * @property conversationId The ID of the conversation this message belongs to
 * @property role The role of the message sender (USER, ASSISTANT, or SYSTEM)
 * @property content The text content of the message
 * @property thinkingContent The thinking/reasoning content (for models that support it)
 * @property attachments List of image attachments for this message (for multimodal support)
 * @property isStreaming Whether the message is currently being streamed
 * @property createdAt Timestamp when the message was created (epoch milliseconds)
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val thinkingContent: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val isStreaming: Boolean = false,
    val thinkingDurationMs: Long? = null,
    val totalDurationMs: Long? = null,
    val modelName: String? = null,
    val fusionMetadata: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Whether this message has any image attachments.
     */
    val hasAttachments: Boolean
        get() = attachments.isNotEmpty()
}
