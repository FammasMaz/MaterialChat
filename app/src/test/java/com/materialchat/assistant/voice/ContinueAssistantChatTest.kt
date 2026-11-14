package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.shouldContinueAssistantChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueAssistantChatTest {
    private val now = 10_000_000L

    @Test fun neverAlwaysStartsFresh() {
        assertFalse(shouldContinueAssistantChat(0, lastActivityMillis = now - 1_000, nowMillis = now))
    }
    @Test fun withinWindowContinues() {
        assertTrue(shouldContinueAssistantChat(5, lastActivityMillis = now - 4 * 60_000, nowMillis = now))
    }
    @Test fun pastWindowStartsFresh() {
        assertFalse(shouldContinueAssistantChat(5, lastActivityMillis = now - 6 * 60_000, nowMillis = now))
    }
    @Test fun clockMovingBackwardsStartsFresh() {
        assertFalse(shouldContinueAssistantChat(60, lastActivityMillis = now + 5_000, nowMillis = now))
    }
    @Test fun defaultIsNeverAndUnknownValuesNormalizeToNever() {
        assertEquals(0, AssistantVoiceSettings().continueChatMinutes)
        assertEquals(0, AssistantVoiceSettings(continueChatMinutes = 7).normalized().continueChatMinutes)
        assertEquals(15, AssistantVoiceSettings(continueChatMinutes = 15).normalized().continueChatMinutes)
    }
}
