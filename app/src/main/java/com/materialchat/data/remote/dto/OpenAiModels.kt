package com.materialchat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible API DTOs.
 * These models work with OpenAI, Groq, Together, and other compatible APIs.
 */

// ============================================================================
// Chat Completion Request/Response
// ============================================================================

/**
 * Request body for POST /v1/chat/completions
 */
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

/**
 * Message in OpenAI format.
 */
@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

/**
 * Non-streaming response from POST /v1/chat/completions
 */
@Serializable
data class OpenAiChatResponse(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

/**
 * Choice in a chat completion response.
 */
@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiResponseMessage? = null,
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * Full message in a non-streaming response.
 */
@Serializable
data class OpenAiResponseMessage(
    val role: String? = null,
    val content: String? = null
)

/**
 * Delta (partial content) in a streaming response.
 */
@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null
)

/**
 * Token usage statistics.
 */
@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

// ============================================================================
// Streaming Chunk
// ============================================================================

/**
 * SSE chunk from streaming chat completion.
 * Each chunk contains partial response data.
 *
 * Example SSE line:
 * data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}
 */
@Serializable
data class OpenAiStreamChunk(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<OpenAiChoice> = emptyList()
)

// ============================================================================
// Models List
// ============================================================================

/**
 * Response from GET /v1/models
 */
@Serializable
data class OpenAiModelsResponse(
    val `object`: String? = null,
    val data: List<OpenAiModelData> = emptyList()
)

/**
 * Individual model info from models list.
 */
@Serializable
data class OpenAiModelData(
    val id: String,
    val `object`: String? = null,
    val created: Long? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null
)

// ============================================================================
// Error Response
// ============================================================================

/**
 * Error response from OpenAI API.
 */
@Serializable
data class OpenAiErrorResponse(
    val error: OpenAiError? = null
)

/**
 * Error details.
 */
@Serializable
data class OpenAiError(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)
