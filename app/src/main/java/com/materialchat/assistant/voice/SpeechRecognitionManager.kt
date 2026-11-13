package com.materialchat.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages speech recognition for the MaterialChat Assistant.
 *
 * Uses Android's SpeechRecognizer API to convert voice input to text,
 * with support for partial results and real-time audio amplitude data.
 */
class SpeechRecognitionManager internal constructor(
    private val platform: RecognitionPlatform
) {
    @Inject constructor(context: Context) : this(AndroidRecognitionPlatform(context))
    @Volatile private var activeCleanup: (() -> Unit)? = null
    @Volatile private var activeStop: (() -> Unit)? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _amplitudeData = MutableStateFlow(AudioAmplitudeData.Empty)
    val amplitudeData: StateFlow<AudioAmplitudeData> = _amplitudeData.asStateFlow()

    private var lastAmplitudeEmitUptimeMs = 0L

    /**
     * Check if speech recognition is available on this device.
     */
    fun isAvailable(): Boolean = platform.isAvailable()

    /**
     * Start listening for voice input.
     *
     * @return Flow of recognition results (partial and final)
     */
    fun startListening(options: RecognitionOptions = RecognitionOptions()): Flow<SpeechRecognitionResult> = callbackFlow {
        var cleanup: (() -> Unit)? = null
        withContext(Dispatchers.Main.immediate) {
            activeCleanup?.invoke()
            var recognizer: SpeechRecognizer? = null
            var finished = false
            var attempt = 0
            var ready = false
            var fallbackAttempted = false
            var stopRequested = false
            var startupTimeout: Runnable? = null
            var finalTimeout: Runnable? = null

            fun clearStartupTimeout() {
                startupTimeout?.let(platform::removeCallbacks)
                startupTimeout = null
            }

            fun destroyRecognizer() {
                val owned = recognizer
                recognizer = null
                runCatching { owned?.cancel() }
                runCatching { owned?.destroy() }
            }

            lateinit var finish: () -> Unit
            finish = {
                if (!finished) {
                    finished = true
                    attempt++ // Invalidate callbacks before cancel/destroy can deliver any.
                    clearStartupTimeout()
                    finalTimeout?.let(platform::removeCallbacks)
                    finalTimeout = null
                    if (activeCleanup === finish) {
                        activeCleanup = null
                        activeStop = null
                        _isListening.value = false
                        _amplitudeData.value = AudioAmplitudeData.Empty
                        lastAmplitudeEmitUptimeMs = 0L
                    }
                    destroyRecognizer()
                    close()
                }
            }
            cleanup = finish
            activeCleanup = finish

            fun fail(code: Int, message: String = SpeechRecognitionErrors.getErrorMessage(code)) {
                if (finished) return
                trySend(SpeechRecognitionResult.Error(message, code))
                finish()
            }

            fun awaitFinalResult() {
                if (finished || finalTimeout != null) return
                clearStartupTimeout()
                val timeout = Runnable {
                    fail(SpeechRecognitionErrors.ERROR_CLIENT,
                        "Speech recognition did not finish. Please try again.")
                }
                finalTimeout = timeout
                platform.postDelayed(timeout, FINAL_RESULT_TIMEOUT_MS)
            }

            activeStop = {
                if (!finished && !stopRequested) {
                    stopRequested = true
                    awaitFinalResult()
                    try {
                        recognizer?.stopListening()
                    } catch (_: RuntimeException) {
                        fail(SpeechRecognitionErrors.ERROR_CLIENT,
                            "Could not finish speech recognition. Please try again.")
                    }
                }
            }

            lateinit var startAttempt: (Boolean, Int, String) -> Unit
            fun startupFailure(code: Int, message: String = SpeechRecognitionErrors.getErrorMessage(code)) {
                // Retry only a broken startup, never permission denial, busy/no-match, or speech
                // already captured. A second engine must not silently truncate an utterance.
                if (!ready && !stopRequested && !fallbackAttempted && code in STARTUP_FALLBACK_ERRORS) {
                    fallbackAttempted = true
                    startAttempt(true, code, message)
                } else {
                    fail(code, message)
                }
            }

            startAttempt = start@{ fallback, originalCode, originalMessage ->
                if (finished) return@start
                val ownedAttempt = ++attempt
                clearStartupTimeout()
                ready = false
                destroyRecognizer()
                try {
                    val engine = if (fallback) platform.createFallbackRecognizer() else platform.createRecognizer(options.preferredEngine)
                    if (engine == null) {
                        fail(originalCode, originalMessage)
                        return@start
                    }
                    recognizer = engine
                    fun ownsAttempt() = !finished && attempt == ownedAttempt && recognizer === engine
                    fun markReady() {
                        if (!ready) {
                            ready = true
                            clearStartupTimeout()
                            _isListening.value = true
                            trySend(SpeechRecognitionResult.Ready)
                        }
                    }
                    engine.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (ownsAttempt()) markReady()
                        }
                        override fun onBeginningOfSpeech() {
                            if (!ownsAttempt()) return
                            markReady()
                            trySend(SpeechRecognitionResult.SpeechStarted)
                        }
                        override fun onRmsChanged(rmsdB: Float) {
                            if (!ownsAttempt()) return
                            markReady()
                            updateAmplitudeData(((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f))
                        }
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() {
                            if (!ownsAttempt()) return
                            ready = true
                            clearStartupTimeout()
                            _isListening.value = false
                            trySend(SpeechRecognitionResult.SpeechEnded)
                            awaitFinalResult()
                        }
                        override fun onError(error: Int) {
                            if (ownsAttempt()) startupFailure(error)
                        }
                        override fun onResults(results: Bundle?) {
                            if (!ownsAttempt()) return
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull { it.isNotBlank() }
                            if (text != null) {
                                trySend(SpeechRecognitionResult.FinalResult(text))
                                finish()
                            } else {
                                fail(SpeechRecognitionErrors.ERROR_NO_MATCH, "No speech detected")
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            if (!ownsAttempt()) return
                            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull { it.isNotBlank() } ?: return
                            markReady()
                            trySend(SpeechRecognitionResult.PartialResult(text))
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                    val timeout = Runnable {
                        if (ownsAttempt() && !ready) {
                            startupFailure(SpeechRecognitionErrors.ERROR_CLIENT,
                                "Speech recognizer did not start. Please try again.")
                        }
                    }
                    startupTimeout = timeout
                    platform.postDelayed(timeout, STARTUP_TIMEOUT_MS)
                    engine.startListening(recognitionIntent(options))
                } catch (error: RuntimeException) {
                    // A synchronous callback may have replaced this attempt already.
                    if (finished || attempt != ownedAttempt) return@start
                    val code = if (error is SecurityException) {
                        SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS
                    } else SpeechRecognitionErrors.ERROR_CLIENT
                    startupFailure(code)
                }
            }
            startAttempt(false, SpeechRecognitionErrors.ERROR_CLIENT, "Speech recognition not available")
        }
        awaitClose {
            // An old collector must never cancel its successor.
            platform.post { cleanup?.invoke() }
        }
    }

    /** Finish the current utterance, retaining callbacks for its final transcript. */
    fun stopListening() {
        val ownedStop = activeStop
        platform.dispatch { ownedStop?.invoke() }
    }

    /** Abort the captured session; a delayed cancellation must not affect its successor. */
    fun cancel() {
        val ownedCleanup = activeCleanup
        platform.dispatch { ownedCleanup?.invoke() }
    }

    /**
     * Update amplitude data for waveform visualization.
     * Uses a sliding window approach for smooth animation.
     */
    private fun updateAmplitudeData(newAmplitude: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAmplitudeEmitUptimeMs < AMPLITUDE_MIN_INTERVAL_MS) return
        lastAmplitudeEmitUptimeMs = now

        val previous = _amplitudeData.value.amplitudes
        val size = previous.size
        val phase = now * 0.01f
        // Build the shifted + varied window in a single pass. Previously this
        // allocated a mutable copy, shifted it by hand, then mapped to a second
        // list — two full list copies per frame at ~20 fps while listening.
        val variedAmplitudes = ArrayList<Float>(size)
        for (index in 0 until size) {
            val raw = if (index < size - 1) previous[index + 1] else newAmplitude
            val variation = 0.1f * kotlin.math.sin(index * 0.5f + phase).toFloat()
            variedAmplitudes.add((raw + variation).coerceIn(0.05f, 1f))
        }

        _amplitudeData.value = AudioAmplitudeData(variedAmplitudes)
    }

    private companion object {
        /** Limits RMS callbacks (~60/s) to ~20/s — less Compose churn while listening. */
        const val AMPLITUDE_MIN_INTERVAL_MS = 50L
        const val STARTUP_TIMEOUT_MS = 8_000L
        const val FINAL_RESULT_TIMEOUT_MS = 8_000L
        val STARTUP_FALLBACK_ERRORS = setOf(4, 5, 10, 11, 12, 13)
    }

    /**
     * Release resources.
     */
    fun release() {
        cancel()
    }
}

/**
 * Represents the result of speech recognition.
 */
sealed class SpeechRecognitionResult {
    /**
     * Recognizer is ready to receive speech.
     */
    data object Ready : SpeechRecognitionResult()

    /**
     * User started speaking.
     */
    data object SpeechStarted : SpeechRecognitionResult()

    /**
     * User stopped speaking.
     */
    data object SpeechEnded : SpeechRecognitionResult()

    /**
     * Partial transcription result (real-time).
     */
    data class PartialResult(val text: String) : SpeechRecognitionResult()

    /**
     * Final transcription result.
     */
    data class FinalResult(val text: String) : SpeechRecognitionResult()

    /**
     * Recognition error.
     */
    data class Error(val message: String, val errorCode: Int) : SpeechRecognitionResult()
}

/** Recognition language is explicit; optional multilingual models are opt-in. */
data class RecognitionOptions(
    /** BCP-47 tag such as `en-US`. Null follows the device language. */
    val languageTag: String? = null,
    /** Switching can require downloaded language models, so do not enable it by default. */
    val allowLanguageSwitch: Boolean = false,
    /** Recognizer component chosen in settings; null picks the best installed engine. */
    val preferredEngine: String? = null
)

internal fun recognitionExtras(
    options: RecognitionOptions,
    deviceLanguageTag: String = Locale.getDefault().toLanguageTag()
): Map<String, Any> {
    val explicitLanguage = options.languageTag?.trim()?.takeIf { it.isNotEmpty() }
    val extras = linkedMapOf<String, Any>(
        RecognizerIntent.EXTRA_LANGUAGE_MODEL to RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        RecognizerIntent.EXTRA_LANGUAGE to (explicitLanguage ?: deviceLanguageTag),
        RecognizerIntent.EXTRA_PARTIAL_RESULTS to true,
        RecognizerIntent.EXTRA_MAX_RESULTS to 1,
        RecognizerIntent.EXTRA_PREFER_OFFLINE to false,
        RecognizerIntent.EXTRA_ENABLE_FORMATTING to RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
    )
    // Let the installed engine own endpoint timing. Android warns that overriding silence
    // thresholds outside segmented sessions can produce unexpected recognition behaviour.
    if (options.allowLanguageSwitch && explicitLanguage == null) {
        extras[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION] = true
        extras[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH] = RecognizerIntent.LANGUAGE_SWITCH_BALANCED
    }
    return extras
}

/** Preserve the Android extra types instead of coercing unknown values into strings. */
internal fun recognitionIntent(options: RecognitionOptions): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        recognitionExtras(options).forEach { (key, value) ->
            when (value) {
                is Boolean -> putExtra(key, value)
                is Int -> putExtra(key, value)
                is Long -> putExtra(key, value)
                is String -> putExtra(key, value)
                else -> error("Unsupported recognition option type")
            }
        }
    }

/** Android operations only; session ownership and terminal events stay in the manager. */
internal interface RecognitionPlatform {
    fun isAvailable(): Boolean
    fun createRecognizer(preferredComponent: String? = null): SpeechRecognizer
    fun createFallbackRecognizer(): SpeechRecognizer? = null
    fun dispatch(action: () -> Unit)
    fun post(action: () -> Unit)
    fun postDelayed(action: Runnable, delayMillis: Long)
    fun removeCallbacks(action: Runnable)
}
