package com.materialchat.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pins the lattice's motion to what the Gemini Live reference measured (see [LatticeTwinkle]):
 * dots twinkle in place on 3.5-7.5 s cycles, about 40% lit at any instant, neighbours
 * independent, and the pill envelope removes the dots from the pill's interior.
 */
class AssistantLatticeTwinkleTest {

    private val dots = 4000

    @Test
    fun hashIsUniformAndDeterministic() {
        val values = FloatArray(dots) { LatticeTwinkle.hash(it, 7) }
        assertTrue(values.all { it >= 0f && it < 1f })
        assertEquals(0.5f, values.average().toFloat(), 0.03f)
        assertEquals(LatticeTwinkle.hash(123, 7), LatticeTwinkle.hash(123, 7), 0f)
        assertTrue("different salts decorrelate", abs(LatticeTwinkle.hash(123, 1) - LatticeTwinkle.hash(123, 2)) > 1e-6f)
    }

    @Test
    fun periodsSpanTheMeasuredCyclesAndFavourTheLongOnes() {
        val periods = FloatArray(dots) { LatticeTwinkle.period(it) }
        assertTrue(periods.all { it >= LatticeTwinkle.MIN_PERIOD_SECONDS && it <= LatticeTwinkle.MAX_PERIOD_SECONDS })
        // 0.14 Hz (~7 s) was the majority in the reference, 0.29 Hz (~3.5 s) the minority.
        val long = periods.count { it >= 6f }
        val short = periods.count { it <= 4.5f }
        assertTrue("long cycles must dominate (long=$long short=$short)", long > 2 * short)
    }

    @Test
    fun levelIsBoundedAndPeriodic() {
        for (i in 0 until 50) {
            val period = LatticeTwinkle.period(i)
            val phase = LatticeTwinkle.phase(i)
            var t = 0f
            while (t < 20f) {
                val level = LatticeTwinkle.level(period, phase, t)
                assertTrue(level in 0f..1f)
                assertEquals("one period later the dot is at the same level", level, LatticeTwinkle.level(period, phase, t + period), 1e-3f)
                t += 0.37f
            }
        }
    }

    @Test
    fun aboutFortyPercentOfTheDotsAreLitAtAnyInstant() {
        // Reference: 27-43% lit per frame. The lit window (cos >= 0.3) covers 40.3% of a cycle.
        for (time in listOf(0f, 1.3f, 4.7f, 9.2f, 33.3f)) {
            val lit = (0 until dots).count { LatticeTwinkle.level(it, time) >= 0.5f }
            val fraction = lit.toFloat() / dots
            assertTrue("lit fraction at t=$time was $fraction", fraction in 0.33f..0.47f)
        }
    }

    @Test
    fun theLitSetChangesOverTimeWithoutAnyDotMoving() {
        val before = BooleanArray(dots) { LatticeTwinkle.level(it, 0f) >= 0.5f }
        val after = BooleanArray(dots) { LatticeTwinkle.level(it, 2f) >= 0.5f }
        val changed = (0 until dots).count { before[it] != after[it] }
        // Two seconds is 0.27-0.57 of a cycle, so most dots have switched state.
        assertTrue("only $changed of $dots dots switched in 2 s", changed > dots * 0.4)
    }

    @Test
    fun neighboursSwitchIndependently() {
        // Reference: spatial phase coherence R = 0.30, neighbour lag 0. Adjacent indices are
        // adjacent dots on a row, so their phases must not line up.
        var sx = 0.0
        var sy = 0.0
        for (i in 0 until dots - 1) {
            val d = 2.0 * PI * (LatticeTwinkle.phase(i + 1) - LatticeTwinkle.phase(i))
            sx += cos(d)
            sy += sin(d)
        }
        val coherence = hypot(sx, sy) / (dots - 1)
        assertTrue("neighbour phase coherence $coherence is too high", coherence < 0.1)
    }

    @Test
    fun pillEnvelopeRemovesDotsInsideAndIsLiveliestAtTheEdge() {
        val inner = 20f
        val near = 24f
        val far = 0.6f
        assertEquals("deep inside the pill", 0f, LatticeTwinkle.pillFactor(-inner, inner, near, far), 0f)
        assertEquals("deeper still", 0f, LatticeTwinkle.pillFactor(-100f, inner, near, far), 0f)
        assertEquals("at the boundary", 1f, LatticeTwinkle.pillFactor(0f, inner, near, far), 1e-4f)
        assertEquals("far outside", far, LatticeTwinkle.pillFactor(near, inner, near, far), 1e-4f)
        assertEquals("further out", far, LatticeTwinkle.pillFactor(500f, inner, near, far), 1e-4f)
        assertEquals("no pill known", far, LatticeTwinkle.pillFactor(Float.NaN, inner, near, far), 0f)
        // Monotone: rising across the blurred edge, falling away outside.
        var previous = 0f
        var d = -inner
        while (d <= 0f) {
            val f = LatticeTwinkle.pillFactor(d, inner, near, far)
            assertTrue(f >= previous - 1e-5f)
            previous = f
            d += 1f
        }
        previous = 1f
        d = 0f
        while (d <= near) {
            val f = LatticeTwinkle.pillFactor(d, inner, near, far)
            assertTrue(f <= previous + 1e-5f && f >= far - 1e-5f)
            previous = f
            d += 1f
        }
    }
}
