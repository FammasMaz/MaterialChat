package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.EncryptedPreferences
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
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

    @Test(expected = CancellationException::class)
    fun `explicit cancellation is not converted to speech error`() = runTest {
        val credentials = mockk<EncryptedPreferences>()
        coEvery { credentials.getAssistantGeminiTtsApiKey() } throws CancellationException("stop")
        AssistantSpeechRuntime(mockk(), mockk(), credentials).speak("Hello",
            AssistantVoiceSettings(backend = AssistantSpeechBackend.GEMINI)) { fail("No error event") }
    }
}
