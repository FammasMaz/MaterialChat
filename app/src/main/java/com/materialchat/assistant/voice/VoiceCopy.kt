package com.materialchat.assistant.voice

/**
 * The voice layer's user-visible copy: every sentence a recognizer failure, a speech-output
 * failure, a voice fallback or a neural-model install can put in front of the user (the pill's
 * notice surface or the voice settings dialog). It follows the assistant's copy rules
 * (`com.materialchat.assistant.ui.AssistantCopy`, which aliases the recognizer sentences and lists
 * every sentence here for its copy-rule test): one or more complete sentences in sentence case,
 * each ending with a full stop; "try again" as the only error verb; no "please", no exception
 * text, no engine wording. Product names (the Android voice, Gemini, a neural model's label) are
 * kept where the user has to pick between them in voice settings. Pure Kotlin, in the voice
 * package so the backends do not depend on the overlay (W6).
 */
object VoiceCopy {
    // Speech recognition, by platform code ([SpeechRecognitionErrors]). The pill shows these
    // for the code, never the recognizer's own wording.
    const val MIC_PERMISSION = "Microphone access is off. Allow it in system settings, or type your question."
    const val RECOGNIZER_UNAVAILABLE = "Speech recognition is not available on this device. Type your question instead."
    const val RECOGNITION_FAILED = "Speech recognition stopped early. Try again."
    const val NO_MATCH = "Didn't catch that. Try again."
    const val SPEECH_TIMEOUT = "No speech heard. Try again."
    const val RECOGNITION_NEEDS_CONNECTION = "Speech recognition needs a connection. Check your network and try again."
    const val MICROPHONE_FAILED = "The microphone could not be used. Try again."
    const val SPEECH_SERVICE_FAILED = "The speech service did not respond. Try again."
    const val SPEECH_SERVICE_BUSY = "The speech service is busy. Try again in a moment."

    /** The sentence for a known recognizer code; null for a code this table does not know. */
    fun recognition(code: Int): String? = when (code) {
        SpeechRecognitionErrors.ERROR_NO_MATCH -> NO_MATCH
        SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT -> SPEECH_TIMEOUT
        SpeechRecognitionErrors.ERROR_NETWORK, SpeechRecognitionErrors.ERROR_NETWORK_TIMEOUT -> RECOGNITION_NEEDS_CONNECTION
        SpeechRecognitionErrors.ERROR_AUDIO -> MICROPHONE_FAILED
        SpeechRecognitionErrors.ERROR_SERVER -> SPEECH_SERVICE_FAILED
        SpeechRecognitionErrors.ERROR_RECOGNIZER_BUSY -> SPEECH_SERVICE_BUSY
        SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS -> MIC_PERMISSION
        // A missing engine, a startup that never happened and a stop that never finished share
        // this code; the manager names the first explicitly and the pill maps it.
        SpeechRecognitionErrors.ERROR_CLIENT -> RECOGNITION_FAILED
        else -> null
    }

    // Speech output: failures that reach the pill as `error` (the answer stays on screen).
    const val SPEECH_UNAVAILABLE = "Speech output is not available. Check voice settings and try again."
    const val SPEECH_NOT_READY = "Speech output is not ready. Try again."
    const val SPEECH_FAILED = "Speech output failed. Try again."
    const val AUDIO_PLAYBACK_FAILED = "Audio playback failed."
    const val ANDROID_VOICE_UNAVAILABLE = "The Android voice is not available. Check voice settings."

    // Gemini speech.
    const val GEMINI_KEY_MISSING = "No Gemini speech key is set. Add one in voice settings."
    const val GEMINI_TIMED_OUT = "Gemini speech timed out. Try again, or use the Android voice."
    const val GEMINI_FAILED = "Gemini speech failed. Check voice settings, quota and connection."
    const val GEMINI_AUTHENTICATION = "Gemini speech authentication failed. Check the speech API key."
    const val GEMINI_PERMISSION = "Gemini speech permission was denied. Check project and API access."
    const val GEMINI_QUOTA = "Gemini speech quota or rate limit reached. Check this model's quota in Google AI Studio, or use the Android voice."
    const val GEMINI_NO_QUOTA = "This Gemini speech model has no project quota. Check model access and billing in Google AI Studio, or use the Android voice."
    const val GEMINI_MODEL_UNAVAILABLE = "The Gemini speech model is not available. Check the selected model."
    const val GEMINI_TRANSPORT = "The Gemini speech service or the connection failed. Try again later."
    const val GEMINI_INVALID_AUDIO = "Gemini speech returned audio that could not be played."

    // The neural (sherpa-onnx) voice: fallbacks reach the pill as `notice`, failures as `error`.
    const val NEURAL_UNAVAILABLE_BUILD = "The neural voice is not available in this build. Using the Android voice."
    const val USING_ANDROID_VOICE = "Using the Android voice."
    fun neuralNotDownloaded(label: String): String = "$label isn't downloaded. Using the Android voice until you download it in voice settings."
    fun neuralInstalling(label: String): String = "$label is still installing. Using the Android voice for now."
    /** [reason] is one of the install sentences below (it already ends with a full stop). */
    fun neuralInstallFailed(label: String, reason: String): String = "$label failed to install. $reason $USING_ANDROID_VOICE"
    fun neuralUnavailable(label: String): String = "$label is not available. $USING_ANDROID_VOICE"
    fun neuralLoadFailed(label: String): String = "$label could not be loaded. Delete it and download it again in voice settings."
    fun neuralSynthesisFailed(label: String): String = "$label failed to synthesise speech."
    /** A failure before any audio played: the Android voice speaks instead, and the notice says why. */
    fun fallingBack(reason: String): String = "$reason $USING_ANDROID_VOICE"

    // Neural model install (the voice settings dialog's failed state; also inside the fallback notice).
    fun notEnoughStorage(needed: String): String = "Not enough free storage. About $needed is needed."
    const val DOWNLOAD_INCOMPLETE = "Download was incomplete. Check the connection and try again."
    const val CHECKSUM_MISMATCH = "Downloaded file failed verification (checksum mismatch). Try again."
    fun packageMissing(file: String): String = "Model package is missing $file."
    const val MOVE_FAILED = "Could not move the model into app storage."
    const val INSTALL_FAILED = "Model install failed."
    const val INSTALL_FAILED_UNEXPECTED = "Model install failed unexpectedly. Try again."
    const val NO_INTERNET = "No internet connection. Connect to a network and try again."
    const val DOWNLOAD_FAILED = "Download failed. Check the connection and try again."

    /** Every sentence above, with representative arguments, for the assistant's copy-rule test. */
    fun samples(): List<String> {
        val label = SherpaTtsModel.SUPERTONIC.label
        return listOf(
            MIC_PERMISSION, RECOGNIZER_UNAVAILABLE, RECOGNITION_FAILED, NO_MATCH, SPEECH_TIMEOUT,
            RECOGNITION_NEEDS_CONNECTION, MICROPHONE_FAILED, SPEECH_SERVICE_FAILED, SPEECH_SERVICE_BUSY,
            SPEECH_UNAVAILABLE, SPEECH_NOT_READY, SPEECH_FAILED, AUDIO_PLAYBACK_FAILED, ANDROID_VOICE_UNAVAILABLE,
            GEMINI_KEY_MISSING, GEMINI_TIMED_OUT, GEMINI_FAILED, GEMINI_AUTHENTICATION, GEMINI_PERMISSION,
            GEMINI_QUOTA, GEMINI_NO_QUOTA, GEMINI_MODEL_UNAVAILABLE, GEMINI_TRANSPORT, GEMINI_INVALID_AUDIO,
            NEURAL_UNAVAILABLE_BUILD, USING_ANDROID_VOICE, neuralNotDownloaded(label), neuralInstalling(label),
            neuralInstallFailed(label, DOWNLOAD_INCOMPLETE), neuralUnavailable(label), neuralLoadFailed(label),
            neuralSynthesisFailed(label), fallingBack(neuralLoadFailed(label)),
            notEnoughStorage("80 MB"), DOWNLOAD_INCOMPLETE, CHECKSUM_MISMATCH, packageMissing("tts.json"),
            MOVE_FAILED, INSTALL_FAILED, INSTALL_FAILED_UNEXPECTED, NO_INTERNET, DOWNLOAD_FAILED
        )
    }
}
