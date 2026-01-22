package com.materialchat.ui.screens.search

import android.text.format.DateUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.MessageMatch
import com.materialchat.domain.model.SearchResult
import com.materialchat.domain.usecase.SearchConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the search functionality.
 *
 * Handles search query input, debouncing, and result transformation for the UI.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchConversationsUseCase: SearchConversationsUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeSearchQuery()
    }

    /**
     * Observes search query changes with debouncing to avoid excessive searches.
     */
    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        _searchQuery
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.length < MIN_QUERY_LENGTH) {
                    _uiState.value = SearchUiState.Idle
                } else {
                    performSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Updates the search query.
     */
    fun onQueryChange(query: String) {
        _searchQuery.value = query
        if (query.length >= MIN_QUERY_LENGTH) {
            _uiState.value = SearchUiState.Loading
        }
    }

    /**
     * Clears the search query and resets state.
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.value = SearchUiState.Idle
        searchJob?.cancel()
    }

    /**
     * Performs the search operation.
     */
    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading

            try {
                val results = searchConversationsUseCase(query, MAX_RESULTS)

                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty(query)
                } else {
                    SearchUiState.Results(
                        query = query,
                        results = results.map { it.toUiItem(query) },
                        totalResults = results.size
                    )
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(
                    message = e.message ?: "Search failed"
                )
            }
        }
    }

    /**
     * Converts a domain SearchResult to a UI item.
     */
    private fun SearchResult.toUiItem(query: String): SearchResultUiItem {
        return SearchResultUiItem(
            id = conversation.id,
            conversationId = conversation.id,
            title = conversation.title,
            icon = conversation.icon,
            matchType = matchType,
            matchingMessages = matchingMessages.map { it.toSnippet(query) },
            timestamp = formatRelativeTime(conversation.updatedAt),
            modelName = conversation.modelName
        )
    }

    /**
     * Converts a MessageMatch to a UI snippet with highlighting.
     */
    private fun MessageMatch.toSnippet(query: String): MessageSnippet {
        return MessageSnippet(
            id = message.id,
            role = message.role.name.lowercase().replaceFirstChar { it.uppercase() },
            snippet = contextSnippet,
            highlightedSnippet = highlightMatch(contextSnippet, query)
        )
    }

    /**
     * Creates an AnnotatedString with the query match highlighted.
     */
    private fun highlightMatch(text: String, query: String): AnnotatedString {
        return buildAnnotatedString {
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            var currentIndex = 0

            while (currentIndex < text.length) {
                val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
                if (matchIndex == -1) {
                    // No more matches, append the rest
                    append(text.substring(currentIndex))
                    break
                }

                // Append text before the match
                if (matchIndex > currentIndex) {
                    append(text.substring(currentIndex, matchIndex))
                }

                // Append the match with highlighting
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        background = Color(0x40FFEB3B) // Yellow highlight
                    )
                ) {
                    append(text.substring(matchIndex, matchIndex + query.length))
                }

                currentIndex = matchIndex + query.length
            }
        }
    }

    /**
     * Formats a timestamp to a human-readable relative time string.
     */
    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_RESULTS = 10
    }
}
