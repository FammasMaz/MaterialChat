package com.materialchat.data.repository

import com.materialchat.data.remote.api.WebSearchApiClient
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.model.WebSearchMetadata
import com.materialchat.domain.model.WebSearchProvider
import com.materialchat.domain.repository.WebSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchRepositoryImpl @Inject constructor(
    private val webSearchApiClient: WebSearchApiClient
) : WebSearchRepository {

    override suspend fun search(query: String, config: WebSearchConfig): Result<WebSearchMetadata> {
        val startTime = System.currentTimeMillis()

        val resultsResult = when (config.provider) {
            WebSearchProvider.EXA -> {
                webSearchApiClient.searchExa(
                    query = query,
                    apiKey = config.apiKey,
                    maxResults = config.maxResults
                )
            }
            WebSearchProvider.SEARXNG -> {
                webSearchApiClient.searchSearxng(
                    query = query,
                    baseUrl = config.searxngBaseUrl,
                    maxResults = config.maxResults
                )
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        return resultsResult.map { results ->
            WebSearchMetadata(
                query = query,
                provider = config.provider,
                results = results,
                searchDurationMs = durationMs
            )
        }
    }
}
