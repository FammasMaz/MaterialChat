package com.materialchat.domain.usecase

import com.materialchat.domain.model.SearchQuery
import com.materialchat.domain.model.SearchResult
import com.materialchat.domain.repository.ConversationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for searching conversations by title and message content.
 *
 * Implements a title-first search approach where:
 * - Conversations are the primary search results
 * - Matching messages are shown branched under their parent conversation
 * - System messages are excluded from search
 * - Results are limited to a configurable maximum (default: 10)
 */
@Singleton
class SearchConversationsUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    /**
     * Searches for conversations matching the given query.
     *
     * @param query The search query string
     * @param limit Maximum number of results to return (default: 10)
     * @return List of search results, empty if query is blank or too short
     */
    suspend operator fun invoke(
        query: String,
        limit: Int = DEFAULT_LIMIT
    ): List<SearchResult> {
        // Require at least 2 characters to search
        if (query.isBlank() || query.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        val searchQuery = SearchQuery(
            text = query.trim(),
            searchInTitles = true,
            searchInContent = true,
            limit = limit
        )

        return conversationRepository.searchConversations(searchQuery)
    }

    companion object {
        /**
         * Default maximum number of search results.
         */
        const val DEFAULT_LIMIT = 10

        /**
         * Minimum query length required to perform a search.
         */
        const val MIN_QUERY_LENGTH = 2
    }
}
