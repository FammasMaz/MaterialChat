package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Back in the chat must stay native: a normal conversation never intercepts the gesture (so the
 * system predictive-back animation plays), and a temporary one intercepts only to run its
 * cleanup while tracking the gesture.
 */
class ChatBackHandlingTest {
    @Test
    fun normalConversationLeavesBackToTheSystem() {
        assertEquals(ChatBackHandling.System, resolveChatBackHandling(isEphemeral = false))
    }

    @Test
    fun temporaryConversationInterceptsForCleanup() {
        assertEquals(
            ChatBackHandling.InterceptForEphemeralCleanup,
            resolveChatBackHandling(isEphemeral = true)
        )
    }

    @Test
    fun transformIsIdentityAtRest() {
        val t = chatBackTransform(0f)
        assertEquals(1f, t.scale, 0f)
        assertEquals(0f, t.translationXFraction, 0f)
        assertEquals(1f, t.alpha, 0f)
    }

    @Test
    fun transformMatchesPopExitAtCommit() {
        val t = chatBackTransform(1f)
        assertEquals(CHAT_BACK_MIN_SCALE, t.scale, 0f)
        assertEquals(CHAT_BACK_MAX_TRANSLATION_FRACTION, t.translationXFraction, 0f)
        assertEquals(CHAT_BACK_MIN_ALPHA, t.alpha, 0f)
    }

    @Test
    fun transformTracksProgressMonotonically() {
        var previous = chatBackTransform(0f)
        for (step in 1..10) {
            val t = chatBackTransform(step / 10f)
            assertTrue(t.scale <= previous.scale)
            assertTrue(t.translationXFraction >= previous.translationXFraction)
            assertTrue(t.alpha <= previous.alpha)
            previous = t
        }
    }

    @Test
    fun transformClampsOutOfRangeProgress() {
        assertEquals(chatBackTransform(1f), chatBackTransform(3f))
        assertEquals(chatBackTransform(0f), chatBackTransform(-1f))
        assertEquals(chatBackTransform(0f), chatBackTransform(Float.NaN))
    }
}
