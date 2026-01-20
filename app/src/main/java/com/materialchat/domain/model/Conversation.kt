package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a conversation with an AI assistant.
 *
 * @property id Unique identifier for the conversation
 * @property title Display title for the conversation
 * @property providerId The ID of the provider used for this conversation
 * @property modelName The name of the AI model being used
 * @property createdAt Timestamp when the conversation was created (epoch milliseconds)
 * @property updatedAt Timestamp when the conversation was last updated (epoch milliseconds)
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val providerId: String,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Generates a default title for a new conversation.
         */
        fun generateDefaultTitle(): String = "New Chat"
    }
}
