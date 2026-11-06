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
    private val speak: suspend (String) -> Unit
) {
    private val chunks = Channel<String>(64)
    val job: Job = scope.launch { for (chunk in chunks) speak(chunk) }
    suspend fun enqueue(text: String) {
        // Both backends accept bounded utterances. Never split a surrogate pair.
        var remaining = text
        while (remaining.isNotBlank()) {
            var end = minOf(remaining.length, 3500)
            if (end < remaining.length && remaining[end - 1].isHighSurrogate()) end--
            try {
                chunks.send(remaining.take(end))
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                return // Speech stopped; generation remains owned by its live parent.
            } catch (_: ClosedSendChannelException) {
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
    }
}
