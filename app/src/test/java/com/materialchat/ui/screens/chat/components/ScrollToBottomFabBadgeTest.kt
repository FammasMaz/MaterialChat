package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unread badge rule for the scroll-to-bottom FAB. */
class ScrollToBottomFabBadgeTest {

    @Test
    fun noBadgeWhenNothingIsUnread() {
        assertNull(scrollFabBadgeLabel(0))
        assertNull(scrollFabBadgeLabel(-3))
    }

    @Test
    fun exactCountUpToNinetyNine() {
        assertEquals("1", scrollFabBadgeLabel(1))
        assertEquals("42", scrollFabBadgeLabel(42))
        assertEquals("99", scrollFabBadgeLabel(99))
    }

    @Test
    fun capsAtNinetyNinePlus() {
        assertEquals("99+", scrollFabBadgeLabel(100))
        assertEquals("99+", scrollFabBadgeLabel(10_000))
    }

    @Test
    fun cornerTrackIsMonotonic() {
        // Arrival (circle) > resting (small FAB) > pressed, so the morph only ever tightens.
        assertTrue(ScrollFabArrivalCorner > ScrollFabRestingCorner)
        assertTrue(ScrollFabRestingCorner > ScrollFabPressedCorner)
    }
}
