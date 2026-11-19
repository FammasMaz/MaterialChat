package com.materialchat.assistant.ui

import com.materialchat.assistant.voice.SpeechRecognitionErrors
import java.io.IOException

/**
 * The one copy system of the assistant window. Every phrase the pill, the notice surface and the
 * answer surface show comes from here, so tense, person and case cannot drift between states.
 *
 * Rules:
 * - Phase lines are a present participle with an ellipsis: "Listening…", "Thinking…", "Speaking…".
 *   The resting prompt is the imperative "Ask anything"; after a failure it is "Try again".
 * - Notices are one or two short sentences in sentence case, addressed to the user, with no
 *   "please", no exception text and no engine or API names. Each ends with a period. The last
 *   sentence names what the user can do; the notice's action chip offers the same thing.
 * - Action labels are sentence-case verbs without a trailing period ("Try again", "Type instead").
 *
 * Pure Kotlin so the JVM tests can check the mapping without a device.
 */
internal object AssistantCopy {
    const val PROMPT = "Ask anything"
    const val LISTENING = "Listening\u2026"
    const val THINKING = "Thinking\u2026"
    /** The reply is arriving (streaming): distinct from waiting for it. */
    const val ANSWERING = "Answering\u2026"
    const val SPEAKING = "Speaking\u2026"
    const val AFTER_ERROR = "Try again"

    /** The thinking surface inside the answer: the phase word without the pill's ellipsis, over one status line. */
    const val THINKING_TITLE = "Thinking"
    const val THINKING_STATUS = "Working on your answer"

    /** The answer was produced with the screen capture (the small marker on the answer surface). */
    const val USING_SCREEN = "Using screen"

    const val MIC_PERMISSION = "Microphone access is off. Allow it in system settings, or type your question."
    const val VOICE_DISABLED = "Voice input is off in settings. Type your question instead."
    const val RECOGNIZER_UNAVAILABLE = "Speech recognition is not available on this device. Type your question instead."
    const val NO_PROVIDER = "No AI provider is set up. Add one in the app."
    const val NO_CONNECTION = "No connection. Check your network and try again."
    /** HTTP 429 / quota exhaustion (Gemini, OpenAI, OpenRouter): retrying later is the right move. */
    const val RATE_LIMITED = "The model is busy right now. Wait a moment and try again."
    const val REQUEST_FAILED = "Something went wrong. Try again."

    /** Labelled actions of the answer surface and the opened pill. */
    const val OPEN_IN_APP = "Open in app"
    const val CONTINUE_IN_APP = "Continue in app"
    const val TAKE_PHOTO = "Take photo"
    const val ATTACH_PHOTO = "Attach photo"
    const val SCREEN_CHIP = "Screen"
    const val RECOGNITION_FAILED = "Speech recognition stopped early. Try again."

    /**
     * The notice for a recognizer failure, by the platform's error code. The recognizer's own
     * message (which names engines and says "please") never reaches the pill except for a code
     * this table does not know.
     */
    fun recognitionError(code: Int?, engineMessage: String): String = when (code) {
        SpeechRecognitionErrors.ERROR_NO_MATCH -> "Didn't catch that. Try again."
        SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
        SpeechRecognitionErrors.ERROR_NETWORK, SpeechRecognitionErrors.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a connection. Check your network and try again."
        SpeechRecognitionErrors.ERROR_AUDIO -> "The microphone could not be used. Try again."
        SpeechRecognitionErrors.ERROR_SERVER -> "The speech service did not respond. Try again."
        SpeechRecognitionErrors.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Try again in a moment."
        SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS -> MIC_PERMISSION
        // The manager reports a missing engine, a startup that never happened and a stop that
        // never finished under the same client code; only the first is not worth retrying.
        SpeechRecognitionErrors.ERROR_CLIENT ->
            if (engineMessage.contains("not available", ignoreCase = true)) RECOGNIZER_UNAVAILABLE
            else RECOGNITION_FAILED
        else -> engineMessage.ifBlank { RECOGNITION_FAILED }
    }

    /**
     * The notice for a failed request. Connectivity is named first (an [IOException] anywhere in
     * the cause chain). A provider's HTTP failure reaches the assistant as a plain exception
     * carrying the parsed error body (ChatRepositoryImpl wraps `StreamingEvent.Error.message`),
     * so the one state a user acts on differently is recognised from that text: a rate limit
     * (429, quota exhaustion) invites waiting before the retry. Everything else, including an
     * HTTP 401/403, stays generic (W2 pins that); the exception's own text never reaches the pill.
     */
    fun requestFailure(error: Throwable?): String = when {
        isConnectivity(error) -> NO_CONNECTION
        mentionsAny(error, RATE_LIMIT_MARKERS) -> RATE_LIMITED
        else -> REQUEST_FAILED
    }

    private val RATE_LIMIT_MARKERS = listOf(
        "429", "rate limit", "rate_limit", "ratelimit", "too many requests", "quota",
        "resource_exhausted", "resource has been exhausted"
    )

    private fun isConnectivity(error: Throwable?): Boolean = causes(error).any { it is IOException }

    private fun mentionsAny(error: Throwable?, markers: List<String>): Boolean =
        causes(error).any { cause ->
            val text = cause.message?.lowercase().orEmpty()
            text.isNotEmpty() && markers.any { it in text }
        }

    private fun causes(error: Throwable?): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var cause = error
        while (cause != null && chain.size < 6 && cause !in chain) {
            chain += cause
            cause = cause.cause
        }
        return chain
    }
}
