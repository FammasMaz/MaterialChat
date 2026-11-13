package com.materialchat.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
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
            var finalTimeout: Runnable? = null
            lateinit var finish: () -> Unit
            finish = {
                if (!finished) {
                    finished = true
                    finalTimeout?.let { platform.removeCallbacks(it) }
                    finalTimeout = null
                    if (activeCleanup === finish) {
                        activeCleanup = null
                        activeStop = null
                        _isListening.value = false
                        _amplitudeData.value = AudioAmplitudeData.Empty
                        lastAmplitudeEmitUptimeMs = 0L
                    }
                    val ownedRecognizer = recognizer
                    recognizer = null
                    runCatching { ownedRecognizer?.cancel() }
                    runCatching { ownedRecognizer?.destroy() }
                    close()
                }
            }
            cleanup = finish
            activeCleanup = finish
            try {
                if (!isAvailable()) {
                    trySend(SpeechRecognitionResult.Error(
                        "Speech recognition not available",
                        SpeechRecognitionErrors.ERROR_CLIENT
                    ))
                    finish()
                    return@withContext
                }

                val engine = platform.createRecognizer()
                recognizer = engine
                fun awaitFinalResult() {
                    if (finished || finalTimeout != null) return
                    val timeout = Runnable {
                        if (!finished) {
                            trySend(SpeechRecognitionResult.Error(
                                "Speech recognition did not finish. Please try again.",
                                SpeechRecognitionErrors.ERROR_CLIENT
                            ))
                            finish()
                        }
                    }
                    finalTimeout = timeout
                    platform.postDelayed(timeout, 8_000L)
                }
                activeStop = {
                    if (!finished && finalTimeout == null) {
                        awaitFinalResult()
                        try {
                            engine.stopListening()
                        } catch (error: RuntimeException) {
                            trySend(SpeechRecognitionResult.Error(
                                "Could not finish speech recognition. Please try again.",
                                SpeechRecognitionErrors.ERROR_CLIENT
                            ))
                            finish()
                        }
                    }
                }

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (finished) return
                        _isListening.value = true
                        trySend(SpeechRecognitionResult.Ready)
                    }

                    override fun onBeginningOfSpeech() {
                        if (finished) return
                        trySend(SpeechRecognitionResult.SpeechStarted)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (finished) return
                        // Convert RMS dB to normalized amplitude (0.0 to 1.0)
                        // RMS typically ranges from -2 to 10 dB
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                        updateAmplitudeData(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Not used for basic recognition
                    }

                    override fun onEndOfSpeech() {
                        if (finished) return
                        _isListening.value = false
                        trySend(SpeechRecognitionResult.SpeechEnded)
                        awaitFinalResult()
                    }

                    override fun onError(error: Int) {
                        if (finished) return
                        _isListening.value = false
                        _amplitudeData.value = AudioAmplitudeData.Empty
                        trySend(SpeechRecognitionResult.Error(
                            SpeechRecognitionErrors.getErrorMessage(error),
                            error
                        ))
                        finish()
                    }

                    override fun onResults(results: Bundle?) {
                        if (finished) return
                        _isListening.value = false
                        _amplitudeData.value = AudioAmplitudeData.Empty

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""

                        if (text.isNotEmpty()) {
                            trySend(SpeechRecognitionResult.FinalResult(text))
                        } else {
                            trySend(SpeechRecognitionResult.Error(
                                "No speech detected",
                                SpeechRecognitionErrors.ERROR_NO_MATCH
                            ))
                        }
                        finish()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (finished) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return

                        if (text.isNotEmpty()) {
                            trySend(SpeechRecognitionResult.PartialResult(text))
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Not used
                    }
                }

                engine.setRecognitionListener(listener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    recognitionExtras(options).forEach { (key, value) ->
                        when (value) {
                            is Boolean -> putExtra(key, value)
                            is Int -> putExtra(key, value)
                            else -> putExtra(key, value.toString())
                        }
                    }
                }

                engine.startListening(intent)

            } catch (error: RuntimeException) {
                val code = if (error is SecurityException) {
                    SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS
                } else {
                    SpeechRecognitionErrors.ERROR_CLIENT
                }
                trySend(SpeechRecognitionResult.Error(SpeechRecognitionErrors.getErrorMessage(code), code))
                finish()
            }
        }
        awaitClose {
            // Capture this flow's cleanup: an old collector must not cancel a newer session.
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

/**
 * Recognition request tuning.
 *
 * The system recognizer only does its best work when the caller asks for it the way the keyboard
 * and the built-in assistant do: an explicit language, formatting, and silence thresholds that do
 * not cut a sentence off at the first pause.
 */
data class RecognitionOptions(
    /** BCP-47 tag such as `en-US`. Null follows the device language. */
    val languageTag: String? = null,
    /** Let the recognizer detect and switch languages mid-utterance (API 34+). */
    val allowLanguageSwitch: Boolean = true
)

/**
 * Extras for [RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 *
 * Pure so the request shape is testable without a recognition service. Unknown extras are ignored
 * by older services, so newer keys are always included rather than gated per API level.
 */
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
        // Punctuation and capitalisation, as the keyboard produces (API 33+; older services ignore it).
        RecognizerIntent.EXTRA_ENABLE_FORMATTING to RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,
        // Do not end the utterance at the first short pause.
        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS to 800,
        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS to 1_500,
        RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS to 1_000
    )
    // Auto-switching is only useful while the language is not pinned by the user.
    if (options.allowLanguageSwitch && explicitLanguage == null) {
        extras[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION] = true
        extras[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH] = true
    }
    return extras
}

/** What the system will actually use for recognition; surfaced in the voice settings panel. */
data class RecognitionEngineReport(
    val defaultComponent: String?,
    val visibleServiceCount: Int,
    val usesOnDeviceFallback: Boolean
)

fun recognitionEngineReport(context: Context): RecognitionEngineReport {
    val default = runCatching {
        // Settings.Secure.VOICE_RECOGNITION_SERVICE is not in the public SDK; this is its key.
        Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
    }.getOrNull()
    // Package visibility decides this: an empty list means the app cannot see the installed
    // recognizer, which forces the less accurate on-device model.
    val visible = runCatching {
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .size
    }.getOrDefault(0)
    val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    return RecognitionEngineReport(
        defaultComponent = default?.takeIf { it.isNotBlank() },
        visibleServiceCount = visible,
        usesOnDeviceFallback = visible == 0 && onDeviceAvailable
    )
}

/** Android operations only; session ownership and terminal events stay in the manager. */
internal interface RecognitionPlatform {
    fun isAvailable(): Boolean
    fun createRecognizer(): SpeechRecognizer
    fun dispatch(action: () -> Unit)
    fun post(action: () -> Unit)
    fun postDelayed(action: Runnable, delayMillis: Long)
    fun removeCallbacks(action: Runnable)
}

private class AndroidRecognitionPlatform(private val context: Context) : RecognitionPlatform {
    private val handler = Handler(Looper.getMainLooper())
    private fun isOnDeviceAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    override fun isAvailable() = isOnDeviceAvailable() || SpeechRecognizer.isRecognitionAvailable(context)
    override fun createRecognizer(): SpeechRecognizer {
        // The system default is the same engine the keyboard uses (network-backed and accurate).
        // The on-device model is only a fallback: it works offline but transcribes noticeably worse.
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            return SpeechRecognizer.createSpeechRecognizer(context)
        }
        check(isOnDeviceAvailable()) { "Speech recognition not available" }
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    }
    override fun dispatch(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else post(action)
    }
    override fun post(action: () -> Unit) { handler.post { action() } }
    override fun postDelayed(action: Runnable, delayMillis: Long) { handler.postDelayed(action, delayMillis) }
    override fun removeCallbacks(action: Runnable) { handler.removeCallbacks(action) }
}
