package com.materialchat.domain.model

/**
 * Represents a node in the conversation tree for the Mind Map visualizer.
 *
 * @property id The conversation ID this node represents
 * @property title The display title of the conversation
 * @property icon Optional emoji icon
 * @property modelName The AI model used in this conversation
 * @property messageCount The number of messages in this conversation
 * @property children Child nodes (branches) of this conversation
 * @property isCurrentConversation Whether this node represents the currently viewed conversation
 */
data class ConversationTreeNode(
    val id: String,
    val title: String,
    val icon: String? = null,
    val modelName: String,
    val messageCount: Int = 0,
    val children: List<ConversationTreeNode> = emptyList(),
    val isCurrentConversation: Boolean = false
)
