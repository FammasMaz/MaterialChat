package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The context-window popup's token label must stay on one line inside a fixed-width
 * menu: on device it wrapped to one glyph per line once the popup was squeezed
 * against the screen edge. These tests pin the label's length ceiling.
 */
class ContextWindowLabelsTest {

    @Test
    fun `token label shows used and max compactly`() {
        assertEquals("~12K / 200K", ContextWindowLabels.tokenLabel(12_345, 200_000))
        assertEquals("~1.2K / 8.0K", ContextWindowLabels.tokenLabel(1_234, 8_000))
        assertEquals("~1.0M / 2.0M", ContextWindowLabels.tokenLabel(1_000_000, 2_000_000))
        assertEquals("~999 used", ContextWindowLabels.tokenLabel(999, null))
    }

    @Test
    fun `token label never exceeds the width ceiling`() {
        val samples = listOf(0, 7, 999, 1_000, 9_999, 10_000, 123_456, 999_999, 1_000_000, 9_999_999, Int.MAX_VALUE)
        for (used in samples) {
            for (max in samples + null) {
                val label = ContextWindowLabels.tokenLabel(used, max)
                assertTrue(label, label.length <= ContextWindowLabels.MaxTokenLabelLength)
                assertFalse(label, label.contains('\n'))
            }
        }
    }

    @Test
    fun `negative counts clamp to zero`() {
        assertEquals("0", ContextWindowLabels.compactTokenCount(-5))
    }

    @Test
    fun `detail label names the model and the estimate kind`() {
        assertEquals(
            "Approximate token estimate for gpt-4o. Model limit unknown.",
            ContextWindowLabels.detailLabel("gpt-4o", null, isApproximate = true)
        )
        assertEquals(
            "Reported chat context used for selected model.",
            ContextWindowLabels.detailLabel("", 8_000, isApproximate = false)
        )
    }
}
