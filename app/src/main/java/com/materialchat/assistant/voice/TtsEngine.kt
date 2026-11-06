package com.materialchat.assistant.voice

import java.util.Locale

/** Installed, offline-ready voice. IDs are Android engine voice names. */
data class TtsVoice(val id: String, val languageTag: String)

/** Small platform boundary; all calls and callbacks are serialized on the main thread. */
internal interface TtsEngine {
    fun voices(): List<TtsVoice>
    fun language(locale: Locale): Boolean
    fun voice(id: String): Boolean
    fun rate(value: Float): Boolean
    fun pitch(value: Float): Boolean
    fun speak(text: String, id: String): Boolean
    fun stop()
    fun shutdown()
}

internal interface TtsCallbacks {
    fun initialized(success: Boolean)
    fun started(id: String)
    fun finished(id: String, error: String? = null, stopped: Boolean = false)
}
