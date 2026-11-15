package com.materialchat.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the drag policy of embedded math WebViews. The old listener asked the parent to stop
 * intercepting on the first move with dx > dy, with no slop and without checking that the
 * WebView could scroll at all; through Compose view interop that swallowed the whole gesture,
 * so a table whose cell held an inline equation could not be scrolled from that cell.
 */
class MathWebViewTouchTest {

    private val slop = 16f

    @Test
    fun `a horizontal jitter on a fitting equation never claims the gesture`() {
        assertFalse(
            shouldMathWebViewClaimHorizontalDrag(
                dx = 3f, dy = 1f, touchSlop = slop, canScrollLeft = false, canScrollRight = false
            )
        )
    }

    @Test
    fun `a long horizontal drag on a fitting equation still does not claim`() {
        assertFalse(
            shouldMathWebViewClaimHorizontalDrag(
                dx = -120f, dy = 4f, touchSlop = slop, canScrollLeft = false, canScrollRight = false
            )
        )
    }

    @Test
    fun `a drag below touch slop does not claim even on wide content`() {
        assertFalse(
            shouldMathWebViewClaimHorizontalDrag(
                dx = -10f, dy = 0f, touchSlop = slop, canScrollLeft = true, canScrollRight = true
            )
        )
    }

    @Test
    fun `a horizontal drag past slop claims only in a direction the content can scroll`() {
        // Finger left reveals content on the right.
        assertTrue(
            shouldMathWebViewClaimHorizontalDrag(
                dx = -40f, dy = 2f, touchSlop = slop, canScrollLeft = false, canScrollRight = true
            )
        )
        assertFalse(
            shouldMathWebViewClaimHorizontalDrag(
                dx = 40f, dy = 2f, touchSlop = slop, canScrollLeft = false, canScrollRight = true
            )
        )
        assertTrue(
            shouldMathWebViewClaimHorizontalDrag(
                dx = 40f, dy = 2f, touchSlop = slop, canScrollLeft = true, canScrollRight = false
            )
        )
    }

    @Test
    fun `a vertical drag is never claimed`() {
        assertFalse(
            shouldMathWebViewClaimHorizontalDrag(
                dx = 30f, dy = 45f, touchSlop = slop, canScrollLeft = true, canScrollRight = true
            )
        )
    }
}
