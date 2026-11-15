package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The header folds after real travel toward newer messages and unfolds after real travel back.
 * Deltas follow nested-scroll coordinates: negative when content moves up.
 */
class ChatHeaderCollapseMachineTest {

    private fun machine() = ChatHeaderCollapseMachine(collapseThresholdPx = 100f, expandThresholdPx = 50f)

    @Test
    fun `starts expanded`() {
        assertFalse(machine().isCollapsed)
    }

    @Test
    fun `collapses once travel toward newer messages reaches the threshold`() {
        val m = machine()
        assertFalse(m.onScroll(-60f))
        assertFalse(m.isCollapsed)
        assertTrue(m.onScroll(-40f))
        assertTrue(m.isCollapsed)
    }

    @Test
    fun `does not collapse below the threshold`() {
        val m = machine()
        repeat(9) { m.onScroll(-11f) } // 99px
        assertFalse(m.isCollapsed)
    }

    @Test
    fun `a direction reversal resets accumulated travel`() {
        val m = machine()
        m.onScroll(-90f)
        m.onScroll(5f) // flip: travel restarts at +5
        m.onScroll(-90f) // only 90 since the flip
        assertFalse(m.isCollapsed)
        m.onScroll(-10f)
        assertTrue(m.isCollapsed)
    }

    @Test
    fun `expands after travelling back the expand threshold`() {
        val m = machine()
        m.onScroll(-100f)
        assertTrue(m.isCollapsed)
        assertFalse(m.onScroll(30f))
        assertTrue(m.isCollapsed)
        assertTrue(m.onScroll(20f))
        assertFalse(m.isCollapsed)
    }

    @Test
    fun `expanding is easier than collapsing`() {
        val m = machine()
        m.onScroll(-100f)
        m.onScroll(50f)
        assertFalse(m.isCollapsed)
        m.onScroll(-50f)
        assertTrue("50px must not be enough to collapse again", !m.isCollapsed)
    }

    @Test
    fun `zero and NaN deltas are ignored`() {
        val m = machine()
        assertFalse(m.onScroll(0f))
        assertFalse(m.onScroll(Float.NaN))
        m.onScroll(-99f)
        assertFalse(m.onScroll(0f))
        assertFalse(m.isCollapsed)
    }

    @Test
    fun `staying collapsed while scrolling further reports no change`() {
        val m = machine()
        m.onScroll(-100f)
        assertFalse(m.onScroll(-500f))
        assertTrue(m.isCollapsed)
    }

    @Test
    fun `expand forces the expanded state and clears travel`() {
        val m = machine()
        m.onScroll(-100f)
        assertTrue(m.expand())
        assertFalse(m.isCollapsed)
        assertFalse(m.expand())
        m.onScroll(-99f)
        assertFalse(m.isCollapsed)
    }

    @Test
    fun `collapse forces the collapsed state`() {
        val m = machine()
        assertTrue(m.collapse())
        assertTrue(m.isCollapsed)
        assertFalse(m.collapse())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `thresholds must be positive`() {
        ChatHeaderCollapseMachine(collapseThresholdPx = 0f, expandThresholdPx = 10f)
    }
}
