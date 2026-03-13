package com.materialchat.domain.model

import kotlinx.serialization.Serializable

/**
 * Serializable metadata stored with a message that used web search.
 * Persisted as JSON in the web_search_metadata column.
 */
@Serializable
data class WebSearchMetadata(
    val query: String,
    val provider: WebSearchProvider,
    val results: List<WebSearchResult>,
    val searchDurationMs: Long? = null
)

/**
 * A single web search result with citation index.
 */
@Serializable
data class WebSearchResult(
    val index: Int,
    val url: String,
    val title: String,
    val snippet: String,
    val imageUrl: String? = null,
    val faviconUrl: String? = null,
    val publishedDate: String? = null,
    val domain: String? = null
)

@Serializable
enum class WebSearchProvider { EXA, SEARXNG }
