package com.materialchat.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExaSearchRequest(
    val query: String,
    val numResults: Int = 5,
    val type: String = "auto",
    val contents: ExaContentsRequest = ExaContentsRequest()
)

@Serializable
data class ExaContentsRequest(
    val highlights: ExaHighlightsRequest = ExaHighlightsRequest()
)

@Serializable
data class ExaHighlightsRequest(
    val maxCharacters: Int = 1200
)

@Serializable
data class ExaSearchResponse(
    val results: List<ExaResultDto> = emptyList()
)

@Serializable
data class ExaResultDto(
    val url: String = "",
    val title: String? = null,
    val text: String? = null,
    val highlights: List<String>? = null,
    val publishedDate: String? = null,
    val score: Double? = null,
    val image: String? = null,
    val favicon: String? = null
)
