package com.materialchat.domain.repository

import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for bookmark operations.
 *
 * Provides reactive observation, CRUD, and filtering capabilities
 * for the message bookmarks knowledge base.
 */
interface BookmarkRepository {

    /**
     * Observes all bookmarks ordered by creation date (newest first).
     */
    fun observeAllBookmarks(): Flow<List<Bookmark>>

    /**
     * Observes bookmarks filtered by category.
     */
    fun observeBookmarksByCategory(category: BookmarkCategory): Flow<List<Bookmark>>

    /**
     * Observes bookmarks filtered by tag (substring match).
     */
    fun observeBookmarksByTag(tag: String): Flow<List<Bookmark>>

    /**
     * Observes whether a specific message is bookmarked.
     */
    fun isMessageBookmarkedFlow(messageId: String): Flow<Boolean>

    /**
     * Gets a bookmark by its ID.
     */
    suspend fun getBookmarkById(bookmarkId: String): Bookmark?

    /**
     * Gets a bookmark by the message ID it references.
     */
    suspend fun getBookmarkByMessageId(messageId: String): Bookmark?

    /**
     * Checks whether a specific message is bookmarked.
     */
    suspend fun isMessageBookmarked(messageId: String): Boolean

    /**
     * Adds a new bookmark.
     */
    suspend fun addBookmark(bookmark: Bookmark)

    /**
     * Updates an existing bookmark.
     */
    suspend fun updateBookmark(bookmark: Bookmark)

    /**
     * Deletes a bookmark.
     */
    suspend fun deleteBookmark(bookmark: Bookmark)

    /**
     * Deletes a bookmark by its ID.
     */
    suspend fun deleteBookmarkById(bookmarkId: String)

    /**
     * Gets all unique tags across all bookmarks, sorted alphabetically.
     */
    suspend fun getAllTags(): List<String>

    /**
     * Gets the total number of bookmarks.
     */
    suspend fun getBookmarkCount(): Int
}
