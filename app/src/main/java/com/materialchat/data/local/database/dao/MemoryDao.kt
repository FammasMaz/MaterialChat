package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.MemoryEntity
import com.materialchat.data.local.database.entity.MemorySnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)

    @Query("SELECT * FROM memories ORDER BY updated_at DESC")
    suspend fun getAllMemoriesForBackup(): List<MemoryEntity>

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE normalized_content = :normalizedContent LIMIT 1")
    suspend fun getByNormalizedContent(normalizedContent: String): MemoryEntity?

    @Query("""
        SELECT * FROM memories
        WHERE is_archived = 0
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    suspend fun getActiveMemories(limit: Int): List<MemoryEntity>

    @Query("""
        SELECT * FROM memories
        WHERE is_archived = 0
        ORDER BY updated_at DESC
    """)
    fun observeActiveMemories(): Flow<List<MemoryEntity>>

    @Query("""
        UPDATE memories
        SET recall_count = recall_count + 1,
            last_recalled_at = :recalledAt,
            updated_at = :recalledAt
        WHERE id IN (:ids)
    """)
    suspend fun markRecalled(ids: List<String>, recalledAt: Long)

    @Query("UPDATE memories SET is_archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memories WHERE is_archived = 0")
    suspend fun activeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: MemorySnippetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<MemorySnippetEntity>)

    @Query("SELECT * FROM memory_snippets ORDER BY updated_at DESC")
    suspend fun getAllMemorySnippetsForBackup(): List<MemorySnippetEntity>

    @Query("SELECT * FROM memory_snippets WHERE id IN (:ids)")
    suspend fun getSnippetsByIds(ids: List<String>): List<MemorySnippetEntity>

    @Query("""
        SELECT * FROM memory_snippets
        WHERE is_archived = 0
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    suspend fun getActiveSnippets(limit: Int): List<MemorySnippetEntity>

    @Query("""
        SELECT * FROM memory_snippets
        WHERE is_archived = 0
          AND (
            (:term1 != '' AND normalized_content LIKE '%' || :term1 || '%') OR
            (:term2 != '' AND normalized_content LIKE '%' || :term2 || '%') OR
            (:term3 != '' AND normalized_content LIKE '%' || :term3 || '%') OR
            (:term4 != '' AND normalized_content LIKE '%' || :term4 || '%')
          )
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    suspend fun searchActiveSnippets(
        term1: String,
        term2: String,
        term3: String,
        term4: String,
        limit: Int
    ): List<MemorySnippetEntity>

    @Query("""
        UPDATE memory_snippets
        SET recall_count = recall_count + 1,
            last_recalled_at = :recalledAt,
            updated_at = :recalledAt
        WHERE id IN (:ids)
    """)
    suspend fun markSnippetsRecalled(ids: List<String>, recalledAt: Long)

    @Query("UPDATE memory_snippets SET is_archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setSnippetArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM memory_snippets")
    suspend fun deleteAllSnippets()

    @Query("SELECT COUNT(*) FROM memory_snippets WHERE is_archived = 0")
    suspend fun activeSnippetCount(): Int
}
