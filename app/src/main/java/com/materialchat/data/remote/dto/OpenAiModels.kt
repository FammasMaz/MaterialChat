package com.materialchat.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

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
 * Supports both text-only messages (content as String) and multimodal messages (content as array).
 */
@Serializable
data class OpenAiMessage(
    val role: String,
    val content: OpenAiContent
)

/**
 * Content for OpenAI messages - can be either a simple string or an array of content parts.
 * This uses a custom serializer to handle the polymorphic nature of the content field.
 */
@Serializable(with = OpenAiContentSerializer::class)
sealed class OpenAiContent {
    /**
     * Simple text content (for text-only messages).
     */
    @Serializable
    data class Text(val text: String) : OpenAiContent()

    /**
     * Array of content parts (for multimodal messages with images).
     */
    @Serializable
    data class Parts(val parts: List<OpenAiContentPart>) : OpenAiContent()
}

/**
 * Individual content part in a multimodal message.
 */
@Serializable
sealed class OpenAiContentPart {
    /**
     * Text content part.
     */
    @Serializable
    @SerialName("text")
    data class TextPart(
        val type: String = "text",
        val text: String
    ) : OpenAiContentPart()

    /**
     * Image URL content part (supports base64 data URLs).
     */
    @Serializable
    @SerialName("image_url")
    data class ImageUrlPart(
        val type: String = "image_url",
        @SerialName("image_url")
        val imageUrl: ImageUrl
    ) : OpenAiContentPart()
}

/**
 * Image URL data for OpenAI vision API.
 */
@Serializable
data class ImageUrl(
    val url: String,
    val detail: String = "auto"
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
    val content: String? = null,
    // Reasoning/thinking fields used by various providers
    val reasoning: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    val thinking: String? = null
)

/**
 * Delta (partial content) in a streaming response.
 */
@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    // Reasoning/thinking fields used by various providers
    val reasoning: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    val thinking: String? = null
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

// ============================================================================
// Custom Serializer for OpenAI Content
// ============================================================================

/**
 * Custom serializer for OpenAiContent that handles both string and array formats.
 *
 * OpenAI API accepts content in two formats:
 * - Simple string: "content": "Hello"
 * - Array of parts: "content": [{"type": "text", "text": "..."}, {"type": "image_url", ...}]
 */
object OpenAiContentSerializer : KSerializer<OpenAiContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OpenAiContent")

    override fun serialize(encoder: Encoder, value: OpenAiContent) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("OpenAiContent can only be serialized to JSON")

        val element: JsonElement = when (value) {
            is OpenAiContent.Text -> JsonPrimitive(value.text)
            is OpenAiContent.Parts -> {
                val partsList = value.parts.map { part ->
                    when (part) {
                        is OpenAiContentPart.TextPart -> {
                            kotlinx.serialization.json.buildJsonObject {
                                put("type", JsonPrimitive("text"))
                                put("text", JsonPrimitive(part.text))
                            }
                        }
                        is OpenAiContentPart.ImageUrlPart -> {
                            kotlinx.serialization.json.buildJsonObject {
                                put("type", JsonPrimitive("image_url"))
                                put("image_url", kotlinx.serialization.json.buildJsonObject {
                                    put("url", JsonPrimitive(part.imageUrl.url))
                                    put("detail", JsonPrimitive(part.imageUrl.detail))
                                })
                            }
                        }
                    }
                }
                JsonArray(partsList)
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): OpenAiContent {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("OpenAiContent can only be deserialized from JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> OpenAiContent.Text(element.content)
            is JsonArray -> {
                // For now, we don't need to deserialize array content (only used for requests)
                OpenAiContent.Text("")
            }
            else -> OpenAiContent.Text("")
        }
    }
}

/**
 * Serializer for OpenAiContentPart sealed class.
 */
object OpenAiContentPartSerializer : KSerializer<OpenAiContentPart> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OpenAiContentPart")

    override fun serialize(encoder: Encoder, value: OpenAiContentPart) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("OpenAiContentPart can only be serialized to JSON")

        val element = when (value) {
            is OpenAiContentPart.TextPart -> {
                kotlinx.serialization.json.buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(value.text))
                }
            }
            is OpenAiContentPart.ImageUrlPart -> {
                kotlinx.serialization.json.buildJsonObject {
                    put("type", JsonPrimitive("image_url"))
                    put("image_url", kotlinx.serialization.json.buildJsonObject {
                        put("url", JsonPrimitive(value.imageUrl.url))
                        put("detail", JsonPrimitive(value.imageUrl.detail))
                    })
                }
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): OpenAiContentPart {
        // Not needed for our use case (only used for requests)
        return OpenAiContentPart.TextPart(text = "")
    }
}
