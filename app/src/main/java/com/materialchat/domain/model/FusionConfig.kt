package com.materialchat.domain.model

/**
 * Configuration for Response Fusion (Multi-Model Synthesis).
 *
 * @property selectedModels The list of models to query in parallel (2-3)
 * @property judgeModel The model used to synthesize individual responses
 * @property isEnabled Whether fusion mode is currently active
 */
data class FusionConfig(
    val selectedModels: List<FusionModelSelection> = emptyList(),
    val judgeModel: FusionModelSelection? = null,
    val isEnabled: Boolean = false
)

/**
 * A model selection for fusion, pairing a model name with its provider.
 *
 * @property modelName The model identifier (e.g., "gpt-4o")
 * @property providerId The provider that hosts this model
 */
data class FusionModelSelection(
    val modelName: String,
    val providerId: String
)
