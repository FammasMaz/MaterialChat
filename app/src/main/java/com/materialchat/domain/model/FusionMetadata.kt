package com.materialchat.domain.model

import kotlinx.serialization.Serializable

/**
 * Serializable metadata stored with a fused message.
 * Persisted as JSON in the fusion_metadata column.
 *
 * @property sources The individual model responses that were synthesized
 * @property judgeModel The model used to perform the synthesis
 */
@Serializable
data class FusionMetadata(
    val sources: List<FusionSource>,
    val judgeModel: String? = null
)

/**
 * A single source response within fused metadata.
 *
 * @property modelName The model that generated this source
 * @property content The full response content from this model
 * @property durationMs How long this model took to respond
 */
@Serializable
data class FusionSource(
    val modelName: String,
    val content: String,
    val durationMs: Long? = null
)
