package com.materialchat.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Manages text-to-speech for the MaterialChat Assistant.
 *
 * Uses Android's TextToSpeech API to read AI responses aloud,
 * with support for sentence-by-sentence reading and streaming responses.
 */
class TextToSpeechManager @Inject constructor(
    private val context: Context
) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentUtteranceId: String? = null

    /**
     * Initialize the text-to-speech engine.
     *
     * @return true if initialization was successful
     */
    fun initialize(): Flow<TtsInitResult> = callbackFlow {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech = ttsInstance
                isInitialized = true

                // Set language to English (US)
                val result = ttsInstance?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    trySend(TtsInitResult.Error("English language not supported"))
                } else {
                    // Set default speech rate and pitch
                    ttsInstance?.setSpeechRate(1.0f)
                    ttsInstance?.setPitch(1.0f)

                    trySend(TtsInitResult.Success)
                }
            } else {
                trySend(TtsInitResult.Error("Failed to initialize text-to-speech"))
            }
            close()
        }

        awaitClose {
            // Don't destroy TTS on close, let release() handle that
        }
    }

    /**
     * Speak the given text.
     *
     * @param text The text to speak
     * @return Flow of speech progress events
     */
    fun speak(text: String): Flow<TtsEvent> = callbackFlow {
        if (!isInitialized || textToSpeech == null) {
            trySend(TtsEvent.Error("Text-to-speech not initialized"))
            close()
            return@callbackFlow
        }

        val tts = textToSpeech!!
        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId

        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                trySend(TtsEvent.Started)
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                trySend(TtsEvent.Completed)
                close()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                trySend(TtsEvent.Error("Speech synthesis failed"))
                close()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                trySend(TtsEvent.Error("Speech synthesis failed (code: $errorCode)"))
                close()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _isSpeaking.value = false
                if (interrupted) {
                    trySend(TtsEvent.Stopped)
                } else {
                    trySend(TtsEvent.Completed)
                }
                close()
            }
        }

        tts.setOnUtteranceProgressListener(listener)

        // Split into sentences for more natural pacing with streaming responses
        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) {
            trySend(TtsEvent.Completed)
            close()
            return@callbackFlow
        }

        // Queue all sentences
        sentences.forEachIndexed { index, sentence ->
            val queueMode = if (index == 0) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }

            val sentenceId = if (index == sentences.lastIndex) utteranceId else "$utteranceId-$index"
            tts.speak(sentence, queueMode, null, sentenceId)
        }

        awaitClose {
            stop()
        }
    }

    /**
     * Speak text incrementally for streaming responses.
     * This method queues new text to be spoken after the current speech.
     *
     * @param text The new text segment to speak
     */
    fun speakIncremental(text: String) {
        if (!isInitialized || textToSpeech == null) return

        val tts = textToSpeech!!
        val utteranceId = UUID.randomUUID().toString()

        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        _isSpeaking.value = true
    }

    /**
     * Stop speaking.
     */
    fun stop() {
        _isSpeaking.value = false
        currentUtteranceId = null
        textToSpeech?.stop()
    }

    /**
     * Set the speech rate.
     *
     * @param rate Speech rate (0.5 = half speed, 1.0 = normal, 2.0 = double speed)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    /**
     * Set the pitch.
     *
     * @param pitch Pitch level (0.5 = lower, 1.0 = normal, 2.0 = higher)
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /**
     * Release resources.
     */
    fun release() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }

    /**
     * Split text into sentences for more natural TTS pacing.
     */
    private fun splitIntoSentences(text: String): List<String> {
        // Split on sentence-ending punctuation followed by whitespace
        val sentencePattern = Regex("""(?<=[.!?])\s+""")
        return text.split(sentencePattern)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

/**
 * Result of TTS initialization.
 */
sealed class TtsInitResult {
    data object Success : TtsInitResult()
    data class Error(val message: String) : TtsInitResult()
}

/**
 * Events during text-to-speech playback.
 */
sealed class TtsEvent {
    data object Started : TtsEvent()
    data object Completed : TtsEvent()
    data object Stopped : TtsEvent()
    data class Error(val message: String) : TtsEvent()
}
