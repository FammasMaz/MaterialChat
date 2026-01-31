package com.materialchat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Antigravity API DTOs (Gemini-style format).
 *
 * Antigravity uses a format similar to Google's Gemini API, not OpenAI's format.
 * Key differences from OpenAI:
 * - `contents` array instead of `messages`
 * - Roles are `user`/`model` instead of `user`/`assistant`/`system`
 * - System prompt goes in `systemInstruction`, not in messages
 * - Response uses `candidates` with `content.parts` instead of `choices` with `delta`
 */

// ============================================================================
// Request DTOs
// ============================================================================

/**
 * Request body for Antigravity generateContent endpoint.
 */
@Serializable
data class AntigravityRequest(
    /**
     * The conversation contents (user and model turns).
     */
    val contents: List<AntigravityContent>,

    /**
     * System instruction for the model (separate from conversation).
     */
    @SerialName("system_instruction")
    val systemInstruction: AntigravitySystemInstruction? = null,

    /**
     * Generation configuration (temperature, max tokens, etc.).
     */
    @SerialName("generation_config")
    val generationConfig: AntigravityGenerationConfig? = null,

    /**
     * Safety settings for content filtering.
     */
    @SerialName("safety_settings")
    val safetySettings: List<AntigravitySafetySetting>? = null
)

/**
 * A single turn in the conversation (user or model).
 */
@Serializable
data class AntigravityContent(
    /**
     * Role of this content: "user" or "model".
     */
    val role: String,

    /**
     * The parts of this content (text, images, etc.).
     */
    val parts: List<AntigravityPart>
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}

/**
 * A part within content (text, inline data, etc.).
 */
@Serializable
data class AntigravityPart(
    /**
     * Text content.
     */
    val text: String? = null,

    /**
     * Inline data (for images, audio, etc.).
     */
    @SerialName("inline_data")
    val inlineData: AntigravityInlineData? = null,

    /**
     * Thinking/reasoning content (for extended thinking models).
     */
    val thought: Boolean? = null
)

/**
 * Inline binary data (images, audio, PDFs).
 */
@Serializable
data class AntigravityInlineData(
    /**
     * MIME type of the data (e.g., "image/png", "image/jpeg").
     */
    @SerialName("mime_type")
    val mimeType: String,

    /**
     * Base64-encoded data.
     */
    val data: String
)

/**
 * System instruction wrapper.
 */
@Serializable
data class AntigravitySystemInstruction(
    /**
     * Parts containing the system instruction text.
     */
    val parts: List<AntigravityPart>
) {
    companion object {
        /**
         * Creates a system instruction from text.
         */
        fun fromText(text: String): AntigravitySystemInstruction {
            return AntigravitySystemInstruction(
                parts = listOf(AntigravityPart(text = text))
            )
        }
    }
}

/**
 * Generation configuration parameters.
 */
@Serializable
data class AntigravityGenerationConfig(
    /**
     * Temperature for sampling (0.0 to 2.0).
     */
    val temperature: Float? = null,

    /**
     * Top-P sampling parameter.
     */
    @SerialName("top_p")
    val topP: Float? = null,

    /**
     * Top-K sampling parameter.
     */
    @SerialName("top_k")
    val topK: Int? = null,

    /**
     * Maximum number of output tokens.
     */
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,

    /**
     * Sequences that will stop generation.
     */
    @SerialName("stop_sequences")
    val stopSequences: List<String>? = null,

    /**
     * Response MIME type (e.g., "text/plain", "application/json").
     */
    @SerialName("response_mime_type")
    val responseMimeType: String? = null,

    /**
     * Enable extended thinking/reasoning.
     */
    @SerialName("thinking_config")
    val thinkingConfig: AntigravityThinkingConfig? = null
)

/**
 * Configuration for extended thinking models.
 */
@Serializable
data class AntigravityThinkingConfig(
    /**
     * Whether thinking is enabled.
     */
    @SerialName("thinking_budget")
    val thinkingBudget: Int? = null
)

/**
 * Safety setting for content filtering.
 */
@Serializable
data class AntigravitySafetySetting(
    /**
     * The harm category to configure.
     */
    val category: String,

    /**
     * The threshold for blocking content.
     */
    val threshold: String
) {
    companion object {
        // Harm categories
        const val HARM_CATEGORY_HARASSMENT = "HARM_CATEGORY_HARASSMENT"
        const val HARM_CATEGORY_HATE_SPEECH = "HARM_CATEGORY_HATE_SPEECH"
        const val HARM_CATEGORY_SEXUALLY_EXPLICIT = "HARM_CATEGORY_SEXUALLY_EXPLICIT"
        const val HARM_CATEGORY_DANGEROUS_CONTENT = "HARM_CATEGORY_DANGEROUS_CONTENT"

        // Thresholds
        const val BLOCK_NONE = "BLOCK_NONE"
        const val BLOCK_ONLY_HIGH = "BLOCK_ONLY_HIGH"
        const val BLOCK_MEDIUM_AND_ABOVE = "BLOCK_MEDIUM_AND_ABOVE"
        const val BLOCK_LOW_AND_ABOVE = "BLOCK_LOW_AND_ABOVE"

        /**
         * Default safety settings that allow most content (for coding assistance).
         */
        val PERMISSIVE_DEFAULTS = listOf(
            AntigravitySafetySetting(HARM_CATEGORY_HARASSMENT, BLOCK_ONLY_HIGH),
            AntigravitySafetySetting(HARM_CATEGORY_HATE_SPEECH, BLOCK_ONLY_HIGH),
            AntigravitySafetySetting(HARM_CATEGORY_SEXUALLY_EXPLICIT, BLOCK_ONLY_HIGH),
            AntigravitySafetySetting(HARM_CATEGORY_DANGEROUS_CONTENT, BLOCK_ONLY_HIGH)
        )
    }
}

// ============================================================================
// Response DTOs
// ============================================================================

/**
 * Response from Antigravity generateContent endpoint.
 */
@Serializable
data class AntigravityResponse(
    /**
     * The generated candidates (usually one).
     */
    val candidates: List<AntigravityCandidate>? = null,

    /**
     * Prompt feedback (safety ratings, blocking info).
     */
    @SerialName("prompt_feedback")
    val promptFeedback: AntigravityPromptFeedback? = null,

    /**
     * Usage metadata (token counts).
     */
    @SerialName("usage_metadata")
    val usageMetadata: AntigravityUsageMetadata? = null,

    /**
     * Model version used.
     */
    @SerialName("model_version")
    val modelVersion: String? = null
)

/**
 * A generated response candidate.
 */
@Serializable
data class AntigravityCandidate(
    /**
     * The generated content.
     */
    val content: AntigravityContent? = null,

    /**
     * Why generation stopped.
     */
    @SerialName("finish_reason")
    val finishReason: String? = null,

    /**
     * Safety ratings for the response.
     */
    @SerialName("safety_ratings")
    val safetyRatings: List<AntigravitySafetyRating>? = null,

    /**
     * Index of this candidate.
     */
    val index: Int? = null
) {
    companion object {
        // Finish reasons
        const val FINISH_REASON_STOP = "STOP"
        const val FINISH_REASON_MAX_TOKENS = "MAX_TOKENS"
        const val FINISH_REASON_SAFETY = "SAFETY"
        const val FINISH_REASON_RECITATION = "RECITATION"
        const val FINISH_REASON_OTHER = "OTHER"
    }
}

/**
 * Safety rating for generated content.
 */
@Serializable
data class AntigravitySafetyRating(
    /**
     * The harm category evaluated.
     */
    val category: String,

    /**
     * The probability of harm.
     */
    val probability: String,

    /**
     * Whether this content was blocked.
     */
    val blocked: Boolean? = null
)

/**
 * Feedback about the prompt (safety evaluation).
 */
@Serializable
data class AntigravityPromptFeedback(
    /**
     * Reason the prompt was blocked (if applicable).
     */
    @SerialName("block_reason")
    val blockReason: String? = null,

    /**
     * Safety ratings for the prompt.
     */
    @SerialName("safety_ratings")
    val safetyRatings: List<AntigravitySafetyRating>? = null
)

/**
 * Token usage metadata.
 */
@Serializable
data class AntigravityUsageMetadata(
    /**
     * Tokens used for the prompt.
     */
    @SerialName("prompt_token_count")
    val promptTokenCount: Int? = null,

    /**
     * Tokens used for the response.
     */
    @SerialName("candidates_token_count")
    val candidatesTokenCount: Int? = null,

    /**
     * Total tokens used.
     */
    @SerialName("total_token_count")
    val totalTokenCount: Int? = null,

    /**
     * Tokens used for thinking/reasoning.
     */
    @SerialName("thoughts_token_count")
    val thoughtsTokenCount: Int? = null
)

// ============================================================================
// Streaming Response DTOs
// ============================================================================

/**
 * Streaming chunk from Antigravity streamGenerateContent endpoint.
 *
 * SSE format: `data: {"candidates": [...], ...}`
 */
@Serializable
data class AntigravityStreamChunk(
    /**
     * Partial candidates with incremental content.
     */
    val candidates: List<AntigravityCandidate>? = null,

    /**
     * Usage metadata (may only appear in final chunk).
     */
    @SerialName("usage_metadata")
    val usageMetadata: AntigravityUsageMetadata? = null,

    /**
     * Model version (may only appear in final chunk).
     */
    @SerialName("model_version")
    val modelVersion: String? = null
)

// ============================================================================
// Error Response DTOs
// ============================================================================

/**
 * Error response from Antigravity API.
 */
@Serializable
data class AntigravityErrorResponse(
    /**
     * Error details.
     */
    val error: AntigravityError? = null
)

/**
 * Error details.
 */
@Serializable
data class AntigravityError(
    /**
     * HTTP status code.
     */
    val code: Int? = null,

    /**
     * Human-readable error message.
     */
    val message: String? = null,

    /**
     * Error status string (e.g., "INVALID_ARGUMENT").
     */
    val status: String? = null,

    /**
     * Additional error details.
     */
    val details: List<AntigravityErrorDetail>? = null
)

/**
 * Additional error detail.
 */
@Serializable
data class AntigravityErrorDetail(
    /**
     * Type URL for the error detail.
     */
    @SerialName("@type")
    val type: String? = null,

    /**
     * Reason code.
     */
    val reason: String? = null,

    /**
     * Domain of the error.
     */
    val domain: String? = null,

    /**
     * Metadata about the error.
     */
    val metadata: Map<String, String>? = null
)

// ============================================================================
// Conversion Helpers
// ============================================================================

/**
 * Extension functions for converting between OpenAI and Antigravity formats.
 */
object AntigravityConverters {

    /**
     * Converts an OpenAI role to Antigravity role.
     */
    fun convertRole(openAiRole: String): String {
        return when (openAiRole) {
            "user" -> AntigravityContent.ROLE_USER
            "assistant" -> AntigravityContent.ROLE_MODEL
            else -> AntigravityContent.ROLE_USER
        }
    }

    /**
     * Converts OpenAI content to Antigravity parts.
     */
    fun convertContent(content: OpenAiContent): List<AntigravityPart> {
        return when (content) {
            is OpenAiContent.Text -> listOf(AntigravityPart(text = content.text))
            is OpenAiContent.Parts -> content.parts.mapNotNull { part ->
                when (part) {
                    is OpenAiContentPart.TextPart -> AntigravityPart(text = part.text)
                    is OpenAiContentPart.ImageUrlPart -> {
                        // Convert data URL to inline data
                        val url = part.imageUrl.url
                        if (url.startsWith("data:")) {
                            parseDataUrl(url)
                        } else {
                            // Antigravity doesn't support external URLs directly
                            null
                        }
                    }
                }
            }
        }
    }

    /**
     * Parses a data URL into AntigravityPart with inline data.
     */
    private fun parseDataUrl(dataUrl: String): AntigravityPart? {
        // Format: data:image/png;base64,<base64data>
        val regex = Regex("^data:([^;]+);base64,(.+)$")
        val match = regex.find(dataUrl) ?: return null

        val mimeType = match.groupValues[1]
        val base64Data = match.groupValues[2]

        return AntigravityPart(
            inlineData = AntigravityInlineData(
                mimeType = mimeType,
                data = base64Data
            )
        )
    }

    /**
     * Converts a list of OpenAI messages to Antigravity contents.
     * Filters out system messages (they go in systemInstruction).
     */
    fun convertMessages(messages: List<OpenAiMessage>): List<AntigravityContent> {
        return messages
            .filter { it.role != "system" }
            .map { message ->
                AntigravityContent(
                    role = convertRole(message.role),
                    parts = convertContent(message.content)
                )
            }
    }

    /**
     * Extracts system message from OpenAI messages.
     */
    fun extractSystemInstruction(messages: List<OpenAiMessage>): AntigravitySystemInstruction? {
        val systemMessages = messages.filter { it.role == "system" }
        if (systemMessages.isEmpty()) return null

        val combinedText = systemMessages.joinToString("\n\n") { message ->
            when (val content = message.content) {
                is OpenAiContent.Text -> content.text
                is OpenAiContent.Parts -> content.parts
                    .filterIsInstance<OpenAiContentPart.TextPart>()
                    .joinToString("\n") { it.text }
            }
        }

        return AntigravitySystemInstruction.fromText(combinedText)
    }
}
