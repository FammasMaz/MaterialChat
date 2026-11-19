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

    /**
     * The sentence for a code, from the voice layer's one copy table ([VoiceCopy]): the same
     * words the pill shows, so a code this table does not know can no longer reach the user
     * as engine wording (W6: the old default said "please").
     */
    fun getErrorMessage(errorCode: Int): String =
        VoiceCopy.recognition(errorCode) ?: VoiceCopy.RECOGNITION_FAILED
}
