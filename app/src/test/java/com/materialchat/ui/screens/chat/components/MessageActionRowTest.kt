package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageActionRowTest {

    @Test
    fun `no flags yields an empty row`() {
        assertTrue(messageActionSpecs(false, false, false, false).isEmpty())
    }

    @Test
    fun `actions keep a fixed order with regenerate last`() {
        val specs = messageActionSpecs(
            showCopy = true,
            showBranch = true,
            showRedoWithModel = true,
            showRegenerate = true
        )
        assertEquals(
            listOf(
                MessageActionKind.Copy,
                MessageActionKind.Branch,
                MessageActionKind.RedoWithModel,
                MessageActionKind.Regenerate
            ),
            specs.map { it.kind }
        )
    }

    @Test
    fun `only regenerate carries primary emphasis`() {
        val specs = messageActionSpecs(true, true, true, true)
        assertEquals(
            listOf(MessageActionKind.Regenerate),
            specs.filter { it.emphasis == MessageActionEmphasis.Primary }.map { it.kind }
        )
        assertTrue(
            specs.filter { it.kind != MessageActionKind.Regenerate }
                .all { it.emphasis == MessageActionEmphasis.Standard }
        )
    }

    @Test
    fun `hidden actions are skipped without disturbing order`() {
        val specs = messageActionSpecs(
            showCopy = true,
            showBranch = false,
            showRedoWithModel = true,
            showRegenerate = false
        )
        assertEquals(
            listOf(MessageActionKind.Copy, MessageActionKind.RedoWithModel),
            specs.map { it.kind }
        )
    }

    @Test
    fun `speed stats list only the present figures in display order`() {
        assertEquals(
            listOf("42 tok/s", "1.2s", "avg 40"),
            MessageSpeedStats(tps = "42 tok/s", ttft = "1.2s", total = null, avg = "avg 40").segments()
        )
        assertEquals(emptyList<String>(), MessageSpeedStats().segments())
    }
}
