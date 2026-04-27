package com.materialchat.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPromptBuilderTest {

    @Test
    fun `shouldUseWebSearchForQuery skips acknowledgements`() {
        assertFalse(shouldUseWebSearchForQuery("thanks"))
        assertFalse(shouldUseWebSearchForQuery("ok cool"))
        assertFalse(shouldUseWebSearchForQuery("got it!"))
    }

    @Test
    fun `shouldUseWebSearchForQuery detects current information requests`() {
        assertTrue(shouldUseWebSearchForQuery("PSL 2026 playoff qualification scenarios after today's match"))
        assertTrue(shouldUseWebSearchForQuery("latest Kotlin release notes"))
        assertTrue(shouldUseWebSearchForQuery("what is the weather right now"))
    }

    @Test
    fun `shouldUseWebSearchForQuery skips static questions`() {
        assertFalse(shouldUseWebSearchForQuery("what is the Pythagorean theorem?"))
        assertFalse(shouldUseWebSearchForQuery("write a friendly email draft"))
    }
}
