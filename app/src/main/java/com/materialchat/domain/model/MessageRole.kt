package com.materialchat.domain.model

/**
 * Represents the role of a message in a conversation.
 */
enum class MessageRole {
    /**
     * A message from the user.
     */
    USER,

    /**
     * A message from the AI assistant.
     */
    ASSISTANT,

    /**
     * A system message that sets the behavior of the assistant.
     */
    SYSTEM
}
