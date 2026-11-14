package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.EncryptedPreferences
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AssistantSpeechRuntimeTest {
    @Test fun `credential failure reports sanitized speech error`() = runTest {
        val credentials = mockk<EncryptedPreferences>()
        coEvery { credentials.getAssistantGeminiTtsApiKey() } throws IllegalStateException("private-secret")
        val runtime = AssistantSpeechRuntime(mockk(), mockk(), credentials)
        val events = mutableListOf<TtsEvent>()
        runtime.speak("Hello", AssistantVoiceSettings(backend = AssistantSpeechBackend.GEMINI), events::add)
        assertEquals(1, events.size)
        assertTrue(events.single() is TtsEvent.Error)
        assertFalse(events.toString().contains("private-secret"))
    }

    @Test fun `engine exception reports error while caller stays active`() = runTest {
        val android = mockk<TextToSpeechManager>()
        every { android.initialize() } returns flow { throw IllegalStateException("engine detail") }
        val events = mutableListOf<TtsEvent>()
        AssistantSpeechRuntime(android, mockk(), mockk()).speak("Hello", AssistantVoiceSettings(), events::add)
        assertTrue(events.single() is TtsEvent.Error)
        assertFalse(events.toString().contains("engine detail"))
    }

    private val sherpaSettings = AssistantVoiceSettings(backend = AssistantSpeechBackend.SHERPA)

    private fun androidThatSpeaks(spoken: MutableList<String>): TextToSpeechManager {
        val android = mockk<TextToSpeechManager>()
        every { android.initialize() } returns flowOf(TtsInitResult.Success)
        every { android.configure(any()) } returns flowOf(TtsInitResult.Success)
        every { android.enqueue(any()) } answers { spoken += firstArg<String>(); flowOf(TtsEvent.Started, TtsEvent.Completed) }
        return android
    }

    @Test fun `sherpa without a backend falls back to android with a notice`() = runTest {
        val spoken = mutableListOf<String>()
        val events = mutableListOf<TtsEvent>()
        AssistantSpeechRuntime(androidThatSpeaks(spoken), mockk(), mockk(), sherpa = null).speak("Hi", sherpaSettings, events::add)
        assertEquals(listOf("Hi"), spoken)
        assertTrue(events.first() is TtsEvent.Notice)
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), events.drop(1))
    }

    @Test fun `sherpa model not installed falls back to android and says why`() = runTest {
        val sherpa = mockk<SherpaTtsBackend>()
        every { sherpa.unavailableReason(any()) } returns "Supertonic (fast) isn't downloaded."
        val spoken = mutableListOf<String>()
        val events = mutableListOf<TtsEvent>()
        AssistantSpeechRuntime(androidThatSpeaks(spoken), mockk(), mockk(), sherpa).speak("Hi", sherpaSettings, events::add)
        assertEquals(listOf("Hi"), spoken)
        assertEquals("Supertonic (fast) isn't downloaded.", (events.first() as TtsEvent.Notice).message)
        verify(exactly = 0) { sherpa.speak(any(), any(), any()) }
    }

    @Test fun `sherpa failure before audio starts falls back, failure after is an error`() = runTest {
        val sherpa = mockk<SherpaTtsBackend>()
        every { sherpa.unavailableReason(any()) } returns null
        every { sherpa.speak(any(), any(), any()) } returns flowOf(TtsEvent.Error("Supertonic (fast) could not be loaded."))
        val spoken = mutableListOf<String>()
        val events = mutableListOf<TtsEvent>()
        val runtime = AssistantSpeechRuntime(androidThatSpeaks(spoken), mockk(), mockk(), sherpa)
        runtime.speak("Hi", sherpaSettings, events::add)
        assertEquals(listOf("Hi"), spoken)
        assertTrue((events.first() as TtsEvent.Notice).message.contains("Using the Android voice"))
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), events.drop(1))

        every { sherpa.speak(any(), any(), any()) } returns flowOf(TtsEvent.Started, TtsEvent.Error("Audio playback failed."))
        spoken.clear(); events.clear()
        runtime.speak("Hi", sherpaSettings, events::add)
        assertTrue(spoken.isEmpty())
        assertEquals(listOf<TtsEvent>(TtsEvent.Started, TtsEvent.Error("Audio playback failed.")), events)
    }

    @Test fun `sherpa success never touches android`() = runTest {
        val sherpa = mockk<SherpaTtsBackend>()
        every { sherpa.unavailableReason(any()) } returns null
        every { sherpa.speak(any(), any(), any()) } returns flowOf(TtsEvent.Started, TtsEvent.Completed)
        val android = mockk<TextToSpeechManager>()
        val events = mutableListOf<TtsEvent>()
        AssistantSpeechRuntime(android, mockk(), mockk(), sherpa).speak("Hi", sherpaSettings, events::add)
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), events)
        verify(exactly = 0) { android.initialize() }
    }

    @Test(expected = CancellationException::class)
    fun `explicit cancellation is not converted to speech error`() = runTest {
        val credentials = mockk<EncryptedPreferences>()
        coEvery { credentials.getAssistantGeminiTtsApiKey() } throws CancellationException("stop")
        AssistantSpeechRuntime(mockk(), mockk(), credentials).speak("Hello",
            AssistantVoiceSettings(backend = AssistantSpeechBackend.GEMINI)) { fail("No error event") }
    }
}
