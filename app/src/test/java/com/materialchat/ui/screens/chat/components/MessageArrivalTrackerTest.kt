package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arrival animation must fire for newly created messages only: never for the initial
 * load of a thread, never for bulk swaps (sibling navigation / branch loads), never twice
 * for the same id, and never for an item that was created off-screen and scrolled into view
 * long after.
 */
class MessageArrivalTrackerTest {

    @Test
    fun initialLoadNeverArrives() {
        val tracker = MessageArrivalTracker { 1_000L }
        tracker.sync(listOf("a", "b", "c", "d"))
        assertFalse(tracker.shouldAnimate("a"))
        assertFalse(tracker.shouldAnimate("d"))
    }

    @Test
    fun freshSendArrivesExactlyOnce() {
        var now = 1_000L
        val tracker = MessageArrivalTracker { now }
        tracker.sync(listOf("a"))
        now = 1_100L
        tracker.sync(listOf("a", "user-2", "assistant-3"))
        assertTrue(tracker.shouldAnimate("user-2"))
        assertTrue(tracker.shouldAnimate("assistant-3"))
        // handed out once: a recomposition / scroll back into view does not re-arrive
        assertFalse(tracker.shouldAnimate("user-2"))
        assertFalse(tracker.shouldAnimate("a"))
    }

    @Test
    fun bulkSwapNeverArrives() {
        val tracker = MessageArrivalTracker { 1_000L }
        tracker.sync(listOf("a"))
        tracker.sync(listOf("x", "y", "z"))
        assertFalse(tracker.shouldAnimate("x"))
        assertFalse(tracker.shouldAnimate("z"))
    }

    @Test
    fun staleArrivalIsDroppedWhenScrolledIntoViewLater() {
        var now = 1_000L
        val tracker = MessageArrivalTracker { now }
        tracker.sync(listOf("a"))
        tracker.sync(listOf("a", "late"))
        now += MessageArrivalTracker.ARRIVAL_WINDOW_MS + 1
        assertFalse(tracker.shouldAnimate("late"))
    }

    @Test
    fun arrivalConstantsAreSubtle() {
        assertTrue(MESSAGE_ARRIVAL_SCALE in 0.9f..0.99f)
        assertTrue(STREAM_END_SETTLE_SCALE in 0.97f..1f)
    }
}
