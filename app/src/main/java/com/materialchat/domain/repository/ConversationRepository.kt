package com.materialchat.domain.repository

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for conversation and message persistence operations.
 */
interface ConversationRepository {

    // ========== Conversation Operations ==========

    /**
     * Observes all conversations sorted by most recently updated.
     *
     * @return A Flow emitting the list of conversations whenever it changes
     */
    fun observeConversations(): Flow<List<Conversation>>

    /**
     * Gets a single conversation by its ID.
     *
     * @param conversationId The ID of the conversation to retrieve
     * @return The conversation, or null if not found
     */
    suspend fun getConversation(conversationId: String): Conversation?

    /**
     * Creates a new conversation.
     *
     * @param conversation The conversation to create
     * @return The ID of the created conversation
     */
    suspend fun createConversation(conversation: Conversation): String

    /**
     * Updates an existing conversation.
     *
     * @param conversation The conversation with updated fields
     */
    suspend fun updateConversation(conversation: Conversation)

    /**
     * Deletes a conversation and all its messages.
     *
     * @param conversationId The ID of the conversation to delete
     */
    suspend fun deleteConversation(conversationId: String)

    /**
     * Updates the title of a conversation.
     *
     * @param conversationId The ID of the conversation
     * @param title The new title
     */
    suspend fun updateConversationTitle(conversationId: String, title: String)

    /**
     * Updates the model used in a conversation.
     *
     * @param conversationId The ID of the conversation
     * @param modelName The new model name
     */
    suspend fun updateConversationModel(conversationId: String, modelName: String)

    // ========== Message Operations ==========

    /**
     * Observes all messages in a conversation sorted by creation time.
     *
     * @param conversationId The ID of the conversation
     * @return A Flow emitting the list of messages whenever it changes
     */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /**
     * Gets all messages in a conversation.
     *
     * @param conversationId The ID of the conversation
     * @return The list of messages
     */
    suspend fun getMessages(conversationId: String): List<Message>

    /**
     * Adds a new message to a conversation.
     *
     * @param message The message to add
     * @return The ID of the created message
     */
    suspend fun addMessage(message: Message): String

    /**
     * Updates an existing message.
     *
     * @param message The message with updated fields
     */
    suspend fun updateMessage(message: Message)

    /**
     * Updates the content of a message (used during streaming).
     *
     * @param messageId The ID of the message
     * @param content The new content
     */
    suspend fun updateMessageContent(messageId: String, content: String)

    /**
     * Sets the streaming state of a message.
     *
     * @param messageId The ID of the message
     * @param isStreaming Whether the message is currently streaming
     */
    suspend fun setMessageStreaming(messageId: String, isStreaming: Boolean)

    /**
     * Deletes a message.
     *
     * @param messageId The ID of the message to delete
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Deletes the last message in a conversation (used for regeneration).
     *
     * @param conversationId The ID of the conversation
     */
    suspend fun deleteLastMessage(conversationId: String)

    // ========== Export Operations ==========

    /**
     * Exports a conversation to JSON format.
     *
     * @param conversationId The ID of the conversation to export
     * @return The JSON string representation of the conversation
     */
    suspend fun exportToJson(conversationId: String): String

    /**
     * Exports a conversation to Markdown format.
     *
     * @param conversationId The ID of the conversation to export
     * @return The Markdown string representation of the conversation
     */
    suspend fun exportToMarkdown(conversationId: String): String
}
