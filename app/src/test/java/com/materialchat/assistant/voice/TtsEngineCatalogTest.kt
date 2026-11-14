package com.materialchat.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineCatalogTest {

    private val stock = TtsEngineOption("com.google.android.tts", "Speech Services by Google", isSystemDefault = true)
    private val neural = TtsEngineOption("app.hayai.tts", "HayaiTTS", isSystemDefault = false)
    private val other = TtsEngineOption("com.example.tts", "Example TTS", isSystemDefault = false)

    @Test
    fun `system default engine ranks first`() {
        assertEquals(stock, rankTtsEngines(listOf(neural, other, stock)).first())
    }

    @Test
    fun `without a default, an installed neural engine outranks the stock engine`() {
        val ranked = rankTtsEngines(listOf(stock.copy(isSystemDefault = false), neural))
        assertEquals(neural, ranked.first())
    }

    @Test
    fun `preferred package wins over the default`() {
        assertEquals(neural, selectTtsEngine(listOf(stock, neural), "app.hayai.tts"))
    }

    @Test
    fun `unknown preferred package falls back to the default engine`() {
        assertEquals(stock, selectTtsEngine(listOf(stock, neural), "not.installed"))
    }

    @Test
    fun `null preference selects the default engine`() {
        assertEquals(stock, selectTtsEngine(listOf(neural, stock), null))
    }

    @Test
    fun `no installed engines selects nothing`() {
        assertNull(selectTtsEngine(emptyList(), "app.hayai.tts"))
    }

    @Test
    fun `duplicate packages are collapsed`() {
        val ranked = rankTtsEngines(listOf(neural, neural.copy(label = "duplicate"), other))
        assertEquals(2, ranked.size)
    }

    @Test
    fun `ranking is stable for engines of the same class`() {
        val a = TtsEngineOption("b.pkg", "Beta", false)
        val b = TtsEngineOption("a.pkg", "Alpha", false)
        assertEquals(listOf(b, a), rankTtsEngines(listOf(a, b)))
        assertTrue(rankTtsEngines(listOf(a, b)).map { it.label } == listOf("Alpha", "Beta"))
    }
}
