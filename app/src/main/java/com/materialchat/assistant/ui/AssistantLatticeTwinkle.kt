package com.materialchat.assistant.ui

import kotlin.math.PI
import kotlin.math.cos

/**
 * The lattice's motion, measured from the Gemini Live reference recording
 * (screen-20261115-000658, 827 frames at 90 fps, 1344x2992):
 *
 *  - The grid does NOT translate. After the ~0.1 s entry slide, phase correlation of the
 *    wallpaper-subtracted dot band gives dx = dy = 0 px at every gap from 9 ms to 829 ms, blob
 *    tracking gives a median per-dot velocity of 0.00 px/s, and y-t slices show straight
 *    horizontal dot rows. Positions are fixed; only brightness changes.
 *  - Dots twinkle in place: per-dot peak-to-peak amplitude median 158/255 (off ~7, on ~210),
 *    dominant frequencies 0.14 Hz (41% of dots) and 0.29 Hz (19%), i.e. 3.5-7 s cycles.
 *  - 27-43% of the dots are lit at any instant.
 *  - Neighbours switch independently: spatial phase coherence R = 0.30, neighbour lag 0 frames,
 *    circular phase sd 1.88 rad. There is no travelling wave.
 *  - The twinkle is stronger just outside the pill (temporal sd 66 within 60 px of its bottom
 *    edge vs ~30 further down) and absent inside the pill's opaque interior.
 *
 * Everything here is pure so the timing and the pill envelope are unit tested; the modifier only
 * feeds it a clock.
 */
internal object LatticeTwinkle {
    /** Shortest per-dot cycle (the reference's 0.29 Hz dots). */
    const val MIN_PERIOD_SECONDS = 3.5f

    /** Longest per-dot cycle (just past the reference's 0.14 Hz dots). */
    const val MAX_PERIOD_SECONDS = 7.5f

    /** The lit window on the dot's cosine (0..1): below [LIT_START] the dot is off, above [LIT_END] fully on. */
    const val LIT_START = 0.5f
    const val LIT_END = 0.8f

    /** Deterministic per-dot random in [0, 1): a 32-bit mix of the dot index and a salt. */
    fun hash(index: Int, salt: Int): Float {
        var h = index * -0x61c88647 + salt * 0x27d4eb2f
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return (h ushr 8) / 16777216f
    }

    /** The dot's cycle length, biased toward the long end like the reference's 0.14 Hz majority. */
    fun period(index: Int): Float {
        val h = hash(index, 1)
        return MAX_PERIOD_SECONDS - (MAX_PERIOD_SECONDS - MIN_PERIOD_SECONDS) * h * h
    }

    /** The dot's cycle offset in turns [0, 1), independent of its neighbours. */
    fun phase(index: Int): Float = hash(index, 2)

    /** Lit level (0 off .. 1 fully on) of a dot with the given [period] and [phase] at [timeSeconds]. */
    fun level(period: Float, phase: Float, timeSeconds: Float): Float {
        val u = 0.5f + 0.5f * cos((2.0 * PI * (timeSeconds / period + phase)).toFloat())
        return smoothstep(LIT_START, LIT_END, u)
    }

    fun level(index: Int, timeSeconds: Float): Float = level(period(index), phase(index), timeSeconds)

    /**
     * How much of the twinkle a dot gets from its position relative to the pill, given the
     * capsule's signed distance (negative inside). Dots deeper than [innerBandPx] inside the
     * pill are gone (its opaque interior); across the blurred edge they ramp up to full strength
     * at the boundary; outside they ease from full strength down to [farLevel] over [nearBandPx],
     * so the band is liveliest right around the pill as in the reference.
     */
    fun pillFactor(signedDistance: Float, innerBandPx: Float, nearBandPx: Float, farLevel: Float): Float {
        if (!signedDistance.isFinite()) return farLevel
        if (signedDistance <= -innerBandPx) return 0f
        if (signedDistance < 0f) return if (innerBandPx <= 0f) 1f else smoothstep(-innerBandPx, 0f, signedDistance)
        if (nearBandPx <= 0f) return farLevel
        return 1f - (1f - farLevel) * smoothstep(0f, nearBandPx, signedDistance)
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
