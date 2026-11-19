package com.materialchat.assistant.ui

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
 * including rejected keys (AssistantNoticesTest pins HTTP 401 to the generic copy).
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

    @Test fun everyNewNoticeFollowsTheCopyRules() {
        for (notice in listOf(AssistantCopy.RATE_LIMITED)) {
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
