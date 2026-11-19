package com.materialchat.assistant.ui

import com.materialchat.assistant.voice.SpeechRecognitionErrors
import com.materialchat.assistant.voice.VoiceState

/** The one action a notice can offer. Every label is a sentence-case verb (see [AssistantCopy]). */
internal enum class AssistantNoticeAction(val label: String) {
    /** Re-send the question the failure interrupted. */
    RetryRequest("Try again"),
    /** Open the microphone again. */
    RetryListening("Try again"),
    /** Switch the pill to keyboard mode. */
    TypeInstead("Type instead"),
    /** Open the main app (the provider list lives in its settings). */
    OpenApp("Open app"),
    /** Open the app's settings (the provider's key): the provider rejected the request. */
    OpenSettings("Open settings"),
    /** Open the system's digital-assistant settings (screen context). */
    ScreenSettings("Open assistant settings")
}

/** What the notice line shows: its message, whether it is a failure, and its single action. */
internal data class AssistantNotice(
    val message: String,
    val isError: Boolean,
    val action: AssistantNoticeAction? = null
) {
    /** The pill's state line while this notice is up and nothing else is happening. */
    val restingLine: String
        get() = if (isError && (action == AssistantNoticeAction.RetryListening ||
                action == AssistantNoticeAction.RetryRequest)) AssistantCopy.AFTER_ERROR
        else AssistantCopy.PROMPT
}

/**
 * Resolves the one notice the window shows from the ViewModel's state. Precedence, highest first:
 * a failed request or failed speech output, a recognizer failure, an informational notice, the
 * screen that could not be read, then the two standing limits (microphone permission, voice input
 * off). Pure Kotlin so the JVM test can enumerate every path.
 */
internal object AssistantNotices {
    fun resolve(
        error: String?,
        notice: String?,
        voiceError: VoiceState.Error?,
        hasQuestion: Boolean,
        screenContextUnavailable: Boolean,
        screenUnavailableNotice: String,
        hasMicPermission: Boolean,
        voiceInputEnabled: Boolean,
        keyboardMode: Boolean
    ): AssistantNotice? {
        val typeInstead = if (keyboardMode) null else AssistantNoticeAction.TypeInstead
        return when {
            error != null -> AssistantNotice(
                message = error,
                isError = true,
                action = when {
                    error == AssistantCopy.NO_PROVIDER -> AssistantNoticeAction.OpenApp
                    // A rejected key is fixed in settings, not by sending the question again.
                    error == AssistantCopy.PROVIDER_REJECTED -> AssistantNoticeAction.OpenSettings
                    // handleError mirrors the message into the voice state; a speech-output
                    // failure does not, and the answer it interrupted is already on screen.
                    voiceError != null && voiceError.message == error && hasQuestion ->
                        AssistantNoticeAction.RetryRequest
                    else -> null
                }
            )
            voiceError != null -> AssistantNotice(
                message = voiceError.message,
                isError = true,
                action = when {
                    voiceError.errorCode == SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS ||
                        voiceError.message == AssistantCopy.MIC_PERMISSION ||
                        voiceError.message == AssistantCopy.RECOGNIZER_UNAVAILABLE -> typeInstead
                    else -> AssistantNoticeAction.RetryListening
                }
            )
            notice != null -> AssistantNotice(message = notice, isError = false)
            screenContextUnavailable -> AssistantNotice(
                message = screenUnavailableNotice,
                isError = false,
                action = AssistantNoticeAction.ScreenSettings
            )
            !hasMicPermission -> AssistantNotice(AssistantCopy.MIC_PERMISSION, isError = false, action = typeInstead)
            !voiceInputEnabled -> AssistantNotice(AssistantCopy.VOICE_DISABLED, isError = false, action = typeInstead)
            else -> null
        }
    }
}
