package com.materialchat.assistant.voice

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Drives the production session orchestration without a recognition service or wall-clock sleeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeechRecognitionManagerTest {
    private class Engine {
        val recognizer = mockk<SpeechRecognizer>(relaxed = true)
        lateinit var listener: RecognitionListener
        init {
            every { recognizer.setRecognitionListener(any()) } answers { listener = firstArg() }
        }
        fun result(text: String) {
            val bundle = mockk<Bundle>()
            every { bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf(text)
            listener.onResults(bundle)
        }
    }
    private class Platform : RecognitionPlatform {
        val engines = mutableListOf<Engine>()
        val posted = ArrayDeque<() -> Unit>()
        val deadlines = linkedMapOf<Runnable, Long>()
        var now = 0L
        var queueDispatch = false
        var fallbackAvailable = false
        var fallbackCalls = 0
        var creationFailure: RuntimeException? = null
        override fun isAvailable() = true
        override fun createRecognizer(): SpeechRecognizer {
            creationFailure?.let { throw it }
            return Engine().also { engines += it }.recognizer
        }
        override fun createFallbackRecognizer(): SpeechRecognizer? {
            fallbackCalls++
            return if (fallbackAvailable) Engine().also { engines += it }.recognizer else null
        }
        override fun dispatch(action: () -> Unit) { if (queueDispatch) posted += action else action() }
        override fun post(action: () -> Unit) { posted += action }
        override fun postDelayed(action: Runnable, delayMillis: Long) { deadlines[action] = now + delayMillis }
        override fun removeCallbacks(action: Runnable) { deadlines.remove(action) }
        fun drain() { while (posted.isNotEmpty()) posted.removeFirst()() }
        fun advance(millis: Long) {
            now += millis
            deadlines.filterValues { it <= now }.keys.toList().forEach { deadlines.remove(it); it.run() }
        }
    }
    private fun TestScope.setup(): Pair<Platform, SpeechRecognitionManager> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val platform = Platform()
        return platform to SpeechRecognitionManager(platform)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `stop retains final transcript and removes timeout`() = runTest {
        val (platform, manager) = setup()
        val result = async { manager.startListening().toList() }
        runCurrent()
        val engine = platform.engines.single()
        engine.listener.onReadyForSpeech(null)
        manager.stopListening()
        manager.stopListening()
        verify(exactly = 1) { engine.recognizer.stopListening() }
        verify(exactly = 0) { engine.recognizer.cancel() }
        assertFalse(result.isCompleted)
        assertEquals(listOf(8_000L), platform.deadlines.values.toList())
        engine.result("final after stop")
        assertEquals(listOf(SpeechRecognitionResult.Ready, SpeechRecognitionResult.FinalResult("final after stop")), result.await())
        assertFalse(manager.isListening.value)
        assertTrue(platform.deadlines.isEmpty())
        verify(exactly = 1) { engine.recognizer.cancel() }
        verify(exactly = 1) { engine.recognizer.destroy() }
        platform.drain()
        verify(exactly = 1) { engine.recognizer.destroy() }
    }

    @Test fun `stop exception emits sanitized terminal error`() = runTest {
        val (platform, manager) = setup()
        val result = async { manager.startListening().toList() }
        runCurrent()
        val engine = platform.engines.single()
        every { engine.recognizer.stopListening() } throws IllegalStateException("private provider details")
        manager.stopListening()
        assertEquals(listOf(SpeechRecognitionResult.Error("Could not finish speech recognition. Please try again.", SpeechRecognitionErrors.ERROR_CLIENT)), result.await())
        assertTrue(platform.deadlines.isEmpty())
        assertFalse(manager.isListening.value)
        verify(exactly = 1) { engine.recognizer.destroy() }
    }

    @Test fun `missing final callback after stop terminates at eight seconds`() = runTest {
        val (platform, manager) = setup()
        val result = async { manager.startListening().toList() }
        runCurrent()
        manager.stopListening()
        platform.advance(7_999)
        runCurrent()
        assertFalse(result.isCompleted)
        platform.advance(1)
        assertEquals(listOf(SpeechRecognitionResult.Error("Speech recognition did not finish. Please try again.", SpeechRecognitionErrors.ERROR_CLIENT)), result.await())
        assertFalse(manager.isListening.value)
        verify(exactly = 1) { platform.engines.single().recognizer.destroy() }
    }

    @Test fun `end of speech starts bounded timeout without extending it`() = runTest {
        val (platform, manager) = setup()
        val result = async { manager.startListening().toList() }
        runCurrent()
        val engine = platform.engines.single()
        engine.listener.onEndOfSpeech()
        platform.advance(4_000)
        engine.listener.onEndOfSpeech()
        assertEquals(listOf(8_000L), platform.deadlines.values.toList())
        platform.advance(4_000)
        val events = result.await()
        assertEquals(2, events.count { it == SpeechRecognitionResult.SpeechEnded })
        assertEquals(SpeechRecognitionResult.Error("Speech recognition did not finish. Please try again.", SpeechRecognitionErrors.ERROR_CLIENT), events.last())
    }

    @Test fun `replacement ignores stale callbacks and old collector cleanup`() = runTest {
        val (platform, manager) = setup()
        val first = async { manager.startListening().toList() }
        runCurrent()
        val old = platform.engines.single()
        old.listener.onReadyForSpeech(null)
        manager.stopListening()
        val second = async { manager.startListening().toList() }
        runCurrent()
        val current = platform.engines.last()
        current.listener.onReadyForSpeech(null)
        assertTrue(manager.isListening.value)
        old.listener.onReadyForSpeech(null)
        old.listener.onEndOfSpeech()
        old.listener.onError(SpeechRecognitionErrors.ERROR_CLIENT)
        old.listener.onPartialResults(null)
        old.result("obsolete")
        platform.drain()
        assertTrue(manager.isListening.value)
        assertFalse(second.isCompleted)
        assertTrue(platform.deadlines.isEmpty())
        verify(exactly = 0) { current.recognizer.cancel() }
        assertEquals(listOf(SpeechRecognitionResult.Ready), first.await())
        current.result("current")
        assertEquals(listOf(SpeechRecognitionResult.Ready, SpeechRecognitionResult.FinalResult("current")), second.await())
    }

    @Test fun `cancel ignores late results and delayed old stop cannot stop successor`() = runTest {
        val (platform, manager) = setup()
        val first = async { manager.startListening().toList() }
        runCurrent()
        val old = platform.engines.single()
        platform.queueDispatch = true
        manager.stopListening()
        manager.cancel()
        platform.queueDispatch = false
        manager.cancel()
        old.listener.onReadyForSpeech(null)
        old.result("cancelled")
        assertTrue(first.await().isEmpty())
        val second = async { manager.startListening().toList() }
        runCurrent()
        val current = platform.engines.last()
        current.listener.onReadyForSpeech(null)
        platform.drain()
        assertTrue(manager.isListening.value)
        verify(exactly = 0) { current.recognizer.stopListening() }
        verify(exactly = 0) { current.recognizer.cancel() }
        current.result("successor")
        assertEquals(SpeechRecognitionResult.FinalResult("successor"), second.await().last())
    }

    @Test fun `silent startup terminates without leaving pending callbacks`() = runTest {
        val (platform, manager) = setup()
        val result = async { manager.startListening().toList() }
        runCurrent()
        platform.advance(7_999)
        runCurrent()
        assertFalse(result.isCompleted)
        platform.advance(1)
        assertEquals("Speech recognizer did not start. Please try again.",
            (result.await().single() as SpeechRecognitionResult.Error).message)
        assertEquals(1, platform.fallbackCalls)
        assertTrue(platform.deadlines.isEmpty())
        verify(exactly = 1) { platform.engines.single().recognizer.destroy() }
    }

    @Test fun `invalid startup retries once and ignores the old engine callbacks`() = runTest {
        val (platform, manager) = setup()
        platform.fallbackAvailable = true
        val result = async { manager.startListening().toList() }
        runCurrent()
        val old = platform.engines.single()
        old.listener.onError(10) // Android can also report an invalid service with this code.
        val current = platform.engines.last()
        assertEquals(2, platform.engines.size)
        old.listener.onReadyForSpeech(null)
        old.result("obsolete")
        old.listener.onError(SpeechRecognitionErrors.ERROR_CLIENT)
        assertFalse(manager.isListening.value)
        current.listener.onReadyForSpeech(null)
        current.result("fallback transcript")
        assertEquals(listOf(SpeechRecognitionResult.Ready,
            SpeechRecognitionResult.FinalResult("fallback transcript")), result.await())
        assertEquals(1, platform.fallbackCalls)
        assertTrue(platform.deadlines.isEmpty())
        verify(exactly = 1) { old.recognizer.destroy() }
        verify(exactly = 1) { current.recognizer.destroy() }
    }

    @Test fun `two silent engines have a bounded total startup time`() = runTest {
        val (platform, manager) = setup()
        platform.fallbackAvailable = true
        val result = async { manager.startListening().toList() }
        runCurrent()
        platform.advance(8_000)
        runCurrent()
        assertEquals(2, platform.engines.size)
        assertFalse(result.isCompleted)
        platform.advance(8_000)
        assertTrue(result.await().single() is SpeechRecognitionResult.Error)
        assertEquals(1, platform.fallbackCalls)
        assertTrue(platform.deadlines.isEmpty())
        platform.engines.forEach { verify(exactly = 1) { it.recognizer.destroy() } }
    }

    @Test fun `creation failure can recover through the one fallback`() = runTest {
        val (platform, manager) = setup()
        platform.creationFailure = IllegalStateException("private service internals")
        platform.fallbackAvailable = true
        val result = async { manager.startListening().toList() }
        runCurrent()
        platform.engines.single().result("recovered")
        assertEquals(listOf(SpeechRecognitionResult.FinalResult("recovered")), result.await())
        assertEquals(1, platform.fallbackCalls)
    }

    @Test fun `permission and busy errors must not try another engine`() = runTest {
        for (code in listOf(9, 8, 6, 7)) {
            val (platform, manager) = setup()
            platform.fallbackAvailable = true
            val result = async { manager.startListening().toList() }
            runCurrent()
            platform.engines.single().listener.onError(code)
            assertEquals(code, (result.await().single() as SpeechRecognitionResult.Error).errorCode)
            assertEquals(0, platform.fallbackCalls)
            assertTrue(platform.deadlines.isEmpty())
        }
    }

    @Test fun `error after ready cannot restart and truncate the utterance`() = runTest {
        val (platform, manager) = setup()
        platform.fallbackAvailable = true
        val result = async { manager.startListening().toList() }
        runCurrent()
        platform.engines.single().listener.onReadyForSpeech(null)
        platform.engines.single().listener.onError(SpeechRecognitionErrors.ERROR_SERVER)
        assertEquals(SpeechRecognitionErrors.ERROR_SERVER,
            (result.await().last() as SpeechRecognitionResult.Error).errorCode)
        assertEquals(0, platform.fallbackCalls)
        assertTrue(platform.deadlines.isEmpty())
    }

    @Test fun `stop before ready never falls back or leaves the startup deadline`() = runTest {
        val (platform, manager) = setup()
        platform.fallbackAvailable = true
        val result = async { manager.startListening().toList() }
        runCurrent()
        manager.stopListening()
        platform.engines.single().listener.onError(SpeechRecognitionErrors.ERROR_CLIENT)
        assertTrue(result.await().single() is SpeechRecognitionResult.Error)
        assertEquals(0, platform.fallbackCalls)
        assertTrue(platform.deadlines.isEmpty())
    }

    @Test fun `cancel during fallback prevents all late callbacks and timers`() = runTest {
        val (platform, manager) = setup()
        platform.fallbackAvailable = true
        val result = async { manager.startListening().toList() }
        runCurrent()
        platform.advance(8_000)
        manager.cancel()
        val current = platform.engines.last()
        current.listener.onReadyForSpeech(null)
        current.result("cancelled fallback")
        platform.advance(16_000)
        assertTrue(result.await().isEmpty())
        assertTrue(platform.deadlines.isEmpty())
        assertFalse(manager.isListening.value)
    }

    @Test fun `cancelled collector queued cleanup cannot reset replacement state`() = runTest {
        val (platform, manager) = setup()
        val first = async { manager.startListening().toList() }
        runCurrent()
        first.cancelAndJoin()
        assertTrue(platform.posted.isNotEmpty())
        val second = async { manager.startListening().toList() }
        runCurrent()
        val current = platform.engines.last()
        current.listener.onReadyForSpeech(null)
        platform.drain()
        assertTrue(manager.isListening.value)
        verify(exactly = 0) { current.recognizer.cancel() }
        current.result("retained")
        assertEquals(SpeechRecognitionResult.FinalResult("retained"), second.await().last())
    }
}
