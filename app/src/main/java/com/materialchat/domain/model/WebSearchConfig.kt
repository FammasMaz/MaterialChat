package com.materialchat.domain.model

/**
 * Runtime configuration for web search (non-serializable).
 */
data class WebSearchConfig(
    val isEnabled: Boolean = false,
    val provider: WebSearchProvider = WebSearchProvider.EXA,
    val apiKey: String = "",
    val maxResults: Int = 5,
    val searxngBaseUrl: String = ""
)
