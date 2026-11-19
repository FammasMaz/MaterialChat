package com.materialchat.assistant.ui

import com.materialchat.assistant.voice.GeminiTtsFailure
import com.materialchat.assistant.voice.SpeechRecognitionErrors
import com.materialchat.assistant.voice.VoiceCopy
import java.io.IOException

/**
 * The one copy system of the assistant window. Every phrase the pill, the notice surface and the
 * answer surface show comes from here, so tense, person and case cannot drift between states.
 *
 * Rules:
 * - Phase lines are a present participle with an ellipsis: "Listening…", "Thinking…", "Speaking…".
 *   The resting prompt is the imperative "Ask anything"; after a failure it is [ERROR_VERB].
 * - The error verb is "Try again" (W5 orchestrator ruling), never "Retry": in the resting line,
 *   in every action label and inside every notice sentence ("... and try again.").
 * - Period rule (W5 orchestrator ruling): a notice is one or more complete sentences and every
 *   sentence ends with a full stop; a phrase (a phase line, a status line, a marker, a chip
 *   label) is not a sentence and carries no period. Notices are sentence case, addressed to the
 *   user, with no "please", no exception text and no engine or API names. The last sentence
 *   names what the user can do; the notice's action chip offers the same thing.
 * - Action labels are sentence-case verbs without a trailing period ("Try again", "Type instead").
 * - The voice layer's own sentences (recognizer codes, speech output, voice fallbacks, neural
 *   model installs) live in [VoiceCopy] under the same rules; the recognizer sentences are
 *   aliased here and every voice sentence is listed in [VOICE_NOTICES] for the rule test (W6).
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
    /** The one verb for trying a failed thing again. Chip labels and the resting line use it as is. */
    const val ERROR_VERB = "Try again"
    const val AFTER_ERROR = ERROR_VERB

    /** The thinking surface inside the answer: the phase word without the pill's ellipsis, over one status line. */
    const val THINKING_TITLE = "Thinking"
    const val THINKING_STATUS = "Working on your answer"

    /** The answer was produced with the screen capture (the small marker on the answer surface). */
    const val USING_SCREEN = "Using screen"

    const val MIC_PERMISSION = VoiceCopy.MIC_PERMISSION
    const val VOICE_DISABLED = "Voice input is off in settings. Type your question instead."
    const val RECOGNIZER_UNAVAILABLE = VoiceCopy.RECOGNIZER_UNAVAILABLE
    const val NO_PROVIDER = "No AI provider is set up. Add one in the app."
    const val NO_CONNECTION = "No connection. Check your network and try again."
    /** HTTP 429 / quota exhaustion (Gemini, OpenAI, OpenRouter): retrying later is the right move. */
    const val RATE_LIMITED = "The model is busy right now. Wait a moment and try again."
    /**
     * HTTP 401 / 403: the provider refused the key or the account. Retrying changes nothing;
     * the key lives in the app's provider settings, which the notice's action opens.
     */
    const val PROVIDER_REJECTED = "The provider rejected the request. Check the API key in settings."
    const val REQUEST_FAILED = "Something went wrong. Try again."

    /** Labelled actions of the answer surface and the opened pill. */
    const val OPEN_IN_APP = "Open in app"
    const val CONTINUE_IN_APP = "Continue in app"
    const val TAKE_PHOTO = "Take photo"
    const val ATTACH_PHOTO = "Attach photo"
    const val SCREEN_CHIP = "Screen"
    const val RECOGNITION_FAILED = VoiceCopy.RECOGNITION_FAILED

    /** Every fixed notice (sentences: full stops), for the copy-rule test. */
    val NOTICES: List<String> = listOf(
        MIC_PERMISSION, VOICE_DISABLED, RECOGNIZER_UNAVAILABLE, NO_PROVIDER, NO_CONNECTION,
        RATE_LIMITED, PROVIDER_REJECTED, REQUEST_FAILED, RECOGNITION_FAILED
    )

    /** Every fixed phrase (not a sentence: no period), for the copy-rule test. */
    val PHRASES: List<String> = listOf(
        PROMPT, LISTENING, THINKING, ANSWERING, SPEAKING, ERROR_VERB, THINKING_TITLE, THINKING_STATUS,
        USING_SCREEN, OPEN_IN_APP, CONTINUE_IN_APP, TAKE_PHOTO, ATTACH_PHOTO, SCREEN_CHIP
    )

    /**
     * Every voice-layer sentence ([VoiceCopy.samples]) plus the Gemini diagnostics, for the
     * copy-rule test: full stops, sentence case, no "please", never "retry".
     */
    val VOICE_NOTICES: List<String> = VoiceCopy.samples() + GeminiTtsFailure.entries.map { it.diagnostic }

    /**
     * The notice for a recognizer failure, by the platform's error code: the voice layer's one
     * table ([VoiceCopy.recognition]). The recognizer's own message (which names engines and
     * says "please") never reaches the pill except for a code the table does not know.
     */
    fun recognitionError(code: Int?, engineMessage: String): String = when (code) {
        // The manager reports a missing engine, a startup that never happened and a stop that
        // never finished under the same client code; only the first is not worth retrying.
        SpeechRecognitionErrors.ERROR_CLIENT ->
            if (engineMessage.contains("not available", ignoreCase = true)) RECOGNIZER_UNAVAILABLE
            else RECOGNITION_FAILED
        else -> code?.let(VoiceCopy::recognition) ?: engineMessage.ifBlank { RECOGNITION_FAILED }
    }

    /**
     * The notice for a failed request. Connectivity is named first (an [IOException] anywhere in
     * the cause chain). Then the typed HTTP status, when the stream delivered one ([httpStatus],
     * the W4 chat delegate's `StreamingState.Error.httpStatus`): 401 and 403 are the provider
     * rejecting the key or account ([PROVIDER_REJECTED], action: open the provider settings),
     * 429 is a rate limit ([RATE_LIMITED], wait before the retry). Without a typed status the
     * provider's failure is only a plain exception carrying the parsed error body
     * (ChatRepositoryImpl wraps `StreamingEvent.Error.message`), and the one state recognised
     * from that text is the rate limit (429, quota exhaustion). A 401/403 is NEVER recognised
     * from text: "HTTP 401" in a body stays generic (W2 pins that), so a provider echoing a
     * status in an unrelated message cannot send the user to the key settings by accident.
     * The exception's own text never reaches the pill.
     */
    fun requestFailure(error: Throwable?, httpStatus: Int? = null): String = when {
        isConnectivity(error) -> NO_CONNECTION
        httpStatus in PROVIDER_REJECTED_STATUSES -> PROVIDER_REJECTED
        httpStatus == HTTP_TOO_MANY_REQUESTS -> RATE_LIMITED
        mentionsAny(error, RATE_LIMIT_MARKERS) -> RATE_LIMITED
        else -> REQUEST_FAILED
    }

    /** The statuses that mean "your key or account", not "try again". */
    private val PROVIDER_REJECTED_STATUSES = setOf(401, 403)
    private const val HTTP_TOO_MANY_REQUESTS = 429

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
