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

    @Test fun synthesisTimeoutIsSpeechErrorNotTurnCancellation() = kotlinx.coroutines.test.runTest {
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> awaitCancellation() },
            GeminiPcmPlayer { _, _ -> fail("Must not play") })
        val result = backend.speak("Hello", settings, "test-key").toList()
        assertTrue(result.single() is TtsEvent.Error)
        currentCoroutineContext().ensureActive()
    }

    @Test fun errorsNeverExposeSecrets() = runBlocking {
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> error("SECRET") }, GeminiPcmPlayer { _, _ -> fail() })
        val result = backend.speak("Hello", settings, "SECRET").toList().single() as TtsEvent.Error
        assertFalse(result.message.contains("SECRET"))
    }
    private fun partsResponse(vararg parts: String) =
        """{"candidates":[{"finishReason":"STOP","content":{"parts":[${parts.joinToString(",")}]}}]}""".toByteArray()

    private fun audioPart(bytes: ByteArray, mime: String = "audio/l16;rate=24000") =
        """{"inlineData":{"mimeType":"$mime","data":"${Base64.getEncoder().encodeToString(bytes)}"}}"""

    @Test fun concatenatesAudioAndIgnoresTextAndThoughts() = runBlocking {
        val body = partsResponse("""{"text":"explanation"}""", audioPart(byteArrayOf(1, 0)),
            """{"thought":true,"text":"thought"}""", audioPart(byteArrayOf(2, 0)))
        var borrowed: ByteArray? = null
        val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> body }, GeminiPcmPlayer { pcm, _ ->
            assertArrayEquals(byteArrayOf(1, 0, 2, 0), pcm)
            borrowed = pcm
        })
        assertEquals(listOf(TtsEvent.Started, TtsEvent.Completed), backend.speak("Hi", settings, "key").toList())
        assertTrue(body.all { it == 0.toByte() })
        assertTrue(borrowed!!.all { it == 0.toByte() })
    }

    @Test fun rejectsMixedUnsupportedMissingAndMalformedAudio() {
        val backend = GeminiTtsBackend(player = GeminiPcmPlayer { _, _ -> fail() })
        val valid = audioPart(byteArrayOf(1, 0))
        val invalid = listOf(partsResponse("""{"text":"only text"}"""), partsResponse(),
            partsResponse(valid, audioPart(byteArrayOf(2, 0), "audio/wav")),
            partsResponse(valid, audioPart(byteArrayOf(1))),
            partsResponse(valid, """{"inlineData":{"mimeType":"audio/l16;rate=24000","data":"!"}}"""),
            partsResponse(valid, """{"fileData":{"mimeType":"audio/l16"}}"""))
        invalid.forEach { body ->
            val failure = assertThrows(GeminiTtsException::class.java) { backend.decode(body) }
            assertEquals(GeminiTtsFailure.INVALID_AUDIO, failure.kind)
            assertNull(failure.cause)
        }
    }

    @Test fun enforcesAggregatePcmLimit() {
        val backend = GeminiTtsBackend(player = GeminiPcmPlayer { _, _ -> fail() })
        val half = audioPart(ByteArray(GeminiTtsBackend.MAX_PCM_BYTES / 2))
        val exact = backend.decode(partsResponse(half, half))
        assertEquals(GeminiTtsBackend.MAX_PCM_BYTES, exact.size)
        exact.fill(0)
        assertEquals(GeminiTtsFailure.INVALID_AUDIO,
            assertThrows(GeminiTtsException::class.java) {
                backend.decode(partsResponse(half, half, audioPart(byteArrayOf(1, 0))))
            }.kind)
    }

    @Test fun httpFailuresHaveDistinctSanitizedDiagnostics() = runBlocking {
        val statuses = mapOf(401 to GeminiTtsFailure.AUTHENTICATION, 403 to GeminiTtsFailure.PERMISSION,
            429 to GeminiTtsFailure.QUOTA, 404 to GeminiTtsFailure.MODEL_UNAVAILABLE,
            500 to GeminiTtsFailure.TRANSPORT)
        for ((status, expected) in statuses) {
            assertEquals(expected, GeminiTtsFailure.fromHttp(status))
            val backend = GeminiTtsBackend(GeminiTtsTransport { _, _, _ -> throw GeminiTtsException(expected) },
                GeminiPcmPlayer { _, _ -> fail() })
            val error = backend.speak("Hi", settings, "SECRET").toList().single() as TtsEvent.Error
            assertEquals(expected.diagnostic, error.message)
            assertFalse(error.message.contains("SECRET"))
        }
    }

}
