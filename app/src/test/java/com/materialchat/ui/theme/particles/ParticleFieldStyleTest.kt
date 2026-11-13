package com.materialchat.ui.theme.particles

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Mirrors essentials' ParticleFieldStyle contract: defaults, validation and pixel resolution. */
class ParticleFieldStyleTest {
    private val density = Density(2f)

    @Test fun defaultsMatchTheReferenceEffect() {
        val style = ParticleFieldStyle()
        assertEquals(6.dp, style.minDistance)
        assertEquals(0.dp, style.minRadius)
        assertEquals(1.5.dp, style.maxRadius)
        assertEquals(12.dp, style.displacement)
        assertEquals(64.dp, style.outerBand)
        assertEquals(48.dp, style.innerBand)
        assertEquals(ParticleShape.Circle, style.shape)
        assertEquals(4f, style.falloffExponent, 0f)
        assertEquals(0.35f, style.opacity, 0f)
        assertEquals(1f, style.speed, 0f)
        assertEquals((1f / ParticleFieldStyle.DEFAULT_NOISE_FREQUENCY).dp, style.noiseScale)
    }

    @Test fun resolveScalesEveryLengthByDensity() {
        val pixels = ParticleFieldStyle().resolve(density)
        assertEquals(12f, pixels.minDistance, 0.001f)
        assertEquals(0f, pixels.minRadius, 0.001f)
        assertEquals(3f, pixels.radius, 0.001f)
        assertEquals(24f, pixels.displacement, 0.001f)
        assertEquals(128f, pixels.outerBand, 0.001f)
        assertEquals(96f, pixels.innerBand, 0.001f)
        assertEquals(2f, pixels.density, 0.001f)
        assertEquals(2f / ParticleFieldStyle.DEFAULT_NOISE_FREQUENCY, pixels.noiseScale, 0.01f)
        assertEquals(ParticleShape.Circle, pixels.shape)
        assertEquals(4f, pixels.falloffExponent, 0f)
    }

    @Test fun invalidParametersAreRejected() {
        val invalid = listOf<() -> ParticleFieldStyle>(
            { ParticleFieldStyle(minDistance = 0.dp) },
            { ParticleFieldStyle(minDistance = (-1).dp) },
            { ParticleFieldStyle(minRadius = 2.dp, maxRadius = 1.dp) },
            { ParticleFieldStyle(maxRadius = (-1).dp) },
            { ParticleFieldStyle(displacement = (-1).dp) },
            { ParticleFieldStyle(innerBand = (-1).dp) },
            { ParticleFieldStyle(outerBand = 0.dp) },
            { ParticleFieldStyle(noiseScale = 0.dp) },
            { ParticleFieldStyle(speed = -0.5f) },
            { ParticleFieldStyle(speed = Float.NaN) },
            { ParticleFieldStyle(falloffExponent = -1f) },
            { ParticleFieldStyle(opacity = 1.5f) },
            { ParticleFieldStyle(opacity = Float.NaN) },
        )
        invalid.forEachIndexed { index, build ->
            try {
                build()
                fail("Style #$index should have been rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test fun alphaEnvelopeIsFullAtTheEdgeAndZeroAtBothBandLimits() {
        val inner = 48f
        val outer = 64f
        assertEquals(1f, particleAlphaEnvelope(0f, inner, outer), 0.0001f)
        assertEquals(0f, particleAlphaEnvelope(outer, inner, outer), 0.0001f)
        assertEquals(0f, particleAlphaEnvelope(-inner, inner, outer), 0.0001f)
        // Smoothstep midpoint on both sides.
        assertEquals(0.5f, particleAlphaEnvelope(outer / 2f, inner, outer), 0.0001f)
        assertEquals(0.5f, particleAlphaEnvelope(-inner / 2f, inner, outer), 0.0001f)
        // Beyond the bands the envelope stays clamped.
        assertEquals(0f, particleAlphaEnvelope(outer * 4f, inner, outer), 0.0001f)
        assertEquals(0f, particleAlphaEnvelope(-inner * 4f, inner, outer), 0.0001f)
    }

    @Test fun alphaEnvelopeDecreasesMonotonicallyOutwardsAndInwards() {
        val inner = 48f
        val outer = 64f
        var previous = particleAlphaEnvelope(0f, inner, outer)
        var d = 0.5f
        while (d <= outer) {
            val value = particleAlphaEnvelope(d, inner, outer)
            assertTrue("outward envelope must not increase at $d", value <= previous + 1e-6f)
            previous = value
            d += 0.5f
        }
        previous = particleAlphaEnvelope(0f, inner, outer)
        d = -0.5f
        while (d >= -inner) {
            val value = particleAlphaEnvelope(d, inner, outer)
            assertTrue("inward envelope must not increase at $d", value <= previous + 1e-6f)
            previous = value
            d -= 0.5f
        }
    }

    @Test fun zeroInnerBandOnlyAllowsTheOuterSide() {
        assertEquals(0f, particleAlphaEnvelope(-1f, 0f, 64f), 0f)
        assertEquals(1f, particleAlphaEnvelope(0f, 0f, 64f), 0f)
        assertTrue(particleAlphaEnvelope(10f, 0f, 64f) > 0f)
    }
}
