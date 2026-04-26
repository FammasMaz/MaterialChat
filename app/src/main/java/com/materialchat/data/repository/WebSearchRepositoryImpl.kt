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
        val resolvedConfig = if (
            config.provider == WebSearchProvider.EXA &&
            config.apiKey.isBlank() &&
            config.searxngBaseUrl.isNotBlank()
        ) {
            config.copy(provider = WebSearchProvider.SEARXNG)
        } else {
            config
        }

        val resultsResult = when (resolvedConfig.provider) {
            WebSearchProvider.EXA -> {
                webSearchApiClient.searchExa(
                    query = query,
                    apiKey = resolvedConfig.apiKey,
                    maxResults = resolvedConfig.maxResults
                )
            }
            WebSearchProvider.SEARXNG -> {
                webSearchApiClient.searchSearxng(
                    query = query,
                    baseUrl = resolvedConfig.searxngBaseUrl,
                    maxResults = resolvedConfig.maxResults
                )
            }
            WebSearchProvider.NATIVE -> {
                Result.failure(IllegalArgumentException("Native web search is handled by the chat provider"))
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        return resultsResult.map { results ->
            WebSearchMetadata(
                query = query,
                provider = resolvedConfig.provider,
                results = results,
                searchDurationMs = durationMs
            )
        }
    }
}
