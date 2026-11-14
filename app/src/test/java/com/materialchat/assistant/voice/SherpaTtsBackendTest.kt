package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SherpaTtsBackendTest {
    @get:Rule val folder = TemporaryFolder()
    private val model = SherpaTtsModel.SUPERTONIC
    private val settings = AssistantVoiceSettings(backend = AssistantSpeechBackend.SHERPA, speechRate = 1.5f, languageTag = "ko-KR")

    private fun store(installed: Boolean): SherpaModelStore {
        val root = folder.newFolder()
        if (installed) {
            val dir = File(root, model.id).apply { mkdirs() }
            model.files.forEach { File(dir, it).writeText("x") }
            File(dir, SherpaModelStore.MANIFEST).writeText(model.manifest)
        }
        return SherpaModelStore(root, { _, _, _, _ -> error("unused") }, kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
    }

    /** Produces [chunks] sentences of audio, one callback each, honoring the stop signal. */
    private class FakeSynthesizer(private val chunks: Int, private val stepMillis: Long = 0) : SherpaSynthesizer {
        override val sampleRate = 24_000
        val closed = AtomicBoolean(false)
        val stopped = AtomicBoolean(false)
        val produced = AtomicInteger(0)
        var lastSpeed = 0f
        var lastLanguage = ""
        override fun synthesize(text: String, speed: Float, language: String, onSamples: (FloatArray) -> Boolean) {
            lastSpeed = speed; lastLanguage = language
            repeat(chunks) { index ->
                if (stepMillis > 0) Thread.sleep(stepMillis)
                val samples = FloatArray(4) { (index + 1) / 10f }
                if (!onSamples(samples)) { stopped.set(true); return }
                produced.incrementAndGet()
            }
        }
        override fun close() { closed.set(true) }
    }

    private class RecordingSink : SherpaPcmSink {
        val played = mutableListOf<ByteArray>()
        var sampleRate = 0
        val started = CompletableDeferred<Unit>()
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun play(sampleRate: Int, first: ByteArray, rest: ReceiveChannel<ByteArray>) {
            this.sampleRate = sampleRate
            played += first
            started.complete(Unit)
            hold?.await()
            for (chunk in rest) played += chunk
        }
    }

    @Test fun `streams chunks in order, starts before synthesis ends and completes`() = runTest {
        val synthesizer = FakeSynthesizer(chunks = 3)
        val sink = RecordingSink()
        val backend = SherpaTtsBackend(store(true), { _, _ -> synthesizer }, sink)
        assertNull(backend.unavailableReason(model))
        val events = backend.speak("Hello there.", settings, model).toList()
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), events)
        assertEquals(24_000, sink.sampleRate)
        assertEquals(3, sink.played.size)
        // 0.1, 0.2, 0.3 scaled to signed 16-bit little-endian, in synthesis order.
        assertEquals(listOf(3276, 6553, 9830), sink.played.map { (it[0].toInt() and 0xff) or (it[1].toInt() shl 8) })
        assertEquals(1.5f, synthesizer.lastSpeed, 0f)
        assertEquals("ko", synthesizer.lastLanguage)
        assertFalse(synthesizer.closed.get())
        // The loaded model is reused for the next utterance, then freed on release.
        backend.speak("Again.", settings, model).toList()
        backend.release()
        assertTrue(synthesizer.closed.get())
    }

    // Real time: synthesis blocks a real thread, so the virtual clock of runTest would fire the timeouts at once.
    @Test fun `cancelling the collection stops synthesis promptly`() = runBlocking {
        val synthesizer = FakeSynthesizer(chunks = 1_000, stepMillis = 2)
        val sink = RecordingSink().apply { hold = CompletableDeferred() }
        val backend = SherpaTtsBackend(store(true), { _, _ -> synthesizer }, sink)
        val job = launch(Dispatchers.Default) { backend.speak("A very long answer.", settings, model).toList() }
        withTimeout(5_000) { sink.started.await() }
        job.cancel()
        withTimeout(5_000) { job.join() }
        withTimeout(5_000) { while (!synthesizer.stopped.get()) kotlinx.coroutines.delay(5) }
        assertTrue(synthesizer.produced.get() < 1_000)
        // The backend is usable again after a cancelled utterance.
        val next = FakeSynthesizer(chunks = 1)
        val replacement = SherpaTtsBackend(store(true), { _, _ -> next }, RecordingSink())
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), replacement.speak("Next.", settings, model).toList())
    }

    @Test fun `missing model reports the fallback reason instead of playing`() = runTest {
        val backend = SherpaTtsBackend(store(false), { _, _ -> fail("must not load"); error("") }, RecordingSink())
        assertTrue(backend.unavailableReason(model)!!.contains("isn't downloaded"))
        val events = backend.speak("Hello.", settings, model).toList()
        assertEquals(1, events.size)
        assertTrue((events.single() as TtsEvent.Error).message.contains("isn't downloaded"))
    }

    @Test fun `load failure and synthesis failure become user-facing errors without playback`() = runTest {
        val sink = RecordingSink()
        val loadFails = SherpaTtsBackend(store(true), { _, _ -> throw UnsatisfiedLinkError("libsherpa-onnx-jni.so") }, sink)
        val loadEvents = loadFails.speak("Hello.", settings, model).toList()
        assertTrue((loadEvents.single() as TtsEvent.Error).message.contains("could not be loaded"))
        assertTrue(sink.played.isEmpty())

        val broken = object : SherpaSynthesizer {
            override val sampleRate = 24_000
            override fun synthesize(text: String, speed: Float, language: String, onSamples: (FloatArray) -> Boolean) =
                throw IllegalStateException("native failure")
            override fun close() {}
        }
        val synthFails = SherpaTtsBackend(store(true), { _, _ -> broken }, sink)
        val events = synthFails.speak("Hello.", settings, model).toList()
        assertEquals(1, events.size)
        val message = (events.single() as TtsEvent.Error).message
        assertTrue(message.contains("failed to synthesise"))
        assertFalse(message.contains("native failure"))
        assertTrue(sink.played.isEmpty())
    }

    @Test fun `blank text completes without loading the model`() = runTest {
        val backend = SherpaTtsBackend(store(true), { _, _ -> fail("must not load"); error("") }, RecordingSink())
        assertEquals(listOf(TtsEvent.Completed), backend.speak("   ", settings, model).toList())
    }
}
