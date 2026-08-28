package com.materialchat.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelNameParserTest {

    @Test
    fun `parses provider and model`() {
        val p = ModelNameParser.parse("antigravity/claude-opus-4.5")
        assertEquals("Antigravity", p.provider)
        assertEquals("Claude Opus 4.5", p.model)
    }

    @Test
    fun `parses multi segment identifier without leaking slashes`() {
        val p = ModelNameParser.parse("proxy/openai/gpt-5.5")
        assertEquals("Proxy", p.provider)
        assertEquals("GPT 5.5", p.model)
        assertTrue(!p.model.contains("/"))
        assertTrue(!p.provider.contains("/"))
    }

    @Test
    fun `parses model without provider`() {
        val p = ModelNameParser.parse("gpt-5.6-sol")
        assertEquals("OpenAI", p.provider)
        assertEquals("GPT 5.6 Sol", p.model)
    }

    @Test
    fun `handles trailing slashes and whitespace`() {
        val p = ModelNameParser.parse("openai/ gpt-4.1 /")
        assertEquals("OpenAI", p.provider)
        assertEquals("GPT 4.1", p.model)
    }

    private fun assertTrue(b: Boolean) { assert(b) }
}
