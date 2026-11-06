package com.materialchat.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Main-thread-owned Android TTS. Collect [enqueue] for completion of an individual chunk. */
class TextToSpeechManager internal constructor(
    private val dispatch: (() -> Unit) -> Unit,
    private val factory: (TtsCallbacks) -> TtsEngine
) {
    @Inject constructor(context: Context) : this(
        { action ->
            val handler = Handler(Looper.getMainLooper())
            if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
        },
        { callbacks -> AndroidTtsEngine(context.applicationContext, callbacks) }
    )

    private var engine: TtsEngine? = null
    private var ready = false
    private var generation = 0
    private val waiters = linkedMapOf<String, (TtsInitResult) -> Unit>()
    private data class Pending(val emit: (TtsEvent) -> Unit, val close: () -> Unit)
    private val pending = linkedMapOf<String, Pending>()
    private val speaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = speaking.asStateFlow()
    private val voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val installedVoices: StateFlow<List<TtsVoice>> = voices.asStateFlow()

    /** Concurrent collectors share one initialization; failed attempts may be retried. */
    fun initialize(): Flow<TtsInitResult> = callbackFlow {
        val id = UUID.randomUUID().toString()
        dispatch {
            if (ready) {
                trySend(TtsInitResult.Success)
                close()
            } else {
                waiters[id] = { trySend(it); close() }
                if (engine == null) startEngine()
            }
        }
        val timeout = launch {
            delay(10_000)
            dispatch {
                if (id in waiters) initialized(generation, false)
            }
        }
        awaitClose {
            timeout.cancel()
            dispatch { waiters.remove(id) }
        }
    }

    private fun startEngine() {
        val token = ++generation
        // Always defer initialization callbacks until the factory has returned its instance.
        var constructing = true
        var initialStatus: Boolean? = null
        val callbacks = object : TtsCallbacks {
            override fun initialized(success: Boolean) {
                dispatch {
                    if (constructing) initialStatus = success else initialized(token, success)
                }
            }
            override fun started(id: String) = dispatch {
                if (token == generation) pending[id]?.emit?.invoke(TtsEvent.Started)
            }
            override fun finished(id: String, error: String?, stopped: Boolean) = dispatch {
                if (token == generation) finish(id, when {
                    error != null -> TtsEvent.Error(error)
                    stopped -> TtsEvent.Stopped
                    else -> TtsEvent.Completed
                })
            }
        }
        try {
            engine = factory(callbacks)
            constructing = false
            initialStatus?.let { initialized(token, it) }
        } catch (error: RuntimeException) {
            constructing = false
            initialized(token, false)
        }
    }

    private fun initialized(token: Int, success: Boolean) {
        if (token != generation || ready) return
        val result = try {
            if (!success) error("Failed to initialize text-to-speech")
            val current = checkNotNull(engine)
            voices.value = current.voices().sortedWith(compareBy({ it.languageTag }, { it.id }))
            ready = true
            TtsInitResult.Success
        } catch (error: RuntimeException) {
            runCatching { engine?.shutdown() }
            engine = null
            generation++
            voices.value = emptyList()
            TtsInitResult.Error(error.message ?: "Failed to initialize text-to-speech")
        }
        val callbacks = waiters.values.toList()
        waiters.clear()
        callbacks.forEach { it(result) }
    }

    /** Additive API. Applies normalized preferences; unknown voices/languages fail explicitly. */
    fun configure(settings: AssistantVoiceSettings): Flow<TtsInitResult> = callbackFlow {
        dispatch {
            val result = try {
                val current = checkNotNull(engine?.takeIf { ready }) { "Text-to-speech not initialized" }
                val value = settings.normalized()
                value.languageTag?.let {
                    val locale = Locale.forLanguageTag(it)
                    check(locale.language.isNotEmpty() && current.language(locale)) { "Language unavailable: $it" }
                }
                value.androidVoiceId?.let { id ->
                    val voice = voices.value.firstOrNull { it.id == id }
                    check(voice != null) { "Voice unavailable: $id" }
                    check(value.languageTag == null ||
                        Locale.forLanguageTag(voice.languageTag).language ==
                        Locale.forLanguageTag(value.languageTag).language) { "Voice language mismatch: $id" }
                    check(current.voice(id)) { "Voice unavailable: $id" }
                }
                check(current.rate(value.speechRate)) { "Speech rate rejected" }
                TtsInitResult.Success
            } catch (error: RuntimeException) {
                TtsInitResult.Error(error.message ?: "Voice configuration failed")
            }
            trySend(result)
            close()
        }
        awaitClose { }
    }

    /** Legacy replacement playback; completes only after the entire text is finished. */
    fun speak(text: String): Flow<TtsEvent> = playback(text, replace = true)

    /** Queues a chunk without replacing the listener or interrupting earlier collectors. */
    fun enqueue(text: String): Flow<TtsEvent> = playback(text, replace = false)

    private fun playback(text: String, replace: Boolean): Flow<TtsEvent> = callbackFlow {
        val id = UUID.randomUUID().toString()
        dispatch { submit(text, id, replace, Pending({ trySend(it) }, { close() })) }
        awaitClose {
            // Android cannot remove one queued utterance. Cancel the queue only if this
            // collector still owns pending speech; normal completion must not stop others.
            dispatch { if (pending.containsKey(id)) stopMain() }
        }
    }

    private fun submit(text: String, id: String, replace: Boolean, sink: Pending) {
        if (replace) stopMain()
        if (!ready) {
            sink.emit(TtsEvent.Error("Text-to-speech not initialized")); sink.close(); return
        }
        if (text.isBlank()) {
            sink.emit(TtsEvent.Completed); sink.close(); return
        }
        pending[id] = sink
        speaking.value = true
        try {
            check(engine!!.speak(text, id)) { "Speech queue rejected text" }
        } catch (error: RuntimeException) {
            finish(id, TtsEvent.Error(error.message ?: "Speech synthesis failed"))
        }
    }

    /** Legacy fire-and-forget queue API; use enqueue for observable completion/errors. */
    fun speakIncremental(text: String) = dispatch {
        submit(text, UUID.randomUUID().toString(), false, Pending({}, {}))
    }

    private fun finish(id: String, event: TtsEvent) {
        val sink = pending.remove(id) ?: return
        speaking.value = pending.isNotEmpty()
        sink.emit(event)
        sink.close()
    }

    fun stop() = dispatch { stopMain() }
    private fun stopMain() {
        val ids = pending.keys.toList()
        ids.forEach { finish(it, TtsEvent.Stopped) }
        runCatching { engine?.stop() }
    }

    fun setSpeechRate(rate: Float) = dispatch {
        engine?.rate(if (rate.isFinite()) rate.coerceIn(0.25f, 2f) else 1f)
        Unit
    }
    fun setPitch(pitch: Float) = dispatch {
        engine?.pitch(if (pitch.isFinite()) pitch.coerceIn(0.5f, 2f) else 1f)
        Unit
    }
    fun release() = dispatch {
        stopMain()
        generation++
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
        voices.value = emptyList()
        val callbacks = waiters.values.toList()
        waiters.clear()
        callbacks.forEach { it(TtsInitResult.Error("Text-to-speech released")) }
    }
}

private class AndroidTtsEngine(context: Context, callbacks: TtsCallbacks) : TtsEngine {
    private val tts = TextToSpeech(context) { callbacks.initialized(it == TextToSpeech.SUCCESS) }
    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = callbacks.started(utteranceId)
            override fun onDone(utteranceId: String) = callbacks.finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = callbacks.finished(utteranceId, "Speech synthesis failed")
            override fun onError(utteranceId: String, errorCode: Int) =
                callbacks.finished(utteranceId, "Speech synthesis failed (code: $errorCode)")
            override fun onStop(utteranceId: String, interrupted: Boolean) =
                callbacks.finished(utteranceId, stopped = true)
        })
    }
    override fun voices(): List<TtsVoice> = tts.voices.orEmpty()
        .filter { !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty() }
        .map { TtsVoice(it.name, it.locale.toLanguageTag()) }
    override fun language(locale: Locale) = tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
    override fun voice(id: String) = tts.voices.orEmpty().firstOrNull { it.name == id }
        ?.let { tts.setVoice(it) == TextToSpeech.SUCCESS } ?: false
    override fun rate(value: Float) = tts.setSpeechRate(value) == TextToSpeech.SUCCESS
    override fun pitch(value: Float) = tts.setPitch(value) == TextToSpeech.SUCCESS
    override fun speak(text: String, id: String) = tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS
    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
}

sealed class TtsInitResult {
    data object Success : TtsInitResult()
    data class Error(val message: String) : TtsInitResult()
}
sealed class TtsEvent {
    data object Started : TtsEvent()
    data object Completed : TtsEvent()
    data object Stopped : TtsEvent()
    data class Error(val message: String) : TtsEvent()
}
