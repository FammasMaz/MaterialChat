package com.materialchat.data.local.preferences

import org.junit.Assert.*
import org.junit.Test

class AssistantVoiceSettingsTest {
    @Test fun defaultsInheritAndUseAndroid() {
        val settings = AssistantVoiceSettings().normalized()
        assertNull(settings.providerId)
        assertNull(settings.model)
        assertNull(settings.reasoningEffort)
        assertEquals(AssistantSpeechBackend.ANDROID, settings.backend)
        assertEquals("", settings.geminiModel)
        assertEquals("", settings.geminiVoice)
    }

    @Test fun trimsIdentifiersAndRemovesBlankOverrides() {
        val settings = AssistantVoiceSettings(providerId = " p ", model = " ",
            androidVoiceId = " voice ", languageTag = " en-US ",
            geminiModel = " model ", geminiVoice = " voice ").normalized()
        assertEquals("p", settings.providerId)
        assertNull(settings.model)
        assertEquals("voice", settings.androidVoiceId)
        assertEquals("en-US", settings.languageTag)
        assertEquals("model", settings.geminiModel)
        assertEquals("voice", settings.geminiVoice)
        assertEquals(settings, settings.normalized())
    }

    @Test fun boundsAndRepairsSpeechRate() {
        for (rate in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, AssistantVoiceSettings(speechRate = rate).normalized().speechRate, 0f)
        }
        assertEquals(0.25f, AssistantVoiceSettings(speechRate = -1f).normalized().speechRate, 0f)
        assertEquals(2f, AssistantVoiceSettings(speechRate = 3f).normalized().speechRate, 0f)
    }

    @Test fun continuationWindowHonoursTheSetting() {
        val now = 10_000_000L
        assertFalse(shouldContinueAssistantChat(0, now - 1_000, now))
        assertTrue(shouldContinueAssistantChat(1, now - 30_000, now))
        assertFalse(shouldContinueAssistantChat(1, now - 61_000, now))
    }

    @Test fun aClockMovedBackwardsDoesNotDropTheChat() {
        val now = 10_000_000L
        // Elapsed time is negative here; that must count as "no time passed", not as expired.
        assertTrue(shouldContinueAssistantChat(5, now + 60_000, now))
    }
}
