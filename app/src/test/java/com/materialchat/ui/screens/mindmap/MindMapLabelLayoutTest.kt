package com.materialchat.ui.screens.mindmap

import com.materialchat.ui.screens.mindmap.components.MindMapLabelLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static audit of the mind-map node labels across font scales. The canvas text is drawn
 * with a native Paint, so the geometry is checked here from the same constants the
 * canvas uses rather than rendered.
 */
class MindMapLabelLayoutTest {

    /** 7 px per code point: wide enough to make the arithmetic obvious. */
    private fun fakeMeasure(text: String): Float = text.codePointCount(0, text.length) * 7f

    @Test
    fun `title and badge keep clear of each other at the common font scales`() {
        listOf(0.85f, 1.0f, 1.15f, 1.3f).forEach { scale ->
            val gap = MindMapLabelLayout.titleBadgeGapDp(scale)
            assertTrue("font scale $scale: gap $gap dp", gap >= 4f)
            assertTrue("font scale $scale: title clips the node top", MindMapLabelLayout.titleTopDp(scale) >= 0f)
        }
    }

    @Test
    fun `at font scale 1_3 the title descent clears the badge ascent by about eight dp`() {
        val gap = MindMapLabelLayout.titleBadgeGapDp(1.3f)
        assertEquals(7.8f, gap, 0.3f)
    }

    @Test
    fun `labels only collide past the largest system font scale`() {
        // Android's largest system font size is 2.0x; the node is tall enough until roughly there.
        assertTrue(MindMapLabelLayout.titleBadgeGapDp(1.9f) > 0f)
        assertTrue(MindMapLabelLayout.titleBadgeGapDp(2.1f) < 0f)
    }

    @Test
    fun `fit keeps a title that already fits`() {
        assertEquals("Short", MindMapLabelLayout.fitToWidth("Short", 100f, ::fakeMeasure))
    }

    @Test
    fun `fit ellipsizes to the measured width not a character count`() {
        // 10 code points fit in 70 px; the ellipsis costs one, so 9 characters survive.
        val fitted = MindMapLabelLayout.fitToWidth("abcdefghijklmnop", 70f, ::fakeMeasure)
        assertEquals("abcdefghi" + MindMapLabelLayout.ELLIPSIS, fitted)
        assertTrue(fakeMeasure(fitted) <= 70f)
    }

    @Test
    fun `fit never splits an emoji prefix`() {
        val title = "\uD83D\uDD0D Research the thing"
        val fitted = MindMapLabelLayout.fitToWidth(title, 35f, ::fakeMeasure)
        assertTrue(fitted.startsWith("\uD83D\uDD0D"))
        assertTrue(fitted.endsWith(MindMapLabelLayout.ELLIPSIS))
        assertTrue(fakeMeasure(fitted) <= 35f)
        fitted.forEachIndexed { index, c ->
            if (Character.isHighSurrogate(c)) assertTrue(index + 1 < fitted.length && Character.isLowSurrogate(fitted[index + 1]))
        }
    }

    @Test
    fun `fit collapses to the ellipsis when nothing fits`() {
        assertEquals(MindMapLabelLayout.ELLIPSIS, MindMapLabelLayout.fitToWidth("anything", 7f, ::fakeMeasure))
    }
}
