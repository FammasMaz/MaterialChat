package com.materialchat.assistant.ui

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The lattice is laid out from the full-window node so it spans the whole assistant window
 * regardless of the pill's bounds. Its geometry is pure, so it is pinned here: the band sits at
 * the bottom, the grid keeps its pitch on both axes, the dots fade upward, and nothing is drawn
 * above the band.
 */
class AssistantLatticeGeometryTest {

    private val pitch = 7f

    @Test
    fun bandOccupiesTheBottomFractionOfTheNode() {
        val geometry = LatticeGeometry.layout(Size(320f, 640f), pitchPx = pitch)
        assertEquals(640f - 640f * AssistantLatticeStyle.BAND_FRACTION, geometry.bandTop, 0.01f)
        assertTrue("every row must sit inside the band", geometry.rows.all { it.y >= geometry.bandTop })
        assertTrue("the lattice must reach the node's bottom edge", geometry.rows.first().y > 640f - pitch)
    }

    @Test
    fun rowsAndColumnsKeepThePitch() {
        val geometry = LatticeGeometry.layout(Size(320f, 640f), pitchPx = pitch)
        val ys = geometry.rows.map { it.y }
        ys.zipWithNext { lower, upper ->
            assertEquals("rows are one pitch apart", pitch, lower - upper, 0.01f)
        }
        val xs = geometry.rows.first().points.map { it.x }
        xs.zipWithNext { left, right -> assertEquals("columns are one pitch apart", pitch, right - left, 0.01f) }
        assertTrue("the lattice spans the full width", xs.first() < pitch && xs.last() > 320f - pitch)
    }

    @Test
    fun dotsFadeUpwardSoTheBandDissolvesInsteadOfCuttingOff() {
        val geometry = LatticeGeometry.layout(Size(320f, 640f), pitchPx = pitch)
        val bottom = geometry.rows.first().alpha
        val top = geometry.rows.last().alpha
        // The bottom row sits half a pitch above the edge, so it is near- but not quite full.
        assertTrue("the bottom row is at full strength (was $bottom)", bottom >= 0.9f)
        assertTrue("the top row is nearly gone (was $top)", top < 0.06f)
        geometry.rows.zipWithNext { lower, upper ->
            assertTrue("alpha must fall monotonically upward", upper.alpha <= lower.alpha + 1e-4f)
        }
    }

    @Test
    fun nothingIsDrawnAboveTheBand() {
        val geometry = LatticeGeometry.layout(Size(320f, 640f), pitchPx = pitch)
        assertTrue(
            "no dot may sit above the band top",
            geometry.rows.all { row -> row.points.all { abs(it.y - row.y) < 0.01f && it.y >= geometry.bandTop } }
        )
    }

    @Test
    fun shortHostsKeepAMinimumBand() {
        // Keyboard up: the host shrinks, and a pure fraction would collapse the texture.
        val minBand = 160f
        val geometry = LatticeGeometry.layout(Size(320f, 300f), pitchPx = pitch, minBandPx = minBand)
        assertEquals(300f - minBand, geometry.bandTop, 0.01f)
        val tall = LatticeGeometry.layout(Size(320f, 800f), pitchPx = pitch, minBandPx = minBand)
        assertEquals("a tall host is not affected by the floor", 800f - 800f * 0.24f, tall.bandTop, 0.01f)
    }

    @Test
    fun degenerateSizesProduceNoDots() {
        assertTrue(LatticeGeometry.layout(Size(0f, 640f), pitchPx = pitch).rows.isEmpty())
        assertTrue(LatticeGeometry.layout(Size(320f, 640f), pitchPx = 0f).rows.isEmpty())
    }
}
