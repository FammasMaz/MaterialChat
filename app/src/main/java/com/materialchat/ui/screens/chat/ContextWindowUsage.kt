package com.materialchat.ui.screens.chat

import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import kotlin.math.roundToInt

/**
 * Approximate chat context usage for the selected model.
 *
 * The max-window fallback values mirror the shape of LiteLLM's
 * model_prices_and_context_window metadata when provider APIs do not report
 * context_length/max_input_tokens directly.
 */
data class ContextWindowUsage(
    val usedTokens: Int = 0,
    val maxTokens: Int? = null,
    val modelName: String = "",
    val isApproximate: Boolean = true
) {
    val fractionUsed: Float?
        get() = maxTokens?.takeIf { it > 0 }?.let { (usedTokens.toFloat() / it).coerceIn(0f, 1f) }

    val level: ContextWindowLevel
        get() = when (val fraction = fractionUsed) {
            null -> ContextWindowLevel.Unknown
            in 0f..0.70f -> ContextWindowLevel.Calm
            in 0.70f..0.86f -> ContextWindowLevel.Filling
            in 0.86f..0.95f -> ContextWindowLevel.High
            else -> ContextWindowLevel.Critical
        }
}

enum class ContextWindowLevel {
    Unknown,
    Calm,
    Filling,
    High,
    Critical
}

object ContextWindowEstimator {
    fun estimate(
        messages: List<Message>,
        draft: String,
        pendingAttachments: List<Attachment>,
        systemPrompt: String,
        modelName: String,
        explicitContextWindowTokens: Int? = null
    ): ContextWindowUsage {
        val systemTokens = estimatePromptTokens(systemPrompt)
        val messageTokens = messages.sumOf { estimateMessageTokens(it) }
        val draftTokens = estimateDraftTokens(draft, pendingAttachments)
        return ContextWindowUsage(
            usedTokens = systemTokens + messageTokens + draftTokens,
            maxTokens = explicitContextWindowTokens ?: inferContextWindowTokens(modelName),
            modelName = modelName
        )
    }

    fun estimateTokens(text: String): Int {
        val compact = text.trim()
        if (compact.isEmpty()) return 0
        val words = compact.split(Regex("\\s+")).size
        val charEstimate = (compact.length + 3) / 4
        val wordEstimate = (words * 1.3f).roundToInt()
        return maxOf(1, (charEstimate + wordEstimate + 1) / 2)
    }

    fun inferContextWindowTokens(modelName: String): Int? {
        val normalized = normalizedModelIds(modelName)
        normalized.firstNotNullOfOrNull { EXACT_CONTEXT_WINDOWS[it] }?.let { return it }
        val raw = modelName.lowercase()
        return when {
            raw.contains("gpt-4.1") -> 1_047_576
            raw.contains("gpt-5") -> 272_000
            raw.contains("gpt-4o") -> 128_000
            raw.contains(Regex("\\bo[34]")) -> 200_000
            raw.contains("gemini") && !raw.contains("nano") -> 1_048_576
            raw.contains("claude") -> 200_000
            raw.contains("grok-code-fast") -> 256_000
            raw.contains("llama-3.1") || raw.contains("llama3.1") -> 128_000
            raw.contains("llama-3.2") || raw.contains("llama3.2") -> 128_000
            raw.contains("qwen3") -> 128_000
            raw.contains("mistral-large") -> 128_000
            raw.contains("llama") -> 32_000
            raw.contains("qwen") -> 32_000
            raw.contains("mistral") -> 32_000
            else -> null
        }
    }

    private fun estimatePromptTokens(systemPrompt: String): Int {
        val tokens = estimateTokens(systemPrompt)
        return if (tokens == 0) 0 else tokens + MESSAGE_OVERHEAD_TOKENS
    }

    private fun estimateMessageTokens(message: Message): Int {
        return estimateTokens(message.content) +
            MESSAGE_OVERHEAD_TOKENS +
            (message.attachments.size * IMAGE_ATTACHMENT_TOKENS)
    }

    private fun estimateDraftTokens(draft: String, pendingAttachments: List<Attachment>): Int {
        val textTokens = estimateTokens(draft)
        val attachmentTokens = pendingAttachments.size * IMAGE_ATTACHMENT_TOKENS
        return if (textTokens == 0 && attachmentTokens == 0) 0 else {
            textTokens + MESSAGE_OVERHEAD_TOKENS + attachmentTokens
        }
    }

    private fun normalizedModelIds(modelName: String): List<String> {
        val raw = modelName.trim().lowercase()
        if (raw.isBlank()) return emptyList()
        val withoutProvider = raw.substringAfterLast('/')
        val withoutBedrockPrefix = withoutProvider.substringAfterLast('.')
        return listOf(raw, withoutProvider, withoutBedrockPrefix).distinct()
    }

    private val EXACT_CONTEXT_WINDOWS = mapOf(
        "gpt-4o" to 128_000,
        "gpt-4o-mini" to 128_000,
        "gpt-4.1" to 1_047_576,
        "gpt-4.1-mini" to 1_047_576,
        "gpt-4.1-nano" to 1_047_576,
        "gpt-5" to 272_000,
        "gpt-5-mini" to 272_000,
        "gpt-5-codex" to 272_000,
        "o3" to 200_000,
        "o4-mini" to 200_000,
        "gemini-2.5-pro" to 1_048_576,
        "gemini-2.5-flash" to 1_048_576,
        "gemini-2.5-flash-lite" to 1_048_576,
        "gemini-3-pro-preview" to 1_048_576,
        "gemini-3-flash-preview" to 1_048_576,
        "claude-haiku-4.5" to 200_000,
        "claude-sonnet-4.5" to 200_000,
        "claude-opus-4.5" to 200_000,
        "claude-sonnet-4.6" to 1_000_000,
        "claude-opus-4.6" to 1_000_000,
        "grok-code-fast-1" to 256_000,
        "xai/grok-code-fast-1" to 256_000
    )

    private const val MESSAGE_OVERHEAD_TOKENS = 4
    private const val IMAGE_ATTACHMENT_TOKENS = 765
}
