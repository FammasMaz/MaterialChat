package com.materialchat.ui.theme.particles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Presence (fade in/out) timing and the frame-request gate of the neural particle field. */
class ParticleFadeTest {
    private val spec = ParticleFadeSpec(fadeInMillis = 600L, fadeOutMillis = 800L)

    @Test fun defaultsAreInTheRequestedRange() {
        val default = ParticleFadeSpec()
        assertEquals(600L, default.fadeInMillis)
        assertEquals(800L, default.fadeOutMillis)
        assertTrue(default.fadeOutMillis in 600L..900L)
    }

    @Test fun negativeDurationsAreRejected() {
        listOf({ ParticleFadeSpec(fadeInMillis = -1L) }, { ParticleFadeSpec(fadeOutMillis = -1L) }).forEach { build ->
            try {
                build()
                fail("negative duration should be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test fun targetIsOnlyShownWhileActiveAndNotIdleExpired() {
        assertEquals(1f, fadeTarget(active = true, idleExpired = false), 0f)
        assertEquals(0f, fadeTarget(active = false, idleExpired = false), 0f)
        assertEquals(0f, fadeTarget(active = true, idleExpired = true), 0f)
        assertEquals(0f, fadeTarget(active = false, idleExpired = true), 0f)
    }

    @Test fun idleTimeoutIsArmedOnlyWhenPositive() {
        assertEquals(null, effectiveIdleTimeout(null))
        assertEquals(null, effectiveIdleTimeout(0L))
        assertEquals(null, effectiveIdleTimeout(-5L))
        assertEquals(4_000L, effectiveIdleTimeout(4_000L))
    }

    @Test fun fadeOutTakesTheFadeOutDurationAndEndsAtZero() {
        assertEquals(1f, fadePresence(1f, 0f, 0L, spec), 0f)
        assertEquals(0.5f, fadePresence(1f, 0f, 400L, spec), 0.0001f)
        assertEquals(0f, fadePresence(1f, 0f, 800L, spec), 0f)
        assertEquals(0f, fadePresence(1f, 0f, 5_000L, spec), 0f)
        assertTrue(fadePresence(1f, 0f, 799L, spec) > 0f)
    }

    @Test fun fadeInTakesTheFadeInDurationAndEndsAtOne() {
        assertEquals(0f, fadePresence(0f, 1f, 0L, spec), 0f)
        assertEquals(0.5f, fadePresence(0f, 1f, 300L, spec), 0.0001f)
        assertEquals(1f, fadePresence(0f, 1f, 600L, spec), 0f)
        assertTrue(fadePresence(0f, 1f, 599L, spec) < 1f)
    }

    @Test fun fadeIsMonotonicAndSmoothstepEased() {
        var previous = 1f
        for (t in 0L..800L step 10L) {
            val value = fadePresence(1f, 0f, t, spec)
            assertTrue("fade out must not increase at $t", value <= previous + 1e-6f)
            assertTrue(value in 0f..1f)
            previous = value
        }
        // Ease-in-out: slower at the ends than in the middle.
        val early = 1f - fadePresence(1f, 0f, 80L, spec)
        val middle = fadePresence(1f, 0f, 360L, spec) - fadePresence(1f, 0f, 440L, spec)
        assertTrue("smoothstep must be slow at the start", early < middle)
        assertEquals(0f, fadeEase(0f), 0f)
        assertEquals(1f, fadeEase(1f), 0f)
        assertEquals(0.5f, fadeEase(0.5f), 0.0001f)
        assertEquals(0f, fadeEase(-3f), 0f)
        assertEquals(1f, fadeEase(7f), 0f)
    }

    @Test fun reversingMidFadeStartsFromTheCurrentPresence() {
        val midway = fadePresence(1f, 0f, 400L, spec)
        assertEquals(midway, fadePresence(midway, 1f, 0L, spec), 0f)
        val halfBack = fadePresence(midway, 1f, 300L, spec)
        assertEquals(midway + (1f - midway) * 0.5f, halfBack, 0.0001f)
        assertEquals(1f, fadePresence(midway, 1f, 600L, spec), 0f)
    }

    @Test fun animatorDurationScaleScalesTheFadeAndZeroSnaps() {
        assertEquals(0f, fadePresence(1f, 0f, 0L, spec, durationScale = 0f), 0f)
        assertEquals(1f, fadePresence(0f, 1f, 0L, spec, durationScale = 0f), 0f)
        assertEquals(0f, fadePresence(1f, 0f, 0L, spec, durationScale = Float.NaN), 0f)
        // Half speed: the 800ms fade out takes 1600ms.
        assertEquals(0.5f, fadePresence(1f, 0f, 800L, spec, durationScale = 2f), 0.0001f)
        assertEquals(0f, fadePresence(1f, 0f, 1_600L, spec, durationScale = 2f), 0f)
        assertEquals(0f, fadePresence(1f, 0f, 400L, spec, durationScale = 0.5f), 0f)
    }

    @Test fun zeroDurationSnapsToTheTarget() {
        val instant = ParticleFadeSpec(fadeInMillis = 0L, fadeOutMillis = 0L)
        assertEquals(0f, fadePresence(1f, 0f, 0L, instant), 0f)
        assertEquals(1f, fadePresence(0f, 1f, 0L, instant), 0f)
    }

    @Test fun framesAreRequestedOnlyWhileVisibleAnimatedAndPresent() {
        assertTrue(shouldRequestParticleFrames(true, 1f, true, true, 1f))
        assertTrue(shouldRequestParticleFrames(true, 0.01f, true, true, 0.5f))
        // Fully faded: no frames even though every other gate is open.
        assertFalse(shouldRequestParticleFrames(true, 0f, true, true, 1f))
        assertFalse(shouldRequestParticleFrames(false, 1f, true, true, 1f))
        assertFalse(shouldRequestParticleFrames(true, 1f, false, true, 1f))
        assertFalse(shouldRequestParticleFrames(true, 1f, true, false, 1f))
        assertFalse(shouldRequestParticleFrames(true, 1f, true, true, 0f))
    }
}
