package com.materialchat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearxngSearchResponse(
    val results: List<SearxngResultDto> = emptyList()
)

@Serializable
data class SearxngResultDto(
    val url: String = "",
    val title: String = "",
    val content: String? = null,
    @SerialName("img_src") val imgSrc: String? = null,
    val publishedDate: String? = null
)
