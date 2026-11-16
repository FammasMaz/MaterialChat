package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The message-list fling must close its scroll session once the list is visibly still, because
 * an open session makes the list consume every pointer-down inside the bubbles (memory chips,
 * tables, thinking header, action row go dead). These pin the settle rule.
 */
class ChatListFlingTest {
    private val settle = 24f * 3f // 24dp/s at xhdpi

    @Test
    fun fastFlingIsNotSettled() {
        assertFalse(flingHasSettled(velocityPxPerSec = 1_200f, settleVelocityPxPerSec = settle))
        assertFalse(flingHasSettled(velocityPxPerSec = -1_200f, settleVelocityPxPerSec = settle))
    }

    @Test
    fun subPixelTailIsSettledInBothDirections() {
        assertTrue(flingHasSettled(velocityPxPerSec = 10f, settleVelocityPxPerSec = settle))
        assertTrue(flingHasSettled(velocityPxPerSec = -10f, settleVelocityPxPerSec = settle))
        assertTrue(flingHasSettled(velocityPxPerSec = 0f, settleVelocityPxPerSec = settle))
    }

    @Test
    fun exactlyAtThresholdStillRuns() {
        // Strictly below: a fling at the threshold is still moving a visible amount per second.
        assertFalse(flingHasSettled(velocityPxPerSec = settle, settleVelocityPxPerSec = settle))
    }
}
