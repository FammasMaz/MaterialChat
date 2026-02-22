package com.materialchat.domain.repository

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.SearchQuery
import com.materialchat.domain.model.SearchResult
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
     * Observes a single conversation by its ID.
     * Emits updates whenever the conversation changes (e.g., title update).
     *
     * @param conversationId The ID of the conversation to observe
     * @return A Flow emitting the conversation whenever it changes, or null if not found
     */
    fun observeConversation(conversationId: String): Flow<Conversation?>

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
     * Updates the title and icon of a conversation.
     *
     * @param conversationId The ID of the conversation
     * @param title The new title
     * @param icon The new icon (emoji)
     */
    suspend fun updateConversationTitleAndIcon(conversationId: String, title: String, icon: String?)

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
     * Updates both content and thinking content of a message (used during streaming).
     *
     * @param messageId The ID of the message
     * @param content The new content
     * @param thinkingContent The new thinking content (can be null)
     */
    suspend fun updateMessageContentWithThinking(messageId: String, content: String, thinkingContent: String?)

    /**
     * Sets the streaming state of a message.
     *
     * @param messageId The ID of the message
     * @param isStreaming Whether the message is currently streaming
     */
    suspend fun setMessageStreaming(messageId: String, isStreaming: Boolean)

    /**
     * Updates the thinking and total duration for a message.
     *
     * @param messageId The ID of the message
     * @param thinkingDurationMs The thinking duration in milliseconds
     * @param totalDurationMs The total response duration in milliseconds
     */
    suspend fun updateMessageDurations(messageId: String, thinkingDurationMs: Long?, totalDurationMs: Long?)

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

    // ========== Search Operations ==========

    /**
     * Searches conversations by title and/or message content.
     *
     * Results are organized title-first: conversations are the primary results,
     * with matching messages shown as context under each conversation.
     * System messages are excluded from search results.
     *
     * @param query The search query parameters
     * @return List of search results ordered by relevance (title matches first)
     */
    suspend fun searchConversations(query: SearchQuery): List<SearchResult>

    // ========== Branch Operations ==========

    /**
     * Observes all root conversations (conversations without a parent).
     *
     * @return A Flow emitting the list of root conversations whenever it changes
     */
    fun observeRootConversations(): Flow<List<Conversation>>

    /**
     * Observes all branches for a specific parent conversation.
     *
     * @param parentId The ID of the parent conversation
     * @return A Flow emitting the list of branches whenever it changes
     */
    fun observeBranches(parentId: String): Flow<List<Conversation>>

    /**
     * Gets all branches for a specific parent conversation.
     *
     * @param parentId The ID of the parent conversation
     * @return The list of branch conversations
     */
    suspend fun getBranches(parentId: String): List<Conversation>

    /**
     * Gets the branch count for a specific parent conversation.
     *
     * @param parentId The ID of the parent conversation
     * @return The number of branches
     */
    suspend fun getBranchCount(parentId: String): Int

    // ========== Sibling Branch Operations ==========

    /**
     * Observes sibling branches sharing the same parent and branch source message.
     * Used for redo-with-model sibling navigation.
     *
     * @param parentId The parent conversation ID
     * @param branchSourceMessageId The source message ID that siblings share
     * @return A Flow emitting the list of sibling conversations
     */
    fun observeSiblingBranches(parentId: String, branchSourceMessageId: String): Flow<List<Conversation>>

    /**
     * Gets sibling branches sharing the same parent and branch source message (one-shot).
     *
     * @param parentId The parent conversation ID
     * @param branchSourceMessageId The source message ID that siblings share
     * @return The list of sibling conversations
     */
    suspend fun getSiblingBranches(parentId: String, branchSourceMessageId: String): List<Conversation>

    /**
     * Updates the model name for a specific message.
     *
     * @param messageId The ID of the message
     * @param modelName The model name to set
     */
    suspend fun updateMessageModelName(messageId: String, modelName: String)

    /**
     * Updates the fusion metadata for a specific message.
     *
     * @param messageId The ID of the message
     * @param fusionMetadata The serialized fusion metadata JSON string
     */
    suspend fun updateMessageFusionMetadata(messageId: String, fusionMetadata: String?)
}
