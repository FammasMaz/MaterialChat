package com.materialchat.assistant.ui

import com.materialchat.assistant.voice.SpeechRecognitionErrors
import com.materialchat.assistant.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * The assistant's copy system and notice resolution: one phrase per failure, no engine wording,
 * one action per notice, and the pill's resting line following the notice.
 */
class AssistantNoticesTest {

    private fun resolve(
        error: String? = null,
        notice: String? = null,
        voiceError: VoiceState.Error? = null,
        hasQuestion: Boolean = false,
        screenUnavailable: Boolean = false,
        mic: Boolean = true,
        voiceEnabled: Boolean = true,
        keyboard: Boolean = false
    ) = AssistantNotices.resolve(
        error = error,
        notice = notice,
        voiceError = voiceError,
        hasQuestion = hasQuestion,
        screenContextUnavailable = screenUnavailable,
        screenUnavailableNotice = "screen notice",
        hasMicPermission = mic,
        voiceInputEnabled = voiceEnabled,
        keyboardMode = keyboard
    )

    @Test fun nothingWrongMeansNoNotice() {
        assertNull(resolve())
    }

    @Test fun aFailedRequestOffersARetryOfTheQuestion() {
        // handleError mirrors the message into the voice state; the question is on screen.
        val n = resolve(
            error = AssistantCopy.REQUEST_FAILED,
            voiceError = VoiceState.Error(AssistantCopy.REQUEST_FAILED),
            hasQuestion = true
        )!!
        assertTrue(n.isError)
        assertEquals(AssistantNoticeAction.RetryRequest, n.action)
        assertEquals(AssistantCopy.AFTER_ERROR, n.restingLine)
    }

    @Test fun aMissingProviderOpensTheApp() {
        val n = resolve(error = AssistantCopy.NO_PROVIDER, voiceError = VoiceState.Error(AssistantCopy.NO_PROVIDER), hasQuestion = true)!!
        assertEquals(AssistantNoticeAction.OpenApp, n.action)
        assertEquals(AssistantCopy.PROMPT, n.restingLine)
    }

    @Test fun aSpeechOutputFailureKeepsTheAnswerAndOffersNoAction() {
        // A TTS error sets uiState.error without touching the voice state: the answer is already
        // on screen, so re-sending the question would be wrong.
        val n = resolve(error = "Audio playback failed.", hasQuestion = true)!!
        assertTrue(n.isError)
        assertNull(n.action)
        assertEquals(AssistantCopy.PROMPT, n.restingLine)
    }

    @Test fun aRecognizerFailureOffersListeningAgain() {
        val n = resolve(voiceError = VoiceState.Error("Didn't catch that. Try again.", SpeechRecognitionErrors.ERROR_NO_MATCH))!!
        assertTrue(n.isError)
        assertEquals(AssistantNoticeAction.RetryListening, n.action)
        assertEquals(AssistantCopy.AFTER_ERROR, n.restingLine)
    }

    @Test fun permissionAndMissingRecognizerOfferTypingUnlessAlreadyTyping() {
        val denied = resolve(voiceError = VoiceState.Error(AssistantCopy.MIC_PERMISSION, SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS))!!
        assertEquals(AssistantNoticeAction.TypeInstead, denied.action)
        val missing = resolve(voiceError = VoiceState.Error(AssistantCopy.RECOGNIZER_UNAVAILABLE, SpeechRecognitionErrors.ERROR_CLIENT))!!
        assertEquals(AssistantNoticeAction.TypeInstead, missing.action)
        val typing = resolve(voiceError = VoiceState.Error(AssistantCopy.MIC_PERMISSION, SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS), keyboard = true)!!
        assertNull(typing.action)
        // Neither invites "Try again": the microphone is not coming back by retrying.
        assertEquals(AssistantCopy.PROMPT, denied.restingLine)
    }

    @Test fun standingLimitsAreHintsWithATypingAction() {
        val mic = resolve(mic = false)!!
        assertFalse(mic.isError)
        assertEquals(AssistantCopy.MIC_PERMISSION, mic.message)
        assertEquals(AssistantNoticeAction.TypeInstead, mic.action)
        val off = resolve(voiceEnabled = false)!!
        assertFalse(off.isError)
        assertEquals(AssistantCopy.VOICE_DISABLED, off.message)
        assertNull(resolve(mic = false, keyboard = true)!!.action)
    }

    @Test fun theScreenNoticeOpensAssistantSettingsAndYieldsToFailures() {
        val screen = resolve(screenUnavailable = true)!!
        assertFalse(screen.isError)
        assertEquals("screen notice", screen.message)
        assertEquals(AssistantNoticeAction.ScreenSettings, screen.action)
        // An informational notice (a voice fallback) outranks the screen hint; a failure outranks both.
        assertEquals("fallback", resolve(notice = "fallback", screenUnavailable = true)!!.message)
        assertEquals(AssistantCopy.REQUEST_FAILED, resolve(error = AssistantCopy.REQUEST_FAILED, notice = "fallback", screenUnavailable = true)!!.message)
    }

    @Test fun recognizerCodesMapToTheCopySystemNotTheEngineWording() {
        val engine = "Network error. Please check your internet connection."
        for (code in listOf(
            SpeechRecognitionErrors.ERROR_NETWORK, SpeechRecognitionErrors.ERROR_NETWORK_TIMEOUT,
            SpeechRecognitionErrors.ERROR_NO_MATCH, SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT,
            SpeechRecognitionErrors.ERROR_AUDIO, SpeechRecognitionErrors.ERROR_SERVER,
            SpeechRecognitionErrors.ERROR_RECOGNIZER_BUSY, SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognitionErrors.ERROR_CLIENT
        )) {
            val text = AssistantCopy.recognitionError(code, engine)
            assertFalse("code $code leaked the engine text", text == engine)
            assertFalse("code $code says please", text.contains("please", ignoreCase = true))
            assertTrue("code $code must end a sentence", text.endsWith("."))
        }
        assertEquals(AssistantCopy.RECOGNIZER_UNAVAILABLE, AssistantCopy.recognitionError(SpeechRecognitionErrors.ERROR_CLIENT, "Speech recognition not available"))
        assertEquals(AssistantCopy.RECOGNITION_FAILED, AssistantCopy.recognitionError(SpeechRecognitionErrors.ERROR_CLIENT, "Speech recognizer did not start. Please try again."))
        assertEquals(AssistantCopy.MIC_PERMISSION, AssistantCopy.recognitionError(SpeechRecognitionErrors.ERROR_INSUFFICIENT_PERMISSIONS, "x"))
        // An unknown code falls back to the engine's own text rather than hiding the failure.
        assertEquals("custom", AssistantCopy.recognitionError(99, "custom"))
        assertEquals(AssistantCopy.RECOGNITION_FAILED, AssistantCopy.recognitionError(99, ""))
    }

    @Test fun requestFailuresNameConnectivityAndHideEverythingElse() {
        assertEquals(AssistantCopy.NO_CONNECTION, AssistantCopy.requestFailure(UnknownHostException("api.example.com")))
        assertEquals(AssistantCopy.NO_CONNECTION, AssistantCopy.requestFailure(RuntimeException("wrapped", IOException("timeout"))))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(IllegalStateException("HTTP 401 {\"error\":\"bad key\"}")))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(null))
        // W4: a rejected key is a state only when the stream delivered the status as a typed
        // value. The same words inside a body (above) still never select it, an absent typed
        // status falls back to the text path, and connectivity outranks even a typed status.
        assertEquals(AssistantCopy.PROVIDER_REJECTED, AssistantCopy.requestFailure(IllegalStateException("{\"error\":\"bad key\"}"), httpStatus = 401))
        assertEquals(AssistantCopy.REQUEST_FAILED, AssistantCopy.requestFailure(IllegalStateException("HTTP 401 {\"error\":\"bad key\"}"), httpStatus = null))
        assertEquals(AssistantCopy.NO_CONNECTION, AssistantCopy.requestFailure(IOException("HTTP 401"), httpStatus = 401))
    }

    @Test fun copyIsOneSystem() {
        // Phase lines: present participle + ellipsis. Prompts: imperatives without a period.
        for (phase in listOf(AssistantCopy.LISTENING, AssistantCopy.THINKING, AssistantCopy.ANSWERING, AssistantCopy.SPEAKING)) {
            assertTrue(phase, phase.endsWith("\u2026") && phase.dropLast(1).endsWith("ing"))
        }
        for (prompt in listOf(AssistantCopy.PROMPT, AssistantCopy.AFTER_ERROR, AssistantCopy.THINKING_TITLE, AssistantCopy.THINKING_STATUS, AssistantCopy.USING_SCREEN)) {
            assertFalse(prompt, prompt.endsWith("."))
        }
        // Action labels: sentence case, no period.
        for (action in AssistantNoticeAction.values()) {
            assertTrue(action.label, action.label.first().isUpperCase())
            assertFalse(action.label, action.label.endsWith("."))
            assertTrue(action.label, action.label.drop(1).none { it.isUpperCase() })
        }
        // Notices: full sentences, no "please", no engine names.
        for (notice in listOf(
            AssistantCopy.MIC_PERMISSION, AssistantCopy.VOICE_DISABLED, AssistantCopy.RECOGNIZER_UNAVAILABLE,
            AssistantCopy.NO_PROVIDER, AssistantCopy.NO_CONNECTION, AssistantCopy.REQUEST_FAILED, AssistantCopy.RECOGNITION_FAILED,
            AssistantCopy.RATE_LIMITED, AssistantCopy.PROVIDER_REJECTED
        )) {
            assertTrue(notice, notice.endsWith("."))
            assertFalse(notice, notice.contains("please", ignoreCase = true))
            assertFalse(notice, notice.contains("exception", ignoreCase = true))
        }
    }
}
