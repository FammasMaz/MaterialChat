package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bubble-level tap router hands a tap to exactly the control whose bounds contain it,
 * uses the latest bounds a control registered, and forgets a control once it unregisters.
 */
class BubbleTapRouterTest {
    @Test
    fun routesToTheControlContainingTheTap() {
        val router = BubbleTapRouter()
        var saved = 0
        var used = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { saved++ }
        router.register("used", Rect(110f, 0f, 220f, 40f)) { used++ }

        assertTrue(router.route(Offset(50f, 20f)))
        assertTrue(router.route(Offset(150f, 20f)))

        assertEquals(1, saved)
        assertEquals(1, used)
    }

    @Test
    fun aTapOutsideEveryControlIsNotRouted() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("saved", Rect(0f, 0f, 100f, 40f)) { fired++ }

        assertFalse(router.route(Offset(150f, 20f)))
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

        assertFalse("old bounds must be gone", router.route(Offset(50f, 20f)))
        assertTrue(router.route(Offset(50f, 220f)))
        assertEquals(1, fired)

        router.unregister("saved")
        assertEquals(0, router.size)
        assertFalse(router.route(Offset(50f, 220f)))
        assertEquals(1, fired)
    }

    @Test
    fun anEmptyRectNeverMatches() {
        val router = BubbleTapRouter()
        var fired = 0
        router.register("unplaced", Rect.Zero) { fired++ }

        assertFalse(router.route(Offset.Zero))
        assertEquals(0, fired)
    }
}
