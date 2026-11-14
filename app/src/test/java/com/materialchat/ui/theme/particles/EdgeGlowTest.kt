package com.materialchat.ui.theme.particles

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure math of the assistant's screen-edge light: levels, breathing, flow and the frame gate. */
class EdgeGlowTest {
    @Test fun phasesOrderListeningAboveBusyAboveIdleAboveHidden() {
        assertEquals(0f, EdgeGlowPhase.Hidden.level, 0f)
        assertTrue(EdgeGlowPhase.Listening.level > EdgeGlowPhase.Busy.level)
        assertTrue(EdgeGlowPhase.Busy.level > EdgeGlowPhase.Idle.level)
        assertTrue(EdgeGlowPhase.Idle.level > 0f)
        assertEquals(1f, EdgeGlowPhase.Listening.level, 0f)
    }

    @Test fun breathStartsFullDipsByDepthAndNeverLeavesRange() {
        assertEquals(1f, edgeGlowBreath(0f, 2600, 0.22f), 1e-4f)
        assertEquals(0.78f, edgeGlowBreath(1.3f, 2600, 0.22f), 1e-3f)
        assertEquals(1f, edgeGlowBreath(2.6f, 2600, 0.22f), 1e-3f)
        for (t in 0..100) {
            val v = edgeGlowBreath(t * 0.07f, 2600, 0.22f)
            assertTrue(v in 0.78f - 1e-3f..1f + 1e-6f)
        }
        assertEquals(1f, edgeGlowBreath(1.3f, 2600, 0f), 0f)
    }

    @Test fun levelApproachesTargetAndSettles() {
        var level = 1f
        var seconds = 0f
        while (level != 0.65f) {
            level = edgeGlowLevel(level, 0.65f, 1f / 60f, 0.28f)
            seconds += 1f / 60f
            assertTrue("level must stay between the endpoints", level in 0.65f..1f)
            assertTrue("must settle within two seconds: $seconds", seconds < 2f)
        }
        assertEquals(1f, edgeGlowLevel(1f, 0.65f, 0f, 0.28f), 0f)
    }

    @Test fun framesOnlyWhileVisiblePresentAndAnimating() {
        assertTrue(shouldRequestEdgeGlowFrames(true, 0.5f, true, 1f))
        assertFalse(shouldRequestEdgeGlowFrames(false, 0.5f, true, 1f))
        assertFalse(shouldRequestEdgeGlowFrames(true, 0f, true, 1f))
        assertFalse(shouldRequestEdgeGlowFrames(true, 0.5f, false, 1f))
        assertFalse(shouldRequestEdgeGlowFrames(true, 0.5f, true, 0f))
    }

    @Test fun sweepStopsAreSortedClosedAndShiftWithFlow() {
        val palette = listOf(Color.Red, Color.Blue)
        val still = edgeGlowStops(palette, 0f, 8)
        assertEquals(9, still.size)
        assertEquals(0f, still.first().first, 0f)
        assertEquals(1f, still.last().first, 0f)
        for (i in 1 until still.size) assertTrue(still[i].first > still[i - 1].first)
        assertEquals(still.first().second, still.last().second)
        assertEquals(Color.Red, still.first().second)
        val shifted = edgeGlowStops(palette, 0.5f, 8)
        assertEquals("half a trip later the first stop shows the second colour", Color.Blue, shifted.first().second)
        val empty = edgeGlowStops(emptyList(), 0.3f, 4)
        assertTrue(empty.all { it.second == Color.White })
    }

    @Test fun styleRejectsInvalidValues() {
        listOf(
            { EdgeGlowStyle(opacity = 1.5f) },
            { EdgeGlowStyle(breathPeriodMillis = 0) },
            { EdgeGlowStyle(flowPeriodMillis = 0) },
            { EdgeGlowStyle(levelResponseSeconds = 0f) }
        ).forEach { build ->
            try {
                build()
                throw AssertionError("expected rejection")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}
