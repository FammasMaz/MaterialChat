package com.materialchat.data.local.preferences

import com.materialchat.domain.model.ReasoningEffort

/** Null provider/model/reasoning inherit the assistant's normal chat configuration. */
data class AssistantVoiceSettings(
    val providerId: String? = null,
    val model: String? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val backend: AssistantSpeechBackend = AssistantSpeechBackend.ANDROID,
    val androidVoiceId: String? = null,
    val languageTag: String? = null,
    val speechRate: Float = 1f,
    // Intentionally no undocumented model/voice defaults; users supply supported identifiers.
    val geminiModel: String = "",
    val geminiVoice: String = ""
) {
    fun normalized() = copy(
        providerId = providerId?.trim()?.takeIf { it.isNotEmpty() },
        model = model?.trim()?.takeIf { it.isNotEmpty() },
        androidVoiceId = androidVoiceId?.trim()?.takeIf { it.isNotEmpty() },
        languageTag = languageTag?.trim()?.takeIf { it.isNotEmpty() },
        speechRate = if (speechRate.isFinite()) speechRate.coerceIn(0.25f, 2f) else 1f,
        geminiModel = geminiModel.trim(),
        geminiVoice = geminiVoice.trim()
    )
}

enum class AssistantSpeechBackend { ANDROID, GEMINI }
