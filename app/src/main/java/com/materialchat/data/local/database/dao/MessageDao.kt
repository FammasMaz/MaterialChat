package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for message operations.
 *
 * Provides CRUD operations for messages stored in the database.
 * Messages are automatically cascade deleted when their parent conversation is deleted.
 */
@Dao
interface MessageDao {

    /**
     * Insert a new message. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * Insert multiple messages. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * Update an existing message.
     */
    @Update
    suspend fun update(message: MessageEntity)

    /**
     * Delete a message.
     */
    @Delete
    suspend fun delete(message: MessageEntity)

    /**
     * Delete a message by ID.
     */
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    /**
     * Get all messages for a conversation as a Flow, ordered by created_at (oldest first).
     */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    /**
     * Get all messages for a conversation as a one-shot list.
     */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getMessagesForConversationOnce(conversationId: String): List<MessageEntity>

    /**
     * Get a message by ID.
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /**
     * Get the last message in a conversation.
     */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String): MessageEntity?

    /**
     * Get the last assistant message in a conversation.
     */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND role = 'ASSISTANT' ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastAssistantMessage(conversationId: String): MessageEntity?

    /**
     * Update message content (used during streaming).
     */
    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateContent(messageId: String, content: String)

    /**
     * Update message content and thinking content (used during streaming with thinking models).
     */
    @Query("UPDATE messages SET content = :content, thinking_content = :thinkingContent WHERE id = :messageId")
    suspend fun updateContentWithThinking(messageId: String, content: String, thinkingContent: String?)

    /**
     * Update streaming status of a message.
     */
    @Query("UPDATE messages SET is_streaming = :isStreaming WHERE id = :messageId")
    suspend fun updateStreamingStatus(messageId: String, isStreaming: Boolean)

    /**
     * Update both content and streaming status (used when streaming completes).
     */
    @Query("UPDATE messages SET content = :content, is_streaming = :isStreaming WHERE id = :messageId")
    suspend fun updateContentAndStreamingStatus(messageId: String, content: String, isStreaming: Boolean)

    /**
     * Delete all messages in a conversation.
     */
    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteAllMessagesInConversation(conversationId: String)

    /**
     * Get the count of messages in a conversation.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    /**
     * Delete the last N messages in a conversation (for regeneration).
     */
    @Query("""
        DELETE FROM messages
        WHERE id IN (
            SELECT id FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC
            LIMIT :count
        )
    """)
    suspend fun deleteLastMessages(conversationId: String, count: Int)

    /**
     * Check if any messages are currently streaming in a conversation.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE conversation_id = :conversationId AND is_streaming = 1)")
    suspend fun hasStreamingMessage(conversationId: String): Boolean

    /**
     * Get all streaming messages (for cleanup on app restart).
     */
    @Query("SELECT * FROM messages WHERE is_streaming = 1")
    suspend fun getAllStreamingMessages(): List<MessageEntity>

    /**
     * Mark all streaming messages as complete (for cleanup on app restart).
     */
    @Query("UPDATE messages SET is_streaming = 0 WHERE is_streaming = 1")
    suspend fun completeAllStreamingMessages()
}
