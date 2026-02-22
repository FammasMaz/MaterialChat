package com.materialchat.ui.screens.bookmarks

import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.domain.model.Message

/**
 * UI state for the Bookmarks / Knowledge Base screen.
 */
sealed interface BookmarksUiState {

    /**
     * Loading state while bookmarks are being fetched.
     */
    data object Loading : BookmarksUiState

    /**
     * Success state with bookmark data loaded.
     *
     * @property bookmarks The list of bookmarks with their associated message data
     * @property allTags All unique tags across all bookmarks
     * @property selectedCategory Optional category filter currently applied
     * @property selectedTag Optional tag filter currently applied
     * @property searchQuery The current search query text
     * @property showAddBookmarkSheet Whether the add-bookmark bottom sheet is visible
     * @property addBookmarkMessageId The message ID for the pending add-bookmark action
     * @property addBookmarkConversationId The conversation ID for the pending add-bookmark action
     */
    data class Success(
        val bookmarks: List<BookmarkWithMessage> = emptyList(),
        val allTags: List<String> = emptyList(),
        val selectedCategory: BookmarkCategory? = null,
        val selectedTag: String? = null,
        val searchQuery: String = "",
        val showAddBookmarkSheet: Boolean = false,
        val addBookmarkMessageId: String? = null,
        val addBookmarkConversationId: String? = null
    ) : BookmarksUiState {

        /**
         * Bookmarks filtered by the current search query.
         * Searches across message content, tags, notes, and category names.
         */
        val filteredBookmarks: List<BookmarkWithMessage>
            get() {
                if (searchQuery.isBlank()) return bookmarks
                val query = searchQuery.trim().lowercase()
                return bookmarks.filter { item ->
                    item.messageContent.lowercase().contains(query) ||
                        item.bookmark.tags.any { it.lowercase().contains(query) } ||
                        item.bookmark.note?.lowercase()?.contains(query) == true ||
                        item.bookmark.category.displayName.lowercase().contains(query) ||
                        item.conversationTitle.lowercase().contains(query)
                }
            }
    }

    /**
     * Error state when bookmarks fail to load.
     *
     * @property message The error message to display
     */
    data class Error(val message: String) : BookmarksUiState
}

/**
 * A bookmark paired with its associated message and conversation data
 * for display in the UI.
 *
 * @property bookmark The bookmark domain model
 * @property messageContent The content of the bookmarked message
 * @property messageRole The role (USER/ASSISTANT) of the message sender
 * @property modelName The AI model that generated the message (null for user messages)
 * @property conversationTitle The title of the conversation containing the message
 * @property conversationId The ID of the conversation
 */
data class BookmarkWithMessage(
    val bookmark: Bookmark,
    val messageContent: String,
    val messageRole: String,
    val modelName: String? = null,
    val conversationTitle: String,
    val conversationId: String
)
