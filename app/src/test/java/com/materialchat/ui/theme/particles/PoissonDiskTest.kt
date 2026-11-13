package com.materialchat.ui.theme.particles

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Mirrors essentials' toroidally wrapped Poisson-disk tile contract. */
class PoissonDiskTest {
    private val minDistance = 6f

    @Test fun tileIsDeterministicForTheSameSeed() {
        val a = generatePoissonTile(minDistance)
        val b = generatePoissonTile(minDistance)
        assertTrue(a.coords.contentEquals(b.coords))
        val other = generatePoissonTile(minDistance, seed = 1234L)
        assertTrue("a different seed must produce a different field", !a.coords.contentEquals(other.coords))
    }

    @Test fun tileCoversEightyByFortyMinimumDistances() {
        val tile = generatePoissonTile(minDistance)
        assertEquals(minDistance * 80f, tile.width, 0f)
        assertEquals(minDistance * 40f, tile.height, 0f)
        val count = tile.coords.size / 2
        assertTrue("expected a densely packed tile, got $count", count in 800..4000)
    }

    @Test fun everyPairRespectsTheMinimumDistanceAcrossTheWrap() {
        val tile = generatePoissonTile(minDistance)
        val count = tile.coords.size / 2
        for (i in 0 until count) {
            val ax = tile.coords[i * 2]
            val ay = tile.coords[i * 2 + 1]
            assertTrue(ax >= 0f && ax < tile.width)
            assertTrue(ay >= 0f && ay < tile.height)
            for (j in i + 1 until count) {
                val dx = abs(tile.coords[j * 2] - ax).let { min(it, tile.width - it) }
                val dy = abs(tile.coords[j * 2 + 1] - ay).let { min(it, tile.height - it) }
                assertTrue(
                    "points $i and $j are ${hypot(dx, dy)} apart (min $minDistance)",
                    hypot(dx, dy) >= minDistance - 0.001f
                )
            }
        }
    }

    @Test fun visitTilesTheRequestedBoundsWithoutSeams() {
        val tile = generatePoissonTile(minDistance)
        // The same local neighbourhood must be produced by adjacent tile copies.
        val single = mutableListOf<Offset>()
        tile.visit(androidx.compose.ui.geometry.Rect(10f, 10f, 60f, 60f)) { x, y -> single += Offset(x, y) }
        val shifted = mutableListOf<Offset>()
        tile.visit(
            androidx.compose.ui.geometry.Rect(10f + tile.width, 10f + tile.height, 60f + tile.width, 60f + tile.height)
        ) { x, y -> shifted += Offset(x - tile.width, y - tile.height) }
        assertEquals(single, shifted)
        assertTrue("bounds must contain particles", single.isNotEmpty())
        single.forEach {
            assertTrue(it.x in 10f..60f && it.y in 10f..60f)
        }
    }

    @Test fun cancellationHookRunsDuringGeneration() {
        var checks = 0
        generatePoissonTile(minDistance, checkCancelled = { checks++ })
        assertTrue("generation must poll the cancellation hook, saw $checks", checks > 0)
    }

    @Test fun nonPositiveMinDistanceIsRejected() {
        for (value in listOf(0f, -1f, Float.NaN)) {
            try {
                generatePoissonTile(value)
                fail("minDistance=$value should have been rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}
