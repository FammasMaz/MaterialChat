package com.materialchat.ui.screens.search

import androidx.compose.ui.text.AnnotatedString
import com.materialchat.domain.model.MatchType

/**
 * UI state for the search functionality.
 */
sealed interface SearchUiState {

    /**
     * Initial idle state - no search has been performed yet.
     */
    data object Idle : SearchUiState

    /**
     * Loading state while search is in progress.
     */
    data object Loading : SearchUiState

    /**
     * Success state with search results.
     *
     * @property query The search query that produced these results
     * @property results The list of search result items to display
     * @property totalResults Total number of results found
     */
    data class Results(
        val query: String,
        val results: List<SearchResultUiItem>,
        val totalResults: Int
    ) : SearchUiState

    /**
     * Empty state when no results are found for the query.
     *
     * @property query The search query that produced no results
     */
    data class Empty(val query: String) : SearchUiState

    /**
     * Error state when search fails.
     *
     * @property message The error message to display
     */
    data class Error(val message: String) : SearchUiState
}

/**
 * UI representation of a search result item.
 *
 * Follows a title-first approach where the conversation is the primary result,
 * with matching messages shown as context underneath.
 *
 * @property id Unique identifier for the result
 * @property conversationId The ID of the matching conversation
 * @property title The conversation title
 * @property icon Optional emoji icon for the conversation
 * @property matchType How the result matched (title, content, or both)
 * @property matchingMessages Preview snippets of matching messages
 * @property timestamp Human-readable relative time
 * @property modelName The AI model used in this conversation
 */
data class SearchResultUiItem(
    val id: String,
    val conversationId: String,
    val title: String,
    val icon: String?,
    val matchType: MatchType,
    val matchingMessages: List<MessageSnippet>,
    val timestamp: String,
    val modelName: String
)

/**
 * A snippet of a matching message for display in search results.
 *
 * @property id The message ID
 * @property role The role of the message sender (user/assistant)
 * @property snippet The context snippet with match highlighted
 * @property highlightedSnippet The snippet with match emphasis for display
 */
data class MessageSnippet(
    val id: String,
    val role: String,
    val snippet: String,
    val highlightedSnippet: AnnotatedString
)
