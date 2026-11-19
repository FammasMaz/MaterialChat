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

    @Test fun theClassLinesChatAndTheAssistantShareAreTheThemeConstants() {
        // W8 part 1: the last two chat <-> assistant cross-owner pins (ChatErrorCopyTest) can
        // move here; both surfaces alias these constants.
        assertEquals("The model is busy right now. Wait a moment and try again.", CopyRules.RATE_LIMITED)
        assertEquals("No connection. Check your network and try again.", CopyRules.NO_CONNECTION)
        assertEquals(CopyRules.RATE_LIMITED, ChatErrorCopy.RATE_LIMITED)
        assertEquals(CopyRules.RATE_LIMITED, AssistantCopy.RATE_LIMITED)
        assertEquals(CopyRules.NO_CONNECTION, ChatErrorCopy.NO_CONNECTION)
        assertEquals(CopyRules.NO_CONNECTION, AssistantCopy.NO_CONNECTION)
        assertTrue(CopyRules.isNotice(CopyRules.RATE_LIMITED))
        assertTrue(CopyRules.isNotice(CopyRules.NO_CONNECTION))
    }

    @Test fun failureLinesOpenWithCouldNot() {
        assertEquals("Could not", CopyRules.FAILURE_VERB)
        assertEquals("Could not save backup", CopyRules.failureLine("save backup"))
        assertEquals("Could not save backup", CopyRules.failureLine("  save backup "))
        assertEquals(CopyRules.failureLine("share"), CopyRules.COULD_NOT_SHARE)
        assertTrue(CopyRules.isPhrase(CopyRules.COULD_NOT_SHARE))
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

    @Test fun aDashJoinsNoClausesACommaOrAFullStopDoes() {
        // W9 part 3 ruling (audit W8 #7): a phrase joins its clauses with a comma, a notice
        // splits them into sentences; the em dash and the spaced en dash are not copy punctuation.
        assertEquals("\u2014", CopyRules.EM_DASH)
        assertEquals(" \u2013 ", CopyRules.SPACED_EN_DASH)
        assertTrue(CopyRules.isPhrase("No providers configured, add one in Settings"))
        assertFalse(CopyRules.isPhrase("No providers configured \u2014 add one in Settings"))
        assertFalse(CopyRules.isPhrase("No providers configured \u2013 add one in Settings"))
        // An unspaced en dash inside a range is a number, not a joiner.
        assertTrue(CopyRules.isPhrase("About 2\u20133 MB"))
        assertTrue(CopyRules.isNotice("Screen context is off. Enable it in Settings."))
        assertFalse(CopyRules.isNotice("Screen context is off \u2014 enable it in Settings."))
        assertFalse(CopyRules.isNotice("Screen context is off \u2013 enable it in Settings."))
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
