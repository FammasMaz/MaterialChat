package com.materialchat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ollama API DTOs.
 * These models work with the Ollama local LLM server.
 */

// ============================================================================
// Chat Request/Response
// ============================================================================

/**
 * Request body for POST /api/chat
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
    val think: Boolean = false,
    val options: OllamaOptions? = null
)

/**
 * Message in Ollama format.
 */
@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

/**
 * Generation options for Ollama.
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    val seed: Int? = null
)

/**
 * Streaming response chunk from POST /api/chat (NDJSON format).
 * Each line is a complete JSON object.
 *
 * Example:
 * {"model":"llama3.2","message":{"role":"assistant","content":"Hi"},"done":false}
 */
@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: OllamaResponseMessage? = null,
    val done: Boolean = false,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("eval_duration")
    val evalDuration: Long? = null
)

/**
 * Message in Ollama response.
 * Supports thinking content for reasoning models.
 */
@Serializable
data class OllamaResponseMessage(
    val role: String? = null,
    val content: String? = null,
    val thinking: String? = null
)

// ============================================================================
// Models List
// ============================================================================

/**
 * Response from GET /api/tags
 */
@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModelInfo> = emptyList()
)

/**
 * Individual model info from models list.
 */
@Serializable
data class OllamaModelInfo(
    val name: String,
    val model: String? = null,
    @SerialName("modified_at")
    val modifiedAt: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: OllamaModelDetails? = null
)

/**
 * Model details.
 */
@Serializable
data class OllamaModelDetails(
    @SerialName("parent_model")
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    @SerialName("parameter_size")
    val parameterSize: String? = null,
    @SerialName("quantization_level")
    val quantizationLevel: String? = null
)

// ============================================================================
// Generate Endpoint (Alternative to Chat)
// ============================================================================

/**
 * Request body for POST /api/generate (simpler endpoint).
 */
@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
    val system: String? = null,
    val options: OllamaOptions? = null
)

/**
 * Response from POST /api/generate.
 */
@Serializable
data class OllamaGenerateResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val response: String? = null,
    val done: Boolean = false,
    val context: List<Int>? = null,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("eval_duration")
    val evalDuration: Long? = null
)

// ============================================================================
// Error Response
// ============================================================================

/**
 * Error response from Ollama API.
 */
@Serializable
data class OllamaErrorResponse(
    val error: String? = null
)
