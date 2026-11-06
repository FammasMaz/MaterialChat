package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TextToSpeechManagerTest {
    private class Fake(val callbacks: TtsCallbacks) : TtsEngine {
        val ids = mutableListOf<String>()
        var stops = 0
        var shutdowns = 0
        var selected = ""
        var speed = 0f
        override fun voices() = listOf(TtsVoice("voice", "fr-FR"))
        override fun language(locale: Locale) = locale.language == "fr"
        override fun voice(id: String): Boolean { selected = id; return true }
        override fun rate(value: Float): Boolean { speed = value; return true }
        override fun pitch(value: Float) = true
        override fun speak(text: String, id: String): Boolean { ids += id; return true }
        override fun stop() { stops++ }
        override fun shutdown() { shutdowns++ }
    }

    @Test fun `synchronous initialization and independent chunk completion`() = runTest {
        lateinit var fake: Fake
        val manager = TextToSpeechManager({ it() }) { callbacks ->
            Fake(callbacks).also { fake = it; callbacks.initialized(true) }
        }
        assertEquals(listOf(TtsInitResult.Success), manager.initialize().toList())
        val first = async { manager.enqueue("First").toList() }
        val second = async { manager.enqueue("Second").toList() }
        runCurrent()
        fake.callbacks.finished(fake.ids[0])
        runCurrent()
        assertEquals(listOf(TtsEvent.Completed), first.await())
        assertFalse(second.isCompleted)
        assertTrue(manager.isSpeaking.value)
        assertEquals(0, fake.stops)
        fake.callbacks.finished(fake.ids[1])
        assertEquals(listOf(TtsEvent.Completed), second.await())
        assertFalse(manager.isSpeaking.value)
    }

    @Test fun `cancellation drains all queued collectors and ignores stale callbacks`() = runTest {
        lateinit var fake: Fake
        val manager = TextToSpeechManager({ it() }) { Fake(it).also { f -> fake = f } }
        val init = async { manager.initialize().toList() }
        runCurrent(); fake.callbacks.initialized(true); init.await()
        val first = async { manager.enqueue("First").toList() }
        val second = async { manager.enqueue("Second").toList() }
        runCurrent()
        first.cancelAndJoin()
        assertEquals(listOf(TtsEvent.Stopped), second.await())
        assertEquals(1, fake.stops)
        fake.callbacks.started(fake.ids[0])
        fake.callbacks.finished(fake.ids[0])
        assertFalse(manager.isSpeaking.value)
    }

    @Test fun `initialization timeout shuts down engine and permits retry`() = runTest {
        val engines = mutableListOf<Fake>()
        val manager = TextToSpeechManager({ it() }) { Fake(it).also(engines::add) }
        assertTrue(manager.initialize().toList().single() is TtsInitResult.Error)
        assertEquals(1, engines.single().shutdowns)
        val retry = async { manager.initialize().toList() }
        runCurrent()
        engines.last().callbacks.initialized(true)
        assertEquals(listOf(TtsInitResult.Success), retry.await())
    }

    @Test fun `replacement stops prior collectors and release resolves pending initialization`() = runTest {
        lateinit var fake: Fake
        val manager = TextToSpeechManager({ it() }) { Fake(it).also { f -> fake = f } }
        val init = async { manager.initialize().toList() }
        runCurrent()
        manager.release()
        assertTrue(init.await().single() is TtsInitResult.Error)
        val retry = async { manager.initialize().toList() }
        runCurrent(); fake.callbacks.initialized(true); retry.await()
        val first = async { manager.enqueue("First").toList() }
        runCurrent()
        val replacement = async { manager.speak("Replacement").toList() }
        runCurrent()
        assertEquals(listOf(TtsEvent.Stopped), first.await())
        fake.callbacks.finished(fake.ids.last())
        assertEquals(listOf(TtsEvent.Completed), replacement.await())
    }

    @Test fun `shared initialization failure retry release and settings`() = runTest {
        val engines = mutableListOf<Fake>()
        val manager = TextToSpeechManager({ it() }) { Fake(it).also(engines::add) }
        val first = async { manager.initialize().toList() }
        val second = async { manager.initialize().toList() }
        runCurrent()
        assertEquals(1, engines.size)
        engines[0].callbacks.initialized(false)
        assertTrue(first.await().single() is TtsInitResult.Error)
        assertEquals(first.await(), second.await())
        assertEquals(1, engines[0].shutdowns)
        val retry = async { manager.initialize().toList() }
        runCurrent()
        engines[0].callbacks.initialized(true)
        assertFalse(retry.isCompleted)
        engines[1].callbacks.initialized(true)
        assertEquals(listOf(TtsInitResult.Success), retry.await())
        assertEquals(listOf(TtsVoice("voice", "fr-FR")), manager.installedVoices.value)
        assertEquals(listOf(TtsInitResult.Success), manager.configure(AssistantVoiceSettings(
            androidVoiceId = "voice", languageTag = "fr-FR", speechRate = 0.25f
        )).toList())
        assertEquals("voice", engines[1].selected)
        assertEquals(0.25f, engines[1].speed)
        assertTrue(manager.configure(AssistantVoiceSettings(androidVoiceId = "missing")).toList().single() is TtsInitResult.Error)
        manager.release()
        assertEquals(1, engines[1].shutdowns)
        assertTrue(manager.installedVoices.value.isEmpty())
    }
}
