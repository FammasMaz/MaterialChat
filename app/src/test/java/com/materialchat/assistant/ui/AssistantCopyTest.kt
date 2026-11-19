package com.materialchat.assistant.ui

import com.materialchat.assistant.voice.SpeechRecognitionErrors
import com.materialchat.assistant.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The request-failure mapping: a provider's HTTP failure reaches the assistant as a plain
 * exception whose message is the parsed error body (ChatRepositoryImpl), so the one state a
 * user acts on differently (a rate limit: wait, then retry) is recognised from that text;
 * connectivity is an [IOException] anywhere in the chain; everything else stays generic,
 * including rejected keys named only in text (AssistantNoticesTest pins HTTP 401 in a body to
 * the generic copy). W4: a typed HTTP status (StreamingState.Error.httpStatus, read through the
 * ViewModel's adapter) is authoritative: 401/403 is the provider rejecting the key, 429 the
 * rate limit; the text path only ever adds the rate limit.
 */
class AssistantCopyTest {

    @Test fun connectivityIsNamedBeforeAnythingElse() {
        assertEquals(AssistantCopy.NO_CONNECTION, AssistantCopy.requestFailure(IOException("HTTP 429")))
        assertEquals(
            AssistantCopy.NO_CONNECTION,
            AssistantCopy.requestFailure(RuntimeException("wrapped", IOException("timeout")))
        )
    }

    @Test fun rateLimitsAreRecognisedFromTheProviderMessage() {
        val bodies = listOf(
            "HTTP 429: {\"error\":{\"message\":\"Too Many Requests\"}}",
            "Rate limit reached for gpt-4o in organization org-x on requests per min",
            "Resource has been exhausted (e.g. check quota).",
            "RESOURCE_EXHAUSTED",
            "You exceeded your current quota, please check your plan and billing details."
        )
        for (body in bodies) assertEquals(body, AssistantCopy.RATE_LIMITED, AssistantCopy.requestFailure(Exception(body)))
        // A nested cause carries it too.
        assertEquals(AssistantCopy.RATE_LIMITED, AssistantCopy.requestFailure(RuntimeException("stream", Exception("HTTP 429: {}"))))
    }

    @Test fun everythingElseIsGenericAndNeverTheExceptionText() {
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(null))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception("boom")))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception("HTTP 500: internal")))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception("HTTP 401: {\"error\":\"bad key\"}")))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception("HTTP 403: Forbidden")))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception()))
        for (body in listOf("boom", "HTTP 500: internal", "HTTP 401: bad key")) {
            assertFalse(AssistantCopy.requestFailure(Exception(body)).contains(body))
        }
    }

    @Test fun aCircularCauseChainTerminates() {
        val a = RuntimeException("a")
        val b = RuntimeException("HTTP 429", a)
        a.initCause(b)
        assertEquals(AssistantCopy.RATE_LIMITED, AssistantCopy.requestFailure(a))
    }

    @Test fun aTypedStatusIsAuthoritativeForRejectionAndRateLimit() {
        assertEquals(AssistantCopy.PROVIDER_REJECTED, AssistantCopy.requestFailure(Exception("{}"), httpStatus = 401))
        assertEquals(AssistantCopy.PROVIDER_REJECTED, AssistantCopy.requestFailure(Exception("Forbidden"), httpStatus = 403))
        assertEquals(AssistantCopy.PROVIDER_REJECTED, AssistantCopy.requestFailure(null, httpStatus = 401))
        assertEquals(AssistantCopy.RATE_LIMITED, AssistantCopy.requestFailure(Exception("{}"), httpStatus = 429))
        for (status in listOf(400, 404, 408, 500, 502, 503)) {
            assertEquals("$status", AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception("{}"), httpStatus = status))
        }
        // A typed status that is not one of the three leaves the text path in charge: a quota
        // body on a 5xx still names the limit.
        assertEquals(AssistantCopy.RATE_LIMITED, AssistantCopy.requestFailure(Exception("quota exceeded"), httpStatus = 500))
        // Connectivity outranks any status.
        assertEquals(AssistantCopy.NO_CONNECTION, AssistantCopy.requestFailure(IOException("x"), httpStatus = 401))
        // The rejection notice never carries the body.
        assertFalse(AssistantCopy.requestFailure(Exception("sk-live-123"), httpStatus = 401).contains("sk-live"))
    }

    @Test fun withoutATypedStatusTheTextPathNeverSelectsRejection() {
        for (body in listOf(
            "HTTP 401: {\"error\":\"bad key\"}", "HTTP 403: Forbidden", "Unauthorized",
            "Incorrect API key provided", "PERMISSION_DENIED", "invalid_api_key"
        )) {
            assertEquals(body, AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception(body)))
            assertEquals(body, AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(Exception(body), httpStatus = null))
        }
    }

    @Test fun aRejectedKeyOpensSettingsAndInvitesNoRetry() {
        // handleError mirrors the message into the voice state exactly as for any failure, and
        // the resolution still picks settings over the retry: re-sending cannot fix a key.
        val rejected = resolve(AssistantCopy.PROVIDER_REJECTED, VoiceState.Error(AssistantCopy.PROVIDER_REJECTED))
        assertTrue(rejected.isError)
        assertEquals(AssistantNoticeAction.OpenSettings, rejected.action)
        assertEquals(AssistantCopy.PROMPT, rejected.restingLine)
        // Distinct from the two states it must not be confused with.
        assertTrue(AssistantCopy.PROVIDER_REJECTED != AssistantCopy.RATE_LIMITED)
        assertTrue(AssistantCopy.PROVIDER_REJECTED != AssistantCopy.NO_CONNECTION)
        assertTrue(AssistantNoticeAction.OpenSettings.label != AssistantNoticeAction.OpenApp.label)
    }

    @Test fun theErrorVerbIsTryAgainNeverRetry() {
        // W5 orchestrator ruling: "Try again" is the error verb. A change to "Retry" fails here.
        assertEquals("Try again", AssistantCopy.ERROR_VERB)
        assertEquals(AssistantCopy.ERROR_VERB, AssistantCopy.AFTER_ERROR)
        assertEquals(AssistantCopy.ERROR_VERB, AssistantNoticeAction.RetryRequest.label)
        assertEquals(AssistantCopy.ERROR_VERB, AssistantNoticeAction.RetryListening.label)
        val everything = AssistantCopy.NOTICES + AssistantCopy.PHRASES +
            AssistantNoticeAction.values().map { it.label } +
            listOf(
                SpeechRecognitionErrors.ERROR_NO_MATCH, SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT,
                SpeechRecognitionErrors.ERROR_NETWORK, SpeechRecognitionErrors.ERROR_AUDIO,
                SpeechRecognitionErrors.ERROR_SERVER, SpeechRecognitionErrors.ERROR_RECOGNIZER_BUSY,
                SpeechRecognitionErrors.ERROR_CLIENT
            ).map { AssistantCopy.recognitionError(it, "") }
        for (text in everything) {
            assertFalse(text, text.contains("retry", ignoreCase = true))
        }
        // The verb inside a sentence is the same verb (lower case mid-sentence, capitalised when
        // it opens the sentence, as in REQUEST_FAILED's "Try again.").
        for (text in listOf(AssistantCopy.NO_CONNECTION, AssistantCopy.RATE_LIMITED, AssistantCopy.REQUEST_FAILED)) {
            assertTrue(text, text.contains("try again", ignoreCase = true))
        }
    }

    @Test fun noticesAreSentencesWithFullStopsAndPhrasesCarryNone() {
        // W5 orchestrator ruling: one-phrase notices no period, multi-sentence full stops.
        for (notice in AssistantCopy.NOTICES) {
            assertTrue(notice, notice.endsWith("."))
            assertTrue(notice, notice.first().isUpperCase())
            // Every sentence boundary is a full stop followed by a space and a capital.
            for (sentence in notice.split(". ")) assertTrue("$notice -> $sentence", sentence.first().isUpperCase())
            assertFalse(notice, notice.contains(".."))
            assertFalse(notice, notice.contains(" ."))
        }
        for (phrase in AssistantCopy.PHRASES) {
            assertFalse(phrase, phrase.endsWith("."))
            assertTrue(phrase, phrase.first().isUpperCase())
        }
        for (label in AssistantNoticeAction.values().map { it.label }) {
            assertFalse(label, label.endsWith("."))
            assertTrue(label, label.first().isUpperCase())
            assertTrue(label, label.drop(1).none { it.isUpperCase() })
        }
        // The recognizer table: every entry is a sentence or two with full stops.
        for (code in listOf(
            SpeechRecognitionErrors.ERROR_NO_MATCH, SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT,
            SpeechRecognitionErrors.ERROR_NETWORK, SpeechRecognitionErrors.ERROR_AUDIO,
            SpeechRecognitionErrors.ERROR_SERVER, SpeechRecognitionErrors.ERROR_RECOGNIZER_BUSY,
            SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS, SpeechRecognitionErrors.ERROR_CLIENT
        )) {
            val text = AssistantCopy.recognitionError(code, "")
            assertTrue(text, text.endsWith("."))
            assertFalse(text, text.contains("please", ignoreCase = true))
        }
    }

    @Test fun everyNewNoticeFollowsTheCopyRules() {
        for (notice in listOf(AssistantCopy.RATE_LIMITED, AssistantCopy.PROVIDER_REJECTED)) {
            assertTrue(notice, notice.endsWith("."))
            assertFalse(notice, notice.contains("please", ignoreCase = true))
            assertTrue(notice, notice.first().isUpperCase())
        }
        // Chip labels: sentence-case verbs, no period.
        for (label in listOf(
            AssistantCopy.OPEN_IN_APP, AssistantCopy.CONTINUE_IN_APP,
            AssistantCopy.TAKE_PHOTO, AssistantCopy.ATTACH_PHOTO, AssistantCopy.SCREEN_CHIP
        )) {
            assertTrue(label, label.first().isUpperCase())
            assertFalse(label, label.endsWith("."))
            assertTrue(label, label.drop(1).none { it.isUpperCase() })
        }
    }

    private fun resolve(error: String, voiceError: VoiceState.Error?) = AssistantNotices.resolve(
        error = error,
        notice = null,
        voiceError = voiceError,
        hasQuestion = true,
        screenContextUnavailable = false,
        screenUnavailableNotice = "",
        hasMicPermission = true,
        voiceInputEnabled = true,
        keyboardMode = false
    )!!

    @Test fun aRateLimitInvitesTheRetry() {
        // handleError mirrors the message into the voice state, which is what offers the retry.
        val limited = resolve(AssistantCopy.RATE_LIMITED, VoiceState.Error(AssistantCopy.RATE_LIMITED))
        assertTrue(limited.isError)
        assertEquals(AssistantNoticeAction.RetryRequest, limited.action)
        assertEquals(AssistantCopy.AFTER_ERROR, limited.restingLine)
    }
}
