package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bubble-level tap router hands a tap to exactly the control whose bounds contain it,
 * uses the latest bounds a control registered, forgets a control once it unregisters, and
 * never fires a control a second time for a gesture the control already delivered itself.
 */
class BubbleTapRouterTest {
    @Test
    fun routesToTheControlContainingTheTap() {
        val router = BubbleTapRouter()
        var saved = 0
        var used = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { saved++ }
        router.register("used", Rect(110f, 0f, 220f, 40f)) { used++ }

        router.beginGesture()
        assertTrue(router.route(Offset(50f, 20f)))
        router.beginGesture()
        assertTrue(router.route(Offset(150f, 20f)))

        assertEquals(1, saved)
        assertEquals(1, used)
    }

    @Test
    fun aTapOutsideEveryControlIsNotRouted() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { fired++ }

        router.beginGesture()
        assertFalse(router.route(Offset(150f, 20f)))
        router.beginGesture()
        assertFalse(router.route(Offset(50f, 80f)))
        assertEquals(0, fired)
    }

    @Test
    fun reRegisteringReplacesTheBoundsAndUnregisteringForgetsTheControl() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { fired++ }
        router.register("saved", Rect(0f, 200f, 100f, 240f)) { fired++ }
        assertEquals(1, router.size)

        router.beginGesture()
        assertFalse("old bounds must be gone", router.route(Offset(50f, 20f)))
        router.beginGesture()
        assertTrue(router.route(Offset(50f, 220f)))
        assertEquals(1, fired)

        router.unregister("saved")
        assertEquals(0, router.size)
        router.beginGesture()
        assertFalse(router.route(Offset(50f, 220f)))
        assertEquals(1, fired)
    }

    @Test
    fun anEmptyRectNeverMatches() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("unplaced", Rect.Zero) { fired++ }

        router.beginGesture()
        assertFalse(router.route(Offset.Zero))
        assertEquals(0, fired)
    }

    /**
     * The router now sits on the bubble's own node and does not care whether the chip consumed
     * the up, so exactly-once delivery is explicit: a control whose own click path fired marks
     * the gesture delivered and the bubble skips it. Without that mark a working chip would
     * toggle twice per tap and the panel would never open.
     */
    @Test
    fun aGestureTheControlDeliveredItselfIsNotRoutedAgain() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { fired++ }

        router.beginGesture()
        router.delivering { fired++ }()
        assertFalse("the bubble must not deliver the same tap a second time", router.route(Offset(50f, 20f)))
        assertEquals(1, fired)

        // The next gesture is routed normally when the control's own path did not fire.
        router.beginGesture()
        assertTrue(router.route(Offset(50f, 20f)))
        assertEquals(2, fired)
    }

    @Test
    fun aNewGestureClearsAStaleDeliveryMark() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { fired++ }

        // A control fired, then the gesture left slop and was never routed: the mark must not
        // leak into the next tap.
        router.beginGesture()
        router.markDelivered()
        router.beginGesture()
        assertTrue(router.route(Offset(50f, 20f)))
        assertEquals(1, fired)
    }
}
