package com.materialchat.ui.theme

import com.materialchat.assistant.ui.AssistantCopy
import com.materialchat.ui.components.ErrorContentCopy
import com.materialchat.ui.screens.chat.components.ChatErrorCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy rulings live in one place (W7 part 6). The cross-owner equality pins that used to sit
 * in `ErrorContentCopyTest` (chat and assistant retry labels) are held here, by the owner of the
 * ruling, until every surface aliases [CopyRules] directly.
 */
class CopyRulesTest {

    @Test fun tryAgainIsTheOneRetryVerbEverywhere() {
        assertEquals("Try again", CopyRules.TRY_AGAIN)
        assertEquals(CopyRules.TRY_AGAIN, ErrorContentCopy.TRY_AGAIN)
        assertEquals(CopyRules.TRY_AGAIN, ChatErrorCopy.TRY_AGAIN)
        assertEquals(CopyRules.TRY_AGAIN, AssistantCopy.AFTER_ERROR)
        assertNotEquals("Retry", CopyRules.TRY_AGAIN)
    }

    @Test fun theGenericLinesAreShared() {
        assertEquals(CopyRules.GENERIC_HEADLINE, ErrorContentCopy.GENERIC_HEADLINE)
        assertEquals(CopyRules.REQUEST_FAILED, ChatErrorCopy.REQUEST_FAILED)
        assertEquals(CopyRules.REQUEST_FAILED, AssistantCopy.REQUEST_FAILED)
        assertTrue(CopyRules.isPhrase(CopyRules.GENERIC_HEADLINE))
        assertTrue(CopyRules.isNotice(CopyRules.REQUEST_FAILED))
    }

    @Test fun failureLinesOpenWithCouldNot() {
        assertEquals("Could not", CopyRules.FAILURE_VERB)
        assertEquals("Could not save backup", CopyRules.failureLine("save backup"))
        assertEquals("Could not save backup", CopyRules.failureLine("  save backup "))
        assertTrue(CopyRules.isPhrase(CopyRules.failureLine("share image")))
        for (verb in listOf("Failed to", "Unable to", "Couldn't")) {
            assertTrue(verb, verb in CopyRules.BANNED_FAILURE_VERBS)
            assertFalse(verb, CopyRules.isPhrase("$verb save backup"))
            assertFalse(verb, CopyRules.isNotice("$verb save backup."))
        }
    }

    @Test fun aPhraseHasNoTrailingPeriodAndANoticeEndsEverySentenceWithOne() {
        assertTrue(CopyRules.isPhrase("Could not record the vote"))
        assertTrue(CopyRules.isPhrase("Listening" + CopyRules.ELLIPSIS))
        assertFalse(CopyRules.isPhrase("Could not record the vote."))
        assertFalse(CopyRules.isPhrase("could not record the vote"))
        assertFalse(CopyRules.isPhrase(""))
        assertFalse(CopyRules.isPhrase("Vote recorded!"))
        assertFalse(CopyRules.isPhrase("Thinking..."))

        assertTrue(CopyRules.isNotice("No connection. Check your network and try again."))
        assertTrue(CopyRules.isNotice("Something went wrong."))
        assertFalse(CopyRules.isNotice("Something went wrong"))
        assertFalse(CopyRules.isNotice("Something went wrong. try again."))
        assertFalse(CopyRules.isNotice("Voice preview timed out. Please try again."))
        assertFalse(CopyRules.isNotice("Something went wrong!"))
    }

    @Test fun theSharedNoticesFollowTheNoticeRule() {
        for (line in listOf(ChatErrorCopy.UNAUTHORIZED, ChatErrorCopy.FORBIDDEN, ChatErrorCopy.RATE_LIMITED,
            ChatErrorCopy.SERVER_ERROR, ChatErrorCopy.REJECTED, ChatErrorCopy.NO_CONNECTION, ChatErrorCopy.REQUEST_FAILED)) {
            assertTrue(line, CopyRules.isNotice(line))
        }
        for (line in AssistantCopy.NOTICES) assertTrue(line, CopyRules.isNotice(line))
        for (line in AssistantCopy.PHRASES) assertTrue(line, CopyRules.isPhrase(line))
    }
}
