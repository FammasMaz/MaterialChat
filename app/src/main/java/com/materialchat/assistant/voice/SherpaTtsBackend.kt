package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** Blocking synthesizer for one loaded model. Not thread-safe; [SherpaTtsBackend] serialises calls. */
interface SherpaSynthesizer {
    val sampleRate: Int
    /** Delivers audio in order as it is produced. Return false from [onSamples] to stop early. */
    fun synthesize(text: String, speed: Float, language: String, onSamples: (FloatArray) -> Boolean)
    fun close()
}

fun interface SherpaSynthesizerFactory {
    /** Loads the model from [dir]; throws when the native runtime or the model files cannot be used. */
    fun load(model: SherpaTtsModel, dir: File): SherpaSynthesizer
}

/** Plays 16-bit mono little-endian PCM chunks in order and returns once the last one was heard. */
fun interface SherpaPcmSink {
    suspend fun play(sampleRate: Int, first: ByteArray, rest: ReceiveChannel<ByteArray>)
}

class SherpaLoadException(message: String) : RuntimeException(message)
class SherpaSynthesisException(message: String) : RuntimeException(message)

/**
 * On-device neural speech through sherpa-onnx. Each [speak] collection is one bounded utterance:
 * synthesis runs on a dedicated thread and streams PCM into the player as the model produces it, so
 * the first sentence plays while later ones are still being synthesised. Cancelling the collection
 * stops synthesis (the native callback is told to stop) and releases the audio track.
 */
class SherpaTtsBackend(
    private val store: SherpaModelStore,
    private val factory: SherpaSynthesizerFactory,
    private val sink: SherpaPcmSink,
    private val synthesis: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "sherpa-tts") }.asCoroutineDispatcher()
) {
    private val lock = Mutex()
    private var loadedModel: SherpaTtsModel? = null
    private var loaded: SherpaSynthesizer? = null

    init {
        store.onRemoved { model -> release(model) }
    }

    /** Null when the model can be spoken with; otherwise the user-facing reason the Android voice is used. */
    fun unavailableReason(model: SherpaTtsModel): String? = sherpaFallbackMessage(store.state(model), model)

    fun speak(text: String, settings: AssistantVoiceSettings, model: SherpaTtsModel): Flow<TtsEvent> = flow {
        if (text.isBlank()) {
            emit(TtsEvent.Completed)
            return@flow
        }
        val dir = store.installedDir(model)
        if (dir == null) {
            emit(TtsEvent.Error(unavailableReason(model) ?: VoiceCopy.neuralUnavailable(model.label)))
            return@flow
        }
        val value = settings.normalized()
        val language = supertonicLanguage(value.languageTag)
        try {
            lock.withLock {
                val synthesizer = withContext(synthesis) { synthesizer(model, dir) }
                val chunks = Channel<ByteArray>(CHUNK_CAPACITY)
                coroutineScope {
                    launch(synthesis) {
                        var failure: Throwable? = null
                        try {
                            synthesizer.synthesize(text, value.speechRate, language) { samples ->
                                chunks.trySendBlocking(pcm16(samples)).isSuccess
                            }
                        } catch (error: Throwable) {
                            failure = error
                        }
                        chunks.close(failure?.let { SherpaSynthesisException(it.message ?: "synthesis failed") })
                    }
                    try {
                        val first = chunks.receiveCatching()
                        first.exceptionOrNull()?.let { throw it }
                        val audio = first.getOrNull()
                        if (audio != null) {
                            emit(TtsEvent.Started)
                            sink.play(synthesizer.sampleRate, audio, chunks)
                        }
                    } finally {
                        // Unblocks the producer so a stopped native call returns promptly.
                        chunks.cancel()
                    }
                }
            }
            emit(TtsEvent.Completed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SherpaLoadException) {
            emit(TtsEvent.Error(VoiceCopy.neuralLoadFailed(model.label)))
        } catch (_: SherpaSynthesisException) {
            emit(TtsEvent.Error(VoiceCopy.neuralSynthesisFailed(model.label)))
        } catch (_: Exception) {
            emit(TtsEvent.Error(VoiceCopy.AUDIO_PLAYBACK_FAILED))
        }
    }

    /** Frees the loaded model; waits for an utterance in progress to finish first. */
    suspend fun release(model: SherpaTtsModel? = null) {
        lock.withLock {
            if (model == null || loadedModel == model) closeLoaded()
        }
    }

    private fun synthesizer(model: SherpaTtsModel, dir: File): SherpaSynthesizer {
        loaded?.takeIf { loadedModel == model }?.let { return it }
        closeLoaded()
        val synthesizer = try {
            factory.load(model, dir)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw SherpaLoadException(error.message ?: "load failed")
        }
        loaded = synthesizer
        loadedModel = model
        return synthesizer
    }

    private fun closeLoaded() {
        runCatching { loaded?.close() }
        loaded = null
        loadedModel = null
    }

    companion object {
        /** Chunks of synthesised audio buffered ahead of playback. */
        internal const val CHUNK_CAPACITY = 64
        val SUPERTONIC_LANGUAGES = setOf("en", "ko", "es", "pt", "fr")
        const val PREVIEW_TEXT = "This is a preview of your assistant voice."
    }
}

/** ISO 639-1 language Supertonic should use for [languageTag]; unsupported or blank falls back to English. */
internal fun supertonicLanguage(languageTag: String?, default: Locale = Locale.getDefault()): String {
    val language = languageTag?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { Locale.forLanguageTag(it).language }?.takeIf { it.isNotEmpty() }
        ?: default.language
    return language.lowercase(Locale.ROOT).takeIf { it in SherpaTtsBackend.SUPERTONIC_LANGUAGES } ?: "en"
}

/** Converts normalised float samples to signed 16-bit little-endian PCM, clamping out-of-range values. */
internal fun pcm16(samples: FloatArray): ByteArray {
    val out = ByteArray(samples.size * 2)
    for (i in samples.indices) {
        val value = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
        out[2 * i] = (value and 0xff).toByte()
        out[2 * i + 1] = ((value shr 8) and 0xff).toByte()
    }
    return out
}
