package com.materialchat.ui.screens.chat.components

import com.materialchat.ui.screens.chat.ContextWindowLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHeaderStatusTest {

    @Test
    fun `a plain conversation shows no status chips`() {
        assertEquals(
            emptyList<ChatHeaderBadge>(),
            resolveChatHeaderBadges(false, false, false, ContextWindowLevel.Calm)
        )
    }

    @Test
    fun `unknown and filling context levels show no context chip`() {
        assertEquals(emptyList<ChatHeaderBadge>(), resolveChatHeaderBadges(false, false, false, ContextWindowLevel.Unknown))
        assertEquals(emptyList<ChatHeaderBadge>(), resolveChatHeaderBadges(false, false, false, ContextWindowLevel.Filling))
    }

    @Test
    fun `chips come in a fixed order`() {
        assertEquals(
            listOf(
                ChatHeaderBadge.Temporary,
                ChatHeaderBadge.Archived,
                ChatHeaderBadge.ContextHigh,
                ChatHeaderBadge.Branched
            ),
            resolveChatHeaderBadges(true, true, true, ContextWindowLevel.High)
        )
    }

    @Test
    fun `critical context replaces the high chip`() {
        val badges = resolveChatHeaderBadges(false, false, false, ContextWindowLevel.Critical)
        assertEquals(listOf(ChatHeaderBadge.ContextCritical), badges)
    }

    @Test
    fun `streaming label matches the working indicator wording`() {
        assertEquals(CHAT_WORKING_STATUS, chatHeaderStreamingLabel(true))
        assertNull(chatHeaderStreamingLabel(false))
    }

    @Test
    fun `only the first naming of a new chat is revealed`() {
        assertTrue(shouldRevealTitle("New Chat", "Trip to Lisbon"))
        assertFalse(shouldRevealTitle("Trip to Lisbon", "Lisbon itinerary"))
        assertFalse(shouldRevealTitle("New Chat", "New Chat"))
        assertFalse(shouldRevealTitle("Chat", "New Chat"))
    }

    @Test
    fun `revealed title grows with progress and is whole at one`() {
        assertEquals("", revealedTitle("Lisbon", 0f))
        assertEquals("Lis", revealedTitle("Lisbon", 0.5f))
        assertEquals("Lisbon", revealedTitle("Lisbon", 1f))
        assertEquals("Lisbon", revealedTitle("Lisbon", 3f))
        assertEquals("", revealedTitle("Lisbon", -1f))
    }
}
