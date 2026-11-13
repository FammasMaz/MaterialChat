package com.materialchat.ui.theme.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Mirrors essentials' capsule signed-distance and perimeter sampling contract. */
class CapsuleParticleGeometryTest {
    private val viewport = Size(320f, 160f)
    private val geometry = CapsuleParticleGeometry.create(viewport, 0f, 32f)!!

    @Test fun signedDistanceMatchesTheAnalyticStadium() {
        // Centre is half the short side inside the capsule.
        assertEquals(-80f, geometry.signedDistance(Offset(160f, 80f)), 0.001f)
        // Straight edges: the distance equals the offset from the edge.
        for (d in listOf(0.5f, 4f, 20f, 60f)) {
            assertEquals(d, geometry.signedDistance(Offset(320f + d, 80f)), 0.001f)
            assertEquals(d, geometry.signedDistance(Offset(-d, 80f)), 0.001f)
            assertEquals(d, geometry.signedDistance(Offset(160f, -d)), 0.001f)
            assertEquals(d, geometry.signedDistance(Offset(160f, 160f + d)), 0.001f)
            assertEquals(-d, geometry.signedDistance(Offset(320f - d, 80f)), 0.001f)
        }
        // Corner arcs sit on the rounded corner centres.
        val r = 32f
        val cx = 160f - (160f - r)
        val cy = 80f - (80f - r)
        for (degrees in listOf(180f, 225f, 270f)) {
            val a = Math.toRadians(degrees.toDouble())
            val point = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
            assertEquals(0f, geometry.signedDistance(point), 0.01f)
        }
    }

    @Test fun perimeterMatchesTheStadiumFormula() {
        val expected = 4f * ((320f / 2f - 32f) + (160f / 2f - 32f)) + (2 * Math.PI * 32.0).toFloat()
        assertEquals(expected, geometry.perimeter, 0.01f)
    }

    @Test fun sampledPointsLieOnTheBoundaryWithUnitNormals() {
        val perimeter = geometry.perimeter
        var s = 0f
        while (s < perimeter) {
            val (point, normal) = geometry.sample(s)
            assertEquals("sample($s) must lie on the boundary", 0f, geometry.signedDistance(point), 0.02f)
            assertEquals("sample($s) normal must be unit length", 1f, hypot(normal.x, normal.y), 0.001f)
            s += 0.5f
        }
    }

    @Test fun samplingWrapsAroundThePerimeter() {
        val perimeter = geometry.perimeter
        fun assertSameSample(expected: Float, actual: Float) {
            val (a, an) = geometry.sample(expected)
            val (b, bn) = geometry.sample(actual)
            assertEquals("x for $expected vs $actual", a.x, b.x, 0.05f)
            assertEquals("y for $expected vs $actual", a.y, b.y, 0.05f)
            assertEquals("normal x for $expected vs $actual", an.x, bn.x, 0.01f)
            assertEquals("normal y for $expected vs $actual", an.y, bn.y, 0.01f)
        }
        assertSameSample(0f, perimeter)
        assertSameSample(7f, 7f + perimeter * 3f)
        assertSameSample(7f, 7f - perimeter)
    }

    @Test fun createRejectsEmptyOrInvalidInput() {
        assertNull(CapsuleParticleGeometry.create(Size(0f, 160f), 0f, 32f))
        assertNull(CapsuleParticleGeometry.create(Size(320f, Float.NaN), 0f, 32f))
        assertNull(CapsuleParticleGeometry.create(viewport, -1f, 32f))
        assertNull(CapsuleParticleGeometry.create(viewport, Float.NaN, 32f))
        assertNull(CapsuleParticleGeometry.create(viewport, 0f, Float.NaN))
        assertNull(CapsuleParticleGeometry.create(viewport, 0f, 32f, Rect(10f, 10f, 10f, 40f)))
        assertNull(CapsuleParticleGeometry.create(viewport, 0f, 32f, Rect(0f, 0f, Float.NaN, 40f)))
    }

    @Test fun cornerRadiusIsClampedToHalfTheShortSide() {
        val clamped = CapsuleParticleGeometry.create(viewport, 0f, 500f)!!
        assertEquals(80f, clamped.radius, 0f)
        val inset = CapsuleParticleGeometry.create(viewport, 64f, 32f)!!
        assertEquals(Rect(64f, 64f, 256f, 96f), inset.rect)
    }

    @Test fun activationIsClampedAndRejectsNonFiniteValues() {
        assertEquals(0f, particleActivation(-3f), 0f)
        assertEquals(1f, particleActivation(3f), 0f)
        assertEquals(0.25f, particleActivation(0.25f), 0f)
        assertEquals(0f, particleActivation(Float.NaN), 0f)
        assertEquals(0f, particleActivation(Float.POSITIVE_INFINITY), 0f)
        assertTrue(particleActivation(0.5f) in 0f..1f)
    }
}
