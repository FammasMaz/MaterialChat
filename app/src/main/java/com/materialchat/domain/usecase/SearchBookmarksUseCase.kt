package com.materialchat.domain.usecase

import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for searching and filtering bookmarks in the knowledge base.
 *
 * Provides reactive observation of bookmarks with optional category and tag filters,
 * as well as retrieval of all unique tags for the filter UI.
 */
class SearchBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {

    /**
     * Observes all bookmarks ordered by creation date (newest first).
     */
    fun observeAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkRepository.observeAllBookmarks()

    /**
     * Observes bookmarks filtered by category.
     */
    fun observeByCategory(category: BookmarkCategory): Flow<List<Bookmark>> =
        bookmarkRepository.observeBookmarksByCategory(category)

    /**
     * Observes bookmarks filtered by tag.
     */
    fun observeByTag(tag: String): Flow<List<Bookmark>> =
        bookmarkRepository.observeBookmarksByTag(tag)

    /**
     * Observes whether a specific message is bookmarked.
     */
    fun isMessageBookmarkedFlow(messageId: String): Flow<Boolean> =
        bookmarkRepository.isMessageBookmarkedFlow(messageId)

    /**
     * Gets all unique tags across all bookmarks.
     */
    suspend fun getAllTags(): List<String> =
        bookmarkRepository.getAllTags()
}
