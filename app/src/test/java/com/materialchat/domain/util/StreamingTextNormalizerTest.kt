package com.materialchat.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingTextNormalizerTest {

    @Test
    fun `normalizeStreamingTextBoundary removes thought trailing whitespace and answer leading blank lines`() {
        val result = normalizeStreamingTextBoundary(
            content = "\n\n\t  \nYo! Not much.",
            thinkingContent = "\n\nThe user is greeting casually.\n\n\n"
        )

        assertEquals("The user is greeting casually.", result.thinkingContent)
        assertEquals("Yo! Not much.", result.content)
    }

    @Test
    fun `normalizeStreamingTextBoundary preserves answer indentation after blank separators`() {
        val result = normalizeStreamingTextBoundary(
            content = "\n\n    indented answer",
            thinkingContent = "Done thinking.\n"
        )

        assertEquals("Done thinking.", result.thinkingContent)
        assertEquals("    indented answer", result.content)
    }

    @Test
    fun `normalizeStreamingTextBoundary keeps content unchanged without thinking`() {
        val result = normalizeStreamingTextBoundary(
            content = "\n\nStandalone content",
            thinkingContent = null
        )

        assertNull(result.thinkingContent)
        assertEquals("\n\nStandalone content", result.content)
    }
}
