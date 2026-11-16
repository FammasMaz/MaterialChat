package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatEmptyStateTest {

    @Test
    fun `plain chat names the model`() {
        val copy = chatEmptyStateCopy(modelName = "gpt-5", personaName = null, hasStarters = false)
        assertEquals("Ask anything", copy.title)
        assertEquals("gpt-5 is ready. Replies stream in word by word.", copy.supporting)
    }

    @Test
    fun `blank model falls back to a generic line`() {
        val copy = chatEmptyStateCopy(modelName = "  ", personaName = null, hasStarters = false)
        assertEquals("Ask anything", copy.title)
        assertEquals("Replies stream in word by word.", copy.supporting)
    }

    @Test
    fun `persona chat leads with the persona`() {
        val copy = chatEmptyStateCopy(modelName = "gpt-5", personaName = "Tutor", hasStarters = false)
        assertEquals("Chat with Tutor", copy.title)
        assertEquals("gpt-5 is ready. Replies stream in word by word.", copy.supporting)
    }

    @Test
    fun `starters are pointed at when present`() {
        val copy = chatEmptyStateCopy(modelName = "gpt-5", personaName = "Tutor", hasStarters = true)
        assertEquals("Chat with Tutor", copy.title)
        assertEquals("Pick a starter below, or type your own first message.", copy.supporting)
    }

    @Test
    fun `empty persona name is treated as no persona`() {
        val copy = chatEmptyStateCopy(modelName = "gpt-5", personaName = "", hasStarters = false)
        assertEquals("Ask anything", copy.title)
    }
}
