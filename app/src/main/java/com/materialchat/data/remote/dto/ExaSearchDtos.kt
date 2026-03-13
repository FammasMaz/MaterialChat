package com.materialchat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExaSearchRequest(
    val query: String,
    @SerialName("num_results") val numResults: Int = 5,
    val type: String = "auto",
    val contents: ExaContentsRequest = ExaContentsRequest()
)

@Serializable
data class ExaContentsRequest(
    val highlights: ExaHighlightsRequest = ExaHighlightsRequest()
)

@Serializable
data class ExaHighlightsRequest(
    @SerialName("num_sentences") val numSentences: Int = 3
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
    val image: String? = null
)
