package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a conversation with an AI assistant.
 *
 * @property id Unique identifier for the conversation
 * @property title Display title for the conversation
 * @property icon Optional emoji icon for the conversation (AI-generated)
 * @property providerId The ID of the provider used for this conversation
 * @property modelName The name of the AI model being used
 * @property parentId Optional parent conversation ID (null = root, non-null = branch)
 * @property createdAt Timestamp when the conversation was created (epoch milliseconds)
 * @property updatedAt Timestamp when the conversation was last updated (epoch milliseconds)
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val icon: String? = null,
    val providerId: String,
    val modelName: String,
    val parentId: String? = null,
    val branchSourceMessageId: String? = null,
    val personaId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Whether this conversation is a branch (has a parent).
     */
    val isBranch: Boolean get() = parentId != null

    companion object {
        /**
         * Generates a default title for a new conversation.
         */
        fun generateDefaultTitle(): String = "New Chat"

        /**
         * Generates a default title for a new branch conversation.
         */
        fun generateDefaultBranchTitle(): String = "New Branch"
    }
}
