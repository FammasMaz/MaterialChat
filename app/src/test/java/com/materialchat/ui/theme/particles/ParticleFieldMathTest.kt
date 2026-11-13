package com.materialchat.ui.theme.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Mirrors essentials' curl-noise motion, radius reference and per-particle band culling. */
class ParticleFieldMathTest {
    private val density = Density(1f)
    private val fieldStyle = ParticleFieldStyle()
    private val style = fieldStyle.resolve(density)
    private val geometry = CapsuleParticleGeometry.create(Size(320f, 160f), 0f, 32f)!!
    private val tile = generatePoissonTile(style.minDistance)

    private fun particles(time: Float): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        visitParticles(tile, geometry, style, Offset.Zero, time) { x, y, radius, opacity ->
            out += floatArrayOf(x, y, radius, opacity)
        }
        return out
    }

    @Test fun flowNoiseIsDeterministicBoundedAndContinuous() {
        for (x in listOf(0f, 0.5f, 13.25f, -7.5f, 120f)) {
            for (y in listOf(0f, 1.75f, -3.5f, 64f)) {
                val value = flowNoise(x, y)
                assertEquals(value, flowNoise(x, y), 0f)
                assertTrue("noise out of range at ($x,$y): $value", abs(value) < 3f)
                assertTrue("noise must be continuous", abs(flowNoise(x + 0.001f, y) - value) < 0.05f)
            }
        }
    }

    @Test fun curlIsDeterministicAndScalesWithDisplacement() {
        curl(40f, 60f, 1.5f, 1f / style.noiseScale, style.displacement)
        val x1 = curlX[0]
        val y1 = curlX[1]
        curl(40f, 60f, 1.5f, 1f / style.noiseScale, style.displacement)
        assertEquals(x1, curlX[0], 0f)
        assertEquals(y1, curlX[1], 0f)
        assertTrue(x1.isFinite() && y1.isFinite())
        curl(40f, 60f, 1.5f, 1f / style.noiseScale, style.displacement * 2f)
        assertEquals(x1 * 2f, curlX[0], 0.001f)
        assertEquals(y1 * 2f, curlX[1], 0.001f)
    }

    @Test fun referenceRadiusStaysInsideTheConfiguredRangeAndShrinksWithSpeed() {
        val fast = referenceParticleRadius(1_000f, 0f, 0f, 1.5f)
        val slow = referenceParticleRadius(0f, 1f, 0f, 1.5f)
        assertEquals(1.5f, slow, 0.0001f)
        assertTrue(fast < slow)
        assertEquals(1.5f, referenceParticleRadius(0f, 1f, 0f, 1.5f), 0.0001f)
        assertEquals(1f, referenceParticleRadius(1_000f, 0f, 1f, 1.5f), 0.0001f)
        assertEquals(0f, referenceParticleRadius(0f, 1f, 0f, 0f), 0f)
    }

    @Test fun particlesStayInsideTheBandAndCarryBoundedAlphaAndRadius() {
        val drawn = particles(0f)
        assertTrue("expected a visible field, got ${drawn.size}", drawn.size > 50)
        drawn.forEach { p ->
            val distance = geometry.signedDistance(Offset(p[0], p[1]))
            assertTrue("particle at ${p[0]},${p[1]} escaped the band: $distance", distance >= -style.innerBand - 1f)
            assertTrue("particle at ${p[0]},${p[1]} escaped the band: $distance", distance <= style.outerBand + 1f)
            // The painter multiplies this by the style opacity; visitParticles yields the falloff.
            assertTrue("opacity ${p[3]} out of range", p[3] > 0f && p[3] <= 1f)
            assertTrue("radius ${p[2]} out of range", p[2] > 0f && p[2] <= style.radius + 1e-6f)
        }
    }

    @Test fun fixedTimeIsRepeatableAndTimeMovesParticles() {
        val first = particles(4f)
        val second = particles(4f)
        assertEquals(first.size, second.size)
        first.indices.forEach { i -> assertTrue(first[i].contentEquals(second[i])) }
        val later = particles(9f)
        assertTrue("time must move particles", later.isNotEmpty())
        val moved = first.indices.count { i ->
            i < later.size && (abs(first[i][0] - later[i][0]) > 0.01f || abs(first[i][1] - later[i][1]) > 0.01f)
        }
        assertTrue("expected motion between frames, moved=$moved", moved > first.size / 4)
    }

    @Test fun screenOriginOnlyShiftsNoiseSamplingNotTheDrawnExtent() {
        val maxExtent = style.radius + 1f
        val out = mutableListOf<Offset>()
        visitParticles(tile, geometry, style, Offset(10_000f, 10_000f), 0f) { x, y, _, _ ->
            out += Offset(x, y)
        }
        assertTrue("a shifted origin must still produce the field", out.isNotEmpty())
        out.forEach {
            assertTrue("particle escaped the node: $it", it.x >= -maxExtent && it.x <= geometry.viewport.width + maxExtent)
            assertTrue("particle escaped the node: $it", it.y >= -maxExtent && it.y <= geometry.viewport.height + maxExtent)
        }
    }
}
