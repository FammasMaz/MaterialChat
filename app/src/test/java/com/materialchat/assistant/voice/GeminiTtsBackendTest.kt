package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class GeminiTtsBackendTest {
    private val settings = AssistantVoiceSettings(geminiModel = "gemini-3.1-flash-tts-preview", geminiVoice = "Kore")
    private fun response(data: ByteArray = byteArrayOf(1, 0), mime: String = "audio/l16;codec=pcm;rate=24000", finish: String = "STOP") =
        """{"candidates":[{"finishReason":"$finish","content":{"parts":[{"inlineData":{"mimeType":"$mime","data":"${Base64.getEncoder().encodeToString(data)}"}}]}}]}""".toByteArray()

    @Test fun playsValidatedPcmAndWipesBorrowedBuffer() = runBlocking {
        var borrowed: ByteArray? = null
        val backend = GeminiTtsBackend(GeminiTtsTransport { model, key, json ->
            assertEquals(settings.geminiModel, model)
            assertEquals("test-key", key)
            assertTrue(json.contains("voiceName"))
            response()
        }, GeminiPcmPlayer { pcm, rate ->
            assertEquals(24000, rate)
            assertArrayEquals(byteArrayOf(1, 0), pcm)
            borrowed = pcm
        })
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), backend.speak("Hello", settings, "test-key").toList())
        assertArrayEquals(byteArrayOf(0, 0), borrowed)
    }

    @Test fun rejectsInvalidAudioBeforePlayback() = runBlocking {
        val invalid = listOf(response(byteArrayOf()), response(byteArrayOf(1)), response(mime = "audio/wav"),
            response(finish = "MAX_TOKENS"), "{}".toByteArray(), ByteArray(GeminiTtsBackend.MAX_RESPONSE_BYTES + 1))
        for (body in invalid) {
            val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> body }, GeminiPcmPlayer { _, _ -> fail("Must not play") })
            assertTrue(backend.speak("Hello", settings, "key").toList().single() is TtsEvent.Error)
        }
    }

    @Test fun rejectsInvalidInputsWithoutNetwork() = runBlocking {
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> error("Must not request") }, GeminiPcmPlayer { _, _ -> fail() })
        assertTrue(backend.speak("x".repeat(4001), settings, "key").toList().single() is TtsEvent.Error)
        assertTrue(backend.speak("Hello", settings.copy(geminiModel = "gemini-3.8-flash-tts"), "key").toList().single() is TtsEvent.Error)
        assertTrue(backend.speak("Hello", settings, "").toList().single() is TtsEvent.Error)
    }

    @Test fun cancellationReachesTransport() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var stopped = false
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ ->
            try { entered.complete(Unit); awaitCancellation() } finally { stopped = true }
        }, GeminiPcmPlayer { _, _ -> fail() })
        val job = launch { backend.speak("Hello", settings, "key").toList() }
        entered.await(); job.cancelAndJoin()
        assertTrue(stopped)
    }

    @Test fun cancellationReachesPlayerAndWipesPcm() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var borrowed: ByteArray? = null
        var stopped = false
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> response() }, GeminiPcmPlayer { pcm, _ ->
            borrowed = pcm
            try { entered.complete(Unit); awaitCancellation() } finally { stopped = true }
        })
        val job = launch { backend.speak("Hello", settings, "key").toList() }
        entered.await(); job.cancelAndJoin()
        assertTrue(stopped)
        assertArrayEquals(byteArrayOf(0, 0), borrowed)
    }

    @Test fun errorsNeverExposeSecrets() = runBlocking {
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> error("SECRET") }, GeminiPcmPlayer { _, _ -> fail() })
        val result = backend.speak("Hello", settings, "SECRET").toList().single() as TtsEvent.Error
        assertFalse(result.message.contains("SECRET"))
    }
}
