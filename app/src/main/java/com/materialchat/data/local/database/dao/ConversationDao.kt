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
     */
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * Get all conversations as a one-shot list.
     */
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
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
}
