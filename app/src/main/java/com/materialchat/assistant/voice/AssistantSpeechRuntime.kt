package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.EncryptedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A single consumer preserves utterance order without blocking model generation. */
internal class AssistantSpeechQueue(
    scope: CoroutineScope,
    private val onDrained: () -> Unit = {},
    private val speak: suspend (String) -> Unit
) {
    private val chunks = Channel<String>(64)
    private val pendingCount = java.util.concurrent.atomic.AtomicInteger(0)
    /** Chunks enqueued and not yet finished speaking, including the one being spoken. */
    val pending: Int get() = pendingCount.get()
    val job: Job = scope.launch {
        for (chunk in chunks) {
            try { speak(chunk) } finally { pendingCount.decrementAndGet() }
        }
        onDrained()
    }
    suspend fun enqueue(text: String) {
        // Both backends accept bounded utterances. Never split a surrogate pair.
        var remaining = text
        while (remaining.isNotBlank()) {
            var end = minOf(remaining.length, 3500)
            if (end < remaining.length && remaining[end - 1].isHighSurrogate()) end--
            pendingCount.incrementAndGet()
            try {
                chunks.send(remaining.take(end))
            } catch (cancelled: CancellationException) {
                pendingCount.decrementAndGet()
                currentCoroutineContext().ensureActive()
                return // Speech stopped; generation remains owned by its live parent.
            } catch (_: ClosedSendChannelException) {
                pendingCount.decrementAndGet()
                return
            }
            remaining = remaining.drop(end)
        }
    }
    fun finish() { chunks.close() }
    fun cancel() { chunks.cancel(); job.cancel() }
}

class AssistantSpeechRuntime(
    private val android: TextToSpeechManager,
    private val gemini: GeminiTtsBackend,
    private val credentials: EncryptedPreferences
) {
    suspend fun speak(text: String, settings: AssistantVoiceSettings, event: (TtsEvent) -> Unit) {
        try {
            when (settings.backend) {
                AssistantSpeechBackend.ANDROID -> {
                    if (android.initialize().first() !is TtsInitResult.Success ||
                        android.configure(settings).first() !is TtsInitResult.Success) {
                        event(TtsEvent.Error("Android voice unavailable. Check voice settings."))
                        return
                    }
                    android.enqueue(text).collect(event)
                }
                AssistantSpeechBackend.GEMINI -> {
                    val key = credentials.getAssistantGeminiTtsApiKey()
                    if (key.isNullOrBlank()) {
                        event(TtsEvent.Error("Configure a Gemini speech key in voice settings."))
                        return
                    }
                    gemini.speak(text, settings, key).collect(event)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Credentials and engine exceptions must not cancel sibling generation or leak details.
            event(TtsEvent.Error("Speech unavailable. Check voice settings and retry."))
        }
    }
}
