package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for conversation operations.
 *
 * Provides CRUD operations for conversations stored in the database.
 * All query methods return Flow for reactive UI updates.
 */
@Dao
interface ConversationDao {

    /**
     * Insert a new conversation. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    /**
     * Update an existing conversation.
     */
    @Update
    suspend fun update(conversation: ConversationEntity)

    /**
     * Delete a conversation. Messages are cascade deleted.
     */
    @Delete
    suspend fun delete(conversation: ConversationEntity)

    /**
     * Delete a conversation by ID. Messages are cascade deleted.
     */
    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteById(conversationId: String)

    /**
     * Get all conversations as a Flow, ordered by updated_at (newest first).
     * Only includes conversations that have at least one message.
     */
    @Query("""
        SELECT c.* FROM conversations c
        WHERE EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
        ORDER BY c.updated_at DESC
    """)
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * Get all conversations as a one-shot list.
     * Only includes conversations that have at least one message.
     */
    @Query("""
        SELECT c.* FROM conversations c
        WHERE EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
        ORDER BY c.updated_at DESC
    """)
    suspend fun getAllConversationsOnce(): List<ConversationEntity>

    /**
     * Get a conversation by ID.
     */
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    /**
     * Get a conversation by ID as a Flow for reactive updates.
     */
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun getConversationByIdFlow(conversationId: String): Flow<ConversationEntity?>

    /**
     * Get all conversations for a specific provider.
     */
    @Query("SELECT * FROM conversations WHERE provider_id = :providerId ORDER BY updated_at DESC")
    fun getConversationsByProvider(providerId: String): Flow<List<ConversationEntity>>

    /**
     * Update the title of a conversation.
     */
    @Query("UPDATE conversations SET title = :title, updated_at = :updatedAt WHERE id = :conversationId")
    suspend fun updateTitle(conversationId: String, title: String, updatedAt: Long)

    /**
     * Update the title and icon of a conversation.
     */
    @Query("UPDATE conversations SET title = :title, icon = :icon, updated_at = :updatedAt WHERE id = :conversationId")
    suspend fun updateTitleAndIcon(conversationId: String, title: String, icon: String?, updatedAt: Long)

    /**
     * Update the model of a conversation.
     */
    @Query("UPDATE conversations SET model_name = :modelName, updated_at = :updatedAt WHERE id = :conversationId")
    suspend fun updateModel(conversationId: String, modelName: String, updatedAt: Long)

    /**
     * Update the updated_at timestamp of a conversation.
     */
    @Query("UPDATE conversations SET updated_at = :updatedAt WHERE id = :conversationId")
    suspend fun updateTimestamp(conversationId: String, updatedAt: Long)

    /**
     * Get the count of conversations.
     */
    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getConversationCount(): Int

    /**
     * Check if a conversation exists by ID.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM conversations WHERE id = :conversationId)")
    suspend fun conversationExists(conversationId: String): Boolean

    /**
     * Search conversations by title.
     */
    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updated_at DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    /**
     * Search conversations by title (one-shot).
     * Used for search functionality to avoid loading all conversations into memory.
     *
     * @param query The search query string
     * @param limit Maximum number of results to return
     */
    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY updated_at DESC LIMIT :limit")
    suspend fun searchConversationsByTitle(query: String, limit: Int): List<ConversationEntity>

    // ========== Branch Operations ==========

    /**
     * Get all root conversations (conversations without a parent) as a Flow.
     * Only includes conversations that have at least one message.
     */
    @Query("""
        SELECT c.* FROM conversations c
        WHERE c.parent_id IS NULL
        AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
        ORDER BY c.updated_at DESC
    """)
    fun getRootConversations(): Flow<List<ConversationEntity>>

    /**
     * Get all branches for a specific parent conversation.
     * Ordered by most recently updated first.
     */
    @Query("""
        SELECT * FROM conversations
        WHERE parent_id = :parentId
        ORDER BY updated_at DESC
    """)
    fun getBranchesForConversation(parentId: String): Flow<List<ConversationEntity>>

    /**
     * Get all branches for a specific parent conversation (one-shot).
     */
    @Query("""
        SELECT * FROM conversations
        WHERE parent_id = :parentId
        ORDER BY updated_at DESC
    """)
    suspend fun getBranchesForConversationOnce(parentId: String): List<ConversationEntity>

    /**
     * Get branch count for a specific parent conversation.
     */
    @Query("SELECT COUNT(*) FROM conversations WHERE parent_id = :parentId")
    suspend fun getBranchCount(parentId: String): Int

    /**
     * Get all root conversations with their branch counts.
     * Returns a Flow of root conversations.
     */
    @Query("""
        SELECT c.* FROM conversations c
        WHERE c.parent_id IS NULL
        AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
        ORDER BY c.updated_at DESC
    """)
    fun getRootConversationsWithBranches(): Flow<List<ConversationEntity>>

    // ========== Sibling Branch Operations ==========

    /**
     * Get sibling branches sharing the same parent and branch source message.
     * Used for redo-with-model sibling navigation.
     */
    @Query("""
        SELECT * FROM conversations
        WHERE parent_id = :parentId
        AND branch_source_message_id = :branchSourceMessageId
        ORDER BY created_at ASC
    """)
    fun getSiblingBranches(parentId: String, branchSourceMessageId: String): Flow<List<ConversationEntity>>

    /**
     * Get sibling branches sharing the same parent and branch source message (one-shot).
     */
    @Query("""
        SELECT * FROM conversations
        WHERE parent_id = :parentId
        AND branch_source_message_id = :branchSourceMessageId
        ORDER BY created_at ASC
    """)
    suspend fun getSiblingBranchesOnce(parentId: String, branchSourceMessageId: String): List<ConversationEntity>

    // ========== Insights Aggregate Queries ==========

    /**
     * Get total conversation count (for insights dashboard).
     */
    @Query("SELECT COUNT(*) FROM conversations WHERE parent_id IS NULL")
    suspend fun getRootConversationCount(): Int

    /**
     * Get conversation count within a time range.
     */
    @Query("SELECT COUNT(*) FROM conversations WHERE created_at >= :sinceTimestamp AND parent_id IS NULL")
    suspend fun getConversationCountSince(sinceTimestamp: Long): Int
}
