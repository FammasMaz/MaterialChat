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
 * [AssistantExitMotion]'s window. W7: the theme's `MotionTokensTest` pins the token values
 * themselves (press, disclosure); this test only asserts which token each assistant motion
 * resolves to and the settle times the overlay's comments quote.
 */
class AssistantMotionTest {

    /** The animator window the session waits before hiding the window (AssistantExitMotion.BASE_MILLIS). */
    private val exitWindowMillis = AssistantExitMotion.durationMillis(1f)

    @Test fun theExitWindowIsTheDocumented320Ms() {
        assertEquals(320L, exitWindowMillis)
        assertEquals(0L, AssistantExitMotion.durationMillis(0f))
    }

    @Test fun entrancesArriveOnTheSharedContainerSpring() {
        // W7: the answer surface rising out of the pill and the thinking surface growing from
        // its corner arrive on the one container spatial token every container transform in
        // the app uses, the same spring the sheet itself enters on.
        assertEquals(ExpressiveMotion.Spatial.container<Float>(), AssistantMotion.surfaceReveal<Float>())
        assertEquals(AssistantMotion.container<Float>(), AssistantMotion.surfaceReveal<Float>())
        // Arrival may overshoot (M3: overshoot on arrival, never on departure).
        assertTrue(AssistantMotion.surfaceReveal<Float>().dampingRatio < 1f)
    }

    @Test fun theEntranceSpringCouldNotServeAsAnExit() {
        // Why the answer surface leaves on exit() and not on the spring it arrived on: the
        // container token's under-damped envelope is still above 1 % of its travel when the
        // session's 320 ms window closes, so a surface dismissed while the window is leaving
        // would be cut off mid-motion.
        val arrive = AssistantMotion.surfaceReveal<Float>()
        val envelope = underdampedEnvelopeMillis(arrive.dampingRatio, arrive.stiffness, remaining = 0.01)
        assertTrue("container 99 % envelope at $envelope ms", envelope > exitWindowMillis)
    }

    @Test fun stateSwitchesDrawTheFastSpatialToken() {
        // The glyph and state-line switches alias the fast spatial token. The glyph container,
        // the `+` -> `x` affordance and the notice chip draw Press.Shape / Press.Progress
        // directly; whether those stay value-identical to the fast token is the theme's call
        // (MotionTokensTest), not pinned here (W7: the W6 audit's cross-owner pin, F15).
        assertEquals(ExpressiveMotion.Spatial.fast<Float>(), AssistantMotion.stateSwitch<Float>())
    }

    @Test fun disclosuresFoldOnTheSheetsOwnExitCurve() {
        // The opened pill's regions fold on the shared Shrink disclosure, which must be the same
        // spec the whole sheet leaves on, so a region folding while the window leaves is on one
        // curve. Critically damped: departure never overshoots back into view.
        val shrink = ExpressiveMotion.Disclosure.Shrink
        val exit = AssistantMotion.exit<Float>()
        assertEquals(ExpressiveMotion.Spatial.collapse<Float>(), exit)
        assertEquals(exit.dampingRatio, shrink.dampingRatio, 1e-6f)
        assertEquals(exit.stiffness, shrink.stiffness, 1e-6f)
        assertEquals(1.0f, exit.dampingRatio, 1e-6f)
        // Expand is the arrival of the same disclosure: same stiffness, a little overshoot allowed.
        val expand = ExpressiveMotion.Disclosure.Expand
        assertEquals(exit.stiffness, expand.stiffness, 1e-6f)
        assertTrue(expand.dampingRatio < 1f)
    }

    @Test fun everyExitSettlesInsideTheWindow() {
        // The sheet's alpha (and, through ModulateAlpha, everything inside it) leaves on the
        // effects token: 99 % gone well inside the window.
        val alphaSettle = criticallyDampedSettleMillis(AssistantMotion.effect<Float>().stiffness, remaining = 0.01)
        assertTrue("alpha 99 % at $alphaSettle ms", alphaSettle < exitWindowMillis)
        assertTrue("alpha 99 % at $alphaSettle ms", alphaSettle in 150L..180L)
        // The drop/shrink, the answer surface's slide/scale-out and the folding regions: 90 % of
        // the travel inside the window, and the last 1 % lands within a few ms of it, after the
        // alpha has already reached 99 %.
        val exit = AssistantMotion.exit<Float>()
        assertEquals(1.0f, exit.dampingRatio, 1e-6f)
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

    /** Decay envelope of an under-damped unit spring: e^(-zeta omega t) reaches [remaining]. */
    private fun underdampedEnvelopeMillis(dampingRatio: Float, stiffness: Float, remaining: Double): Long =
        (ln(1 / remaining) / (dampingRatio * sqrt(stiffness.toDouble())) * 1000).toLong()
}
