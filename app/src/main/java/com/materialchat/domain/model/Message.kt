package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a single message in a conversation.
 *
 * @property id Unique identifier for the message
 * @property conversationId The ID of the conversation this message belongs to
 * @property role The role of the message sender (USER, ASSISTANT, or SYSTEM)
 * @property content The text content of the message
 * @property isStreaming Whether the message is currently being streamed
 * @property createdAt Timestamp when the message was created (epoch milliseconds)
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
