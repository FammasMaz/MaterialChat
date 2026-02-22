package com.materialchat.domain.model

/**
 * Holds the runtime state of a fusion operation.
 *
 * @property individualResponses The responses from each model
 * @property synthesizedResponse The final synthesized response from the judge model
 * @property isSynthesizing Whether the judge model is currently synthesizing
 */
data class FusionResult(
    val individualResponses: List<FusionIndividualResponse> = emptyList(),
    val synthesizedResponse: String? = null,
    val isSynthesizing: Boolean = false
)

/**
 * An individual model's response during fusion.
 *
 * @property modelName The model that generated this response
 * @property providerId The provider that hosts the model
 * @property content The accumulated response content
 * @property isStreaming Whether this response is still streaming
 * @property durationMs The time taken to generate this response
 */
data class FusionIndividualResponse(
    val modelName: String,
    val providerId: String,
    val content: String,
    val isStreaming: Boolean = true,
    val durationMs: Long? = null
)
