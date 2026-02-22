package com.materialchat.domain.usecase

import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Use case for managing bookmark CRUD operations.
 *
 * Provides add, remove, update, and toggle operations for message bookmarks.
 * Toggle is a convenience operation that adds a bookmark if it doesn't exist,
 * or removes it if it does.
 */
class ManageBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {

    /**
     * Adds a new bookmark for a message.
     */
    suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkRepository.addBookmark(bookmark)
    }

    /**
     * Removes a bookmark by its ID.
     */
    suspend fun removeBookmark(bookmarkId: String) {
        bookmarkRepository.deleteBookmarkById(bookmarkId)
    }

    /**
     * Removes the bookmark associated with a specific message.
     */
    suspend fun removeBookmarkByMessageId(messageId: String) {
        val bookmark = bookmarkRepository.getBookmarkByMessageId(messageId)
        if (bookmark != null) {
            bookmarkRepository.deleteBookmark(bookmark)
        }
    }

    /**
     * Updates an existing bookmark's metadata (tags, note, category).
     */
    suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkRepository.updateBookmark(bookmark)
    }

    /**
     * Toggles the bookmark state for a message.
     *
     * If the message is already bookmarked, removes the bookmark.
     * If not bookmarked, creates a new bookmark with the given parameters.
     *
     * @param messageId The ID of the message to toggle
     * @param conversationId The conversation containing the message
     * @param category The category for the new bookmark (used only when adding)
     * @param tags Tags for the new bookmark (used only when adding)
     * @param note Note for the new bookmark (used only when adding)
     * @return true if the message is now bookmarked, false if it was un-bookmarked
     */
    suspend fun toggleBookmark(
        messageId: String,
        conversationId: String,
        category: BookmarkCategory = BookmarkCategory.GENERAL,
        tags: List<String> = emptyList(),
        note: String? = null
    ): Boolean {
        val existing = bookmarkRepository.getBookmarkByMessageId(messageId)
        return if (existing != null) {
            bookmarkRepository.deleteBookmark(existing)
            false
        } else {
            val bookmark = Bookmark(
                messageId = messageId,
                conversationId = conversationId,
                tags = tags,
                note = note,
                category = category
            )
            bookmarkRepository.addBookmark(bookmark)
            true
        }
    }

    /**
     * Checks whether a specific message is bookmarked.
     */
    suspend fun isMessageBookmarked(messageId: String): Boolean {
        return bookmarkRepository.isMessageBookmarked(messageId)
    }
}
