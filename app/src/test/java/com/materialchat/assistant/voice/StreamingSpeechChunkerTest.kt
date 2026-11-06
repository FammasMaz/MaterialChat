package com.materialchat.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSpeechChunkerTest {
    @Test fun `cumulative snapshots never repeat spoken sentences`() {
        val chunker = StreamingSpeechChunker()
        assertEquals(emptyList<String>(), chunker.update("Hello"))
        assertEquals(listOf("Hello world."), chunker.update("Hello world.\n"))
        assertEquals(emptyList<String>(), chunker.update("Hello world.\n"))
        assertEquals(listOf("Next sentence!"), chunker.update("Hello world.\nNext sentence!\n"))
        assertEquals(emptyList<String>(), chunker.update("Hello world.\nNext sentence!\n", true))
    }

    @Test fun `final flush emits unfinished prose once`() {
        val chunker = StreamingSpeechChunker()
        assertEquals(emptyList<String>(), chunker.update("An unfinished thought"))
        assertEquals(listOf("An unfinished thought"), chunker.update("An unfinished thought", true))
        assertEquals(emptyList<String>(), chunker.update("An unfinished thought", true))
    }

    @Test fun `split fences hide code and preserve surrounding prose`() {
        val chunker = StreamingSpeechChunker()
        assertEquals(listOf("Before."), chunker.update("Before.\n``"))
        assertEquals(emptyList<String>(), chunker.update("Before.\n```kotlin\nsecret()\n``"))
        assertEquals(listOf("After."), chunker.update("Before.\n```kotlin\nsecret()\n```\nAfter.", true))
    }

    @Test fun `filters markdown inline code links tables and indented code`() {
        val chunker = StreamingSpeechChunker()
        assertEquals(listOf("Title Read the docs.", "Done."), chunker.update(
            "# Title\n\n- Read **the** [docs](https://example.com).\n`hidden()`\n" +
                "    hidden too\n| table |\n---\nDone.", true
        ))
    }

    @Test fun `unclosed fence remains silent at final flush`() {
        assertEquals(emptyList<String>(), StreamingSpeechChunker().update("~~~python\nprint('hidden')", true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects rewritten snapshots instead of repeating content`() {
        val chunker = StreamingSpeechChunker()
        chunker.update("Original.\n")
        chunker.update("Replacement.\n")
    }

    @Test fun `reset permits a new response`() {
        val chunker = StreamingSpeechChunker()
        assertEquals(listOf("Hello."), chunker.update("Hello.", true))
        chunker.reset()
        assertEquals(listOf("Hello."), chunker.update("Hello.", true))
    }
}
