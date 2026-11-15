package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingPreviewTest {

    @Test
    fun `first readable line is the preview`() {
        val thinking = "\n\n  The user wants a sorting algorithm.\nLet me consider quicksort."
        assertEquals("The user wants a sorting algorithm.", thinking.thinkingPreviewLine())
    }

    @Test
    fun `markdown furniture is stripped from the preview`() {
        assertEquals("Plan", "## **Plan**".thinkingPreviewLine())
        assertEquals("check the input", "- *check* the input".thinkingPreviewLine())
        assertEquals("first step", "1. first   step".thinkingPreviewLine())
        assertEquals("quoted thought", "> quoted thought".thinkingPreviewLine())
    }

    @Test
    fun `long lines are capped with an ellipsis`() {
        val preview = "x".repeat(THINKING_PREVIEW_MAX_CHARS * 2).thinkingPreviewLine()
        assertEquals(THINKING_PREVIEW_MAX_CHARS + 1, preview.length)
        assertTrue(preview.endsWith("\u2026"))
    }

    @Test
    fun `blank reasoning has no preview`() {
        assertEquals("", "\n   \n".thinkingPreviewLine())
        assertEquals("", "".thinkingPreviewLine())
    }
}
