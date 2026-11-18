package com.materialchat.assistant.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The local "is this about the screen?" test, on utterances as recognition produces them. */
class ScreenContextTriggerTest {
    private fun hits(vararg utterances: String) = utterances.forEach {
        assertTrue("expected a screen reference in '$it'", ScreenContextTrigger.matches(it))
    }

    private fun misses(vararg utterances: String) = utterances.forEach {
        assertFalse("'$it' is not about the screen", ScreenContextTrigger.matches(it))
    }

    @Test fun theUsersExampleFires() {
        hits("what does this first tweet mean", "What does this first tweet mean?")
    }

    @Test fun deicticWordPlusScreenSubjectFires() {
        hits(
            "explain this tweet", "what is that post about", "translate this message",
            "summarize this page", "reply to this email", "is this product any good",
            "what's in these results", "who is that person"
        )
    }

    @Test fun oneQualifierBetweenPointerAndSubjectIsAllowed() {
        hits("what does this last message say", "read that top comment", "explain this weird error")
        // Two words in between is a different sentence shape; the model can still ask.
        misses("this very long boring tweet is nothing")
    }

    @Test fun fixedScreenPhrasesFire() {
        hits(
            "what am I looking at", "what's on my screen", "translate this", "summarise this",
            "read it to me", "what does it say", "is this a scam", "can you see this"
        )
    }

    @Test fun ordinaryQuestionsDoNotFire() {
        misses(
            "what's the weather tomorrow", "set an alarm for 7 am", "tell me a joke",
            "how tall is the eiffel tower", "remind me to call mom at 5", "", "   ",
            "this is great", "that was fun", "turn on the flashlight", "what is love"
        )
    }

    @Test fun punctuationCaseAndCurlyApostrophesAreIgnored() {
        hits("WHAT\u2019S THIS?!", "what... is this", "Explain this tweet.")
    }
}
