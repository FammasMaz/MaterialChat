package com.materialchat.domain.model

/**
 * Persisted result payload for one contender slot in an N-model arena battle.
 */
data class ContenderResult(
    val slot: Int,
    val modelName: String,
    val providerId: String?,
    val response: String,
    val thinkingContent: String?,
    val durationMs: Long?
)
