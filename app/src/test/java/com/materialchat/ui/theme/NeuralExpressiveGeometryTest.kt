package com.materialchat.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class NeuralExpressiveGeometryTest {
    @Test fun seedsAreDeterministicAndSpanMultipleDepths() {
        val seeds = neuralExpressiveSeeds(96)
        assertEquals(seeds, neuralExpressiveSeeds(96))
        assertEquals(96, seeds.size)
        assertTrue(seeds.any { it.distance < 8f })
        assertTrue(seeds.any { it.distance > 20f })
        assertTrue(seeds.all { it.phase >= 0f && it.phase < 1f })
        assertTrue(neuralExpressiveSeeds(0).isEmpty())
    }

    @Test fun entireParticleDisksStayOutsideRoundedFillAtAllSampledPhases() {
        val sizes = listOf(320f to 160f, 320f to 480f, 56f to 56f)
        for ((width, height) in sizes) {
            val radius = minOf(32f, width / 2f, height / 2f)
            for (density in listOf(1f, 2f, 3f)) {
                for (time in listOf(0f, 3.25f, 30f, 75f, 119.9f)) {
                    neuralExpressiveSeeds(96).forEach { seed ->
                        val particle = neuralExpressiveParticle(
                            seed, width * density, height * density, radius * density, density, time
                        )
                        // Signed distance to the rounded rectangle, positive outside.
                        val qx = abs(particle.center.x - width * density / 2f) -
                            (width / 2f - radius) * density
                        val qy = abs(particle.center.y - height * density / 2f) -
                            (height / 2f - radius) * density
                        val distance = hypot(max(qx, 0f), max(qy, 0f)) +
                            minOf(max(qx, qy), 0f) - radius * density
                        assertTrue("Particle disk intersects card: $particle", distance >= particle.radius - 0.01f)
                        assertTrue(particle.alpha in 0f..1f)
                        assertTrue(particle.center.x.isFinite() && particle.center.y.isFinite())
                    }
                }
            }
        }
    }

    @Test fun fixedTimeIsRepeatableAndDifferentTimeMovesParticles() {
        val seed = neuralExpressiveSeeds(96)[17]
        fun frame(time: Float) = neuralExpressiveParticle(seed, 320f, 160f, 32f, 1f, time)
        assertEquals(frame(4f), frame(4f))
        assertTrue(frame(4f).center != frame(8f).center)
    }

    @Test fun everyMotionGateCanStopFrames() {
        fun allowed(
            enabled: Boolean = true,
            visible: Boolean = true,
            started: Boolean = true,
            viewVisible: Boolean = true,
            powerSave: Boolean = false,
            scale: Float = 1f
        ) = neuralExpressiveMotionAllowed(enabled, visible, started, viewVisible, powerSave, scale)
        assertTrue(allowed())
        assertFalse(allowed(enabled = false))
        assertFalse(allowed(visible = false))
        assertFalse(allowed(started = false))
        assertFalse(allowed(viewVisible = false))
        assertFalse(allowed(powerSave = true))
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertFalse(allowed(scale = it))
        }
    }
}
