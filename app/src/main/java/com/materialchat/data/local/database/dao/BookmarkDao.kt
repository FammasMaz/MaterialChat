package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for bookmark operations.
 *
 * Provides CRUD, search, and filtering operations for message bookmarks.
 */
@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: String)

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :bookmarkId")
    suspend fun getBookmarkById(bookmarkId: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE message_id = :messageId")
    suspend fun getBookmarkByMessageId(messageId: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE message_id = :messageId")
    fun getBookmarkByMessageIdFlow(messageId: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE category = :category ORDER BY created_at DESC")
    fun getBookmarksByCategory(category: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE tags LIKE '%' || :tag || '%' ORDER BY created_at DESC")
    fun getBookmarksByTag(tag: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE message_id = :messageId)")
    suspend fun isMessageBookmarked(messageId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE message_id = :messageId)")
    fun isMessageBookmarkedFlow(messageId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun getBookmarkCount(): Int

    @Query("SELECT DISTINCT tags FROM bookmarks WHERE tags IS NOT NULL")
    suspend fun getAllTags(): List<String>
}
