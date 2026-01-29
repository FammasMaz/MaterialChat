package com.materialchat.assistant.voice

/**
 * Represents the current state of voice interaction in the assistant.
 */
sealed class VoiceState {
    /**
     * Assistant is idle, waiting for user interaction.
     */
    data object Idle : VoiceState()

    /**
     * Assistant is listening for voice input.
     */
    data object Listening : VoiceState()

    /**
     * Voice input is being processed (speech-to-text conversion).
     *
     * @property partialText Partial transcription received so far
     */
    data class Processing(val partialText: String = "") : VoiceState()

    /**
     * AI is generating a response.
     *
     * @property userQuery The user's transcribed query
     */
    data class Thinking(val userQuery: String) : VoiceState()

    /**
     * AI response is being streamed.
     *
     * @property content The accumulated response content
     * @property userQuery The original user query
     */
    data class Responding(
        val content: String,
        val userQuery: String
    ) : VoiceState()

    /**
     * Response is complete.
     *
     * @property content The complete response
     * @property userQuery The original user query
     */
    data class Complete(
        val content: String,
        val userQuery: String
    ) : VoiceState()

    /**
     * Text-to-speech is reading the response.
     *
     * @property content The content being read
     */
    data class Speaking(val content: String) : VoiceState()

    /**
     * An error occurred during voice interaction.
     *
     * @property message Error message to display
     * @property errorCode Optional error code for debugging
     */
    data class Error(
        val message: String,
        val errorCode: Int? = null
    ) : VoiceState()
}

/**
 * Audio amplitude data for voice waveform visualization.
 *
 * @property amplitudes List of amplitude values (0.0 to 1.0) for each bar
 */
data class AudioAmplitudeData(
    val amplitudes: List<Float> = List(12) { 0.1f }
) {
    companion object {
        val Empty = AudioAmplitudeData()
    }
}

/**
 * Speech recognition error codes.
 */
object SpeechRecognitionErrors {
    const val ERROR_NETWORK_TIMEOUT = 1
    const val ERROR_NETWORK = 2
    const val ERROR_AUDIO = 3
    const val ERROR_SERVER = 4
    const val ERROR_CLIENT = 5
    const val ERROR_SPEECH_TIMEOUT = 6
    const val ERROR_NO_MATCH = 7
    const val ERROR_RECOGNIZER_BUSY = 8
    const val ERROR_INSUFFICIENT_PERMISSIONS = 9

    fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        ERROR_NETWORK_TIMEOUT -> "Network timeout. Please check your connection."
        ERROR_NETWORK -> "Network error. Please check your internet connection."
        ERROR_AUDIO -> "Audio recording error. Please try again."
        ERROR_SERVER -> "Server error. Please try again later."
        ERROR_CLIENT -> "Client error occurred."
        ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
        ERROR_NO_MATCH -> "Didn't catch that. Please try again."
        ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
        else -> "An error occurred. Please try again."
    }
}
