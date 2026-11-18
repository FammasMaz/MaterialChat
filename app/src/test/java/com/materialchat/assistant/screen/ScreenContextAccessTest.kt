package com.materialchat.assistant.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The API 37 request-state mapping and the wording chosen for each state (pure parts only). */
class ScreenContextAccessTest {
    @Test fun platformConstantsMapToStates() {
        // VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED/REQUESTABLE/UNREQUESTABLE
        assertEquals(ScreenContextAccess.RequestState.GRANTED, ScreenContextAccess.fromPlatform(0))
        assertEquals(ScreenContextAccess.RequestState.REQUESTABLE, ScreenContextAccess.fromPlatform(1))
        assertEquals(ScreenContextAccess.RequestState.UNREQUESTABLE, ScreenContextAccess.fromPlatform(2))
        assertNull(ScreenContextAccess.fromPlatform(3))
        assertNull(ScreenContextAccess.fromPlatform(-1))
    }

    @Test fun apiGuardIsAndroid16() {
        assertEquals(37, ScreenContextAccess.API_READ_SCREEN_CONTEXT)
    }

    @Test fun noticeNamesTheSystemSwitchesWhenTheStateIsUnknownOrRequestable() {
        for (state in listOf(null, ScreenContextAccess.RequestState.REQUESTABLE)) {
            val notice = ScreenContextAccess.notice(state)
            assertEquals(ScreenContextPrompt.UNAVAILABLE_NOTICE, notice)
            assertTrue(notice.contains("Use screenshot"))
            assertTrue(notice.contains("Use text from screen"))
            assertTrue(notice.contains("Digital assistant app"))
        }
    }

    @Test fun grantedButEmptyMeansThisScreenWasWithheld() {
        val notice = ScreenContextAccess.notice(ScreenContextAccess.RequestState.GRANTED)
        assertTrue(notice.contains("private"))
        assertTrue(notice.contains("lock screen"))
    }

    @Test fun unrequestableSaysTheSystemBlocksIt() {
        val notice = ScreenContextAccess.notice(ScreenContextAccess.RequestState.UNREQUESTABLE)
        assertTrue(notice.contains("blocked"))
        assertTrue(notice.contains("Digital assistant app"))
    }
}
