package com.materialchat.ui.theme.particles

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Edge-treatment selection (RenderEffect blur vs gradient fallback) and halo ring geometry. */
class CapsuleHaloTest {
    @Test fun blurIsChosenOnlyOnApi31PlusWithHardwareCanvasAndPositiveRadius() {
        assertEquals(ParticleEdgeTreatment.Blur, resolveParticleEdgeTreatment(31, 4f, true))
        assertEquals(ParticleEdgeTreatment.Blur, resolveParticleEdgeTreatment(35, 4f, true))
        assertEquals(ParticleEdgeTreatment.Gradient, resolveParticleEdgeTreatment(30, 4f, true))
        assertEquals(ParticleEdgeTreatment.Gradient, resolveParticleEdgeTreatment(26, 4f, true))
        assertEquals(ParticleEdgeTreatment.Gradient, resolveParticleEdgeTreatment(35, 4f, false))
        assertEquals(ParticleEdgeTreatment.Gradient, resolveParticleEdgeTreatment(35, 0f, true))
        assertEquals(ParticleEdgeTreatment.Gradient, resolveParticleEdgeTreatment(35, Float.NaN, true))
    }

    @Test fun edgeStyleDefaultsAndValidation() {
        val style = ParticleEdgeStyle()
        assertEquals(14.dp, style.blurRadius)
        assertEquals(20.dp, style.haloWidth)
        assertEquals(0.45f, style.haloOpacity, 0f)
        assertEquals(8, style.haloSteps)
        val invalid = listOf<() -> ParticleEdgeStyle>(
            { ParticleEdgeStyle(blurRadius = (-1).dp) },
            { ParticleEdgeStyle(haloWidth = (-1).dp) },
            { ParticleEdgeStyle(haloOpacity = 1.5f) },
            { ParticleEdgeStyle(haloOpacity = Float.NaN) },
            { ParticleEdgeStyle(haloSteps = -1) },
        )
        invalid.forEachIndexed { index, build ->
            try {
                build()
                fail("Edge style #$index should have been rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test fun haloRingsTileOutwardFromTheEdgeWithoutEnteringTheCapsule() {
        val rings = haloRings(widthPx = 48f, steps = 8, opacity = 0.2f)
        assertTrue(rings.isNotEmpty() && rings.size <= 8)
        rings.forEachIndexed { i, ring ->
            val inner = ring.outset - ring.strokeWidth / 2f
            val outer = ring.outset + ring.strokeWidth / 2f
            assertTrue("ring $i inner edge $inner must not enter the capsule", inner >= -1e-4f)
            assertTrue("ring $i outer edge $outer must stay within the halo width", outer <= 48f + 1e-4f)
            assertEquals(6f, ring.strokeWidth, 1e-4f)
            assertTrue(ring.alpha > 0f && ring.alpha <= 0.2f)
        }
        // First ring starts at the edge; strongest there, fading outward.
        assertEquals(3f, rings.first().outset, 1e-4f)
        for (i in 1 until rings.size) assertTrue(rings[i].alpha < rings[i - 1].alpha)
        assertTrue(rings.first().alpha > 0.19f)
    }

    @Test fun haloIsDisabledByZeroStepsWidthOrOpacity() {
        assertTrue(haloRings(48f, 0, 0.2f).isEmpty())
        assertTrue(haloRings(0f, 8, 0.2f).isEmpty())
        assertTrue(haloRings(48f, 8, 0f).isEmpty())
        assertTrue(haloRings(Float.NaN, 8, 0.2f).isEmpty())
    }
}
