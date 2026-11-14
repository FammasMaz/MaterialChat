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
    /** BCP-47 language for speech input; null follows the device language. */
    val recognitionLanguageTag: String? = null,
    /** Recognizer service component; null picks the best installed engine automatically. */
    val recognitionEngine: String? = null,
    val speechRate: Float = 1f,
    /** Minutes within which invoking the assistant resumes its last chat; 0 always starts fresh. */
    val continueChatMinutes: Int = 0,
    // Intentionally no undocumented model/voice defaults; users supply supported identifiers.
    val geminiModel: String = "",
    val geminiVoice: String = ""
) {
    fun normalized() = copy(
        providerId = providerId?.trim()?.takeIf { it.isNotEmpty() },
        model = model?.trim()?.takeIf { it.isNotEmpty() },
        androidVoiceId = androidVoiceId?.trim()?.takeIf { it.isNotEmpty() },
        languageTag = languageTag?.trim()?.takeIf { it.isNotEmpty() },
        recognitionLanguageTag = recognitionLanguageTag?.trim()?.takeIf { it.isNotEmpty() },
        recognitionEngine = recognitionEngine?.trim()?.takeIf { it.isNotEmpty() },
        continueChatMinutes = continueChatMinutes.takeIf { it in CONTINUE_CHAT_CHOICES } ?: 0,
        speechRate = if (speechRate.isFinite()) speechRate.coerceIn(0.25f, 2f) else 1f,
        geminiModel = geminiModel.trim(),
        geminiVoice = geminiVoice.trim()
    )
}

enum class AssistantSpeechBackend { ANDROID, GEMINI }

/** Allowed "continue previous assistant chat" windows, in minutes; 0 = never. */
val CONTINUE_CHAT_CHOICES = listOf(0, 1, 5, 15, 60)

/** Whether a new invocation should resume the previous assistant chat. */
fun shouldContinueAssistantChat(continueChatMinutes: Int, lastActivityMillis: Long, nowMillis: Long): Boolean =
    continueChatMinutes > 0 && nowMillis - lastActivityMillis in 0..continueChatMinutes * 60_000L
