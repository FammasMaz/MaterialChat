package com.materialchat.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlin.math.abs

/**
 * Manages speech recognition for the MaterialChat Assistant.
 *
 * Uses Android's SpeechRecognizer API to convert voice input to text,
 * with support for partial results and real-time audio amplitude data.
 */
class SpeechRecognitionManager @Inject constructor(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _amplitudeData = MutableStateFlow(AudioAmplitudeData.Empty)
    val amplitudeData: StateFlow<AudioAmplitudeData> = _amplitudeData.asStateFlow()

    /**
     * Check if speech recognition is available on this device.
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start listening for voice input.
     *
     * @return Flow of recognition results (partial and final)
     */
    fun startListening(): Flow<SpeechRecognitionResult> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechRecognitionResult.Error(
                "Speech recognition not available",
                SpeechRecognitionErrors.ERROR_CLIENT
            ))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                trySend(SpeechRecognitionResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechRecognitionResult.SpeechStarted)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Convert RMS dB to normalized amplitude (0.0 to 1.0)
                // RMS typically ranges from -2 to 10 dB
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                updateAmplitudeData(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used for basic recognition
            }

            override fun onEndOfSpeech() {
                _isListening.value = false
                trySend(SpeechRecognitionResult.SpeechEnded)
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _amplitudeData.value = AudioAmplitudeData.Empty
                trySend(SpeechRecognitionResult.Error(
                    SpeechRecognitionErrors.getErrorMessage(error),
                    error
                ))
                close()
            }

            override fun onResults(results: Bundle?) {
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
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
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

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Use the device's default language
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
        }

        recognizer.startListening(intent)

        awaitClose {
            stopListening()
        }
    }

    /**
     * Stop listening for voice input.
     */
    fun stopListening() {
        _isListening.value = false
        _amplitudeData.value = AudioAmplitudeData.Empty
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
    }

    /**
     * Cancel ongoing recognition.
     */
    fun cancel() {
        stopListening()
    }

    /**
     * Update amplitude data for waveform visualization.
     * Uses a sliding window approach for smooth animation.
     */
    private fun updateAmplitudeData(newAmplitude: Float) {
        val currentAmplitudes = _amplitudeData.value.amplitudes.toMutableList()

        // Shift existing amplitudes and add new one
        for (i in 0 until currentAmplitudes.size - 1) {
            currentAmplitudes[i] = currentAmplitudes[i + 1]
        }
        currentAmplitudes[currentAmplitudes.size - 1] = newAmplitude

        // Add some variation to create more organic waveform
        val variedAmplitudes = currentAmplitudes.mapIndexed { index, amplitude ->
            val variation = 0.1f * kotlin.math.sin(index * 0.5f + System.currentTimeMillis() * 0.01f).toFloat()
            (amplitude + variation).coerceIn(0.05f, 1f)
        }

        _amplitudeData.value = AudioAmplitudeData(variedAmplitudes)
    }

    /**
     * Release resources.
     */
    fun release() {
        stopListening()
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
