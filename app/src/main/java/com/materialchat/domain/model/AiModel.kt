package com.materialchat.domain.model

/**
 * Represents an AI model available from a provider.
 *
 * @property id The unique identifier of the model (e.g., "gpt-4o", "llama3.2:latest")
 * @property name Display name for the model (often same as id)
 * @property providerId The ID of the provider that offers this model
 * @property contextWindow The maximum context window size in tokens (null if unknown)
 * @property maxOutputTokens The maximum output tokens the model can generate (null if unknown)
 * @property supportsThinking Whether the model supports extended thinking/reasoning
 * @property maxThinkingTokens Maximum tokens for thinking budget (null if not applicable)
 * @property supportsImages Whether the model supports image inputs
 * @property supportsTools Whether the model supports function/tool calling
 */
data class AiModel(
    val id: String,
    val name: String = id,
    val providerId: String,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsThinking: Boolean = false,
    val maxThinkingTokens: Int? = null,
    val supportsImages: Boolean = false,
    val supportsTools: Boolean = false
)
