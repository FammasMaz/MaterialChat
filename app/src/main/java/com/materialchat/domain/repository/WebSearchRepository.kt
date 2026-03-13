package com.materialchat.domain.repository

import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.model.WebSearchMetadata

/**
 * Repository interface for web search operations.
 */
interface WebSearchRepository {
    /**
     * Performs a web search using the configured provider.
     *
     * @param query The search query
     * @param config The web search configuration
     * @return Result containing search metadata or an error
     */
    suspend fun search(query: String, config: WebSearchConfig): Result<WebSearchMetadata>
}
