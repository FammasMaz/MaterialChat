package com.materialchat.assistant.ui

import com.materialchat.ui.theme.ExpressiveMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The assistant's motion is the shared token set, and every exit settles inside
 * [AssistantExitMotion]'s window. W5: the small shape morphs draw `Press.Shape` / `Press.Progress`
 * and the opened pill's height disclosures draw `Disclosure.Expand` / `Disclosure.Shrink`; this
 * pins the springs those tokens resolve to and the settle times the overlay's comments quote.
 */
class AssistantMotionTest {

    /** The animator window the session waits before hiding the window (AssistantExitMotion.BASE_MILLIS). */
    private val exitWindowMillis = AssistantExitMotion.durationMillis(1f)

    @Test fun theExitWindowIsTheDocumented320Ms() {
        assertEquals(320L, exitWindowMillis)
        assertEquals(0L, AssistantExitMotion.durationMillis(0f))
    }

    @Test fun pressMorphsAreTheSharedPressToken() {
        // What the glyph container, the `+` -> `x` affordance and the notice chip now draw.
        for (spec in listOf(ExpressiveMotion.Press.Shape, ExpressiveMotion.Press.Progress)) {
            assertEquals(0.6f, spec.dampingRatio, 1e-6f)
            assertEquals(800f, spec.stiffness, 1e-6f)
        }
        // Value-identical to the fast spatial token the assistant used to alias as morph(), so
        // the feel of the three morphs is unchanged by the token move.
        assertEquals(ExpressiveMotion.Spatial.fast<Float>(), ExpressiveMotion.Press.Progress)
    }

    @Test fun disclosuresFoldOnTheSheetsOwnExitCurve() {
        val shrink = ExpressiveMotion.Disclosure.Shrink
        val exit = AssistantMotion.exit<Float>()
        assertEquals(1.0f, shrink.dampingRatio, 1e-6f)
        assertEquals(420f, shrink.stiffness, 1e-6f)
        assertEquals(exit.dampingRatio, shrink.dampingRatio, 1e-6f)
        assertEquals(exit.stiffness, shrink.stiffness, 1e-6f)
        // Expand may overshoot a little on arrival (M3: arrival, not departure).
        val expand = ExpressiveMotion.Disclosure.Expand
        assertEquals(0.75f, expand.dampingRatio, 1e-6f)
        assertEquals(420f, expand.stiffness, 1e-6f)
    }

    @Test fun everyExitSettlesInsideTheWindow() {
        // The sheet's alpha (and, through ModulateAlpha, everything inside it) leaves on the
        // effects token: 99 % gone well inside the window.
        val alphaSettle = criticallyDampedSettleMillis(AssistantMotion.effect<Float>().stiffness, remaining = 0.01)
        assertTrue("alpha 99 % at $alphaSettle ms", alphaSettle < exitWindowMillis)
        assertTrue("alpha 99 % at $alphaSettle ms", alphaSettle in 150L..180L)
        // The drop/shrink and the folding regions: 90 % of the travel inside the window, and the
        // last 1 % lands within a few ms of it, after the alpha has already reached 99 %.
        val exit = AssistantMotion.exit<Float>()
        val ninety = criticallyDampedSettleMillis(exit.stiffness, remaining = 0.10)
        val ninetyNine = criticallyDampedSettleMillis(exit.stiffness, remaining = 0.01)
        assertTrue("exit 90 % at $ninety ms", ninety < exitWindowMillis)
        assertTrue("exit 90 % at $ninety ms", ninety in 180L..200L)
        assertTrue("exit 99 % at $ninetyNine ms", ninetyNine in 315L..335L)
        assertTrue(alphaSettle < ninety)
    }

    /**
     * Time for a critically damped unit spring (mass 1, omega = sqrt(stiffness)) released from
     * rest to come within [remaining] of its target: x(t)/x0 = (1 + wt) e^(-wt).
     */
    private fun criticallyDampedSettleMillis(stiffness: Float, remaining: Double): Long {
        val omega = sqrt(stiffness.toDouble())
        // Solve (1 + u) e^-u = remaining by bisection on u = omega * t.
        var lo = 0.0
        var hi = 40.0
        repeat(60) {
            val mid = (lo + hi) / 2
            if ((1 + mid) * exp(-mid) > remaining) lo = mid else hi = mid
        }
        return ((lo + hi) / 2 / omega * 1000).toLong()
    }

    @Suppress("unused")
    private fun underdampedEnvelopeMillis(dampingRatio: Float, stiffness: Float, remaining: Double): Long =
        (ln(1 / remaining) / (dampingRatio * sqrt(stiffness.toDouble())) * 1000).toLong()
}
