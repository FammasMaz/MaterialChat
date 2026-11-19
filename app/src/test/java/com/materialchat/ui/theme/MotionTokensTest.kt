package com.materialchat.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared motion tokens to the material3 1.5.0-alpha15 ExpressiveMotionTokens values
 * (fast spatial 0.6 / 800) and the decorative periods consumers are told to take from here.
 */
class MotionTokensTest {

    @Test fun pressTokensAreTheM3eFastSpatialSpring() {
        for (spec in listOf(ExpressiveMotion.Press.Shape, ExpressiveMotion.Press.Progress, ExpressiveMotion.Press.Scale)) {
            assertEquals(0.6f, spec.dampingRatio, 1e-6f)
            assertEquals(800f, spec.stiffness, 1e-6f)
        }
        assertEquals(ExpressiveMotion.Spatial.fast<Float>(), ExpressiveMotion.Spatial.pressMorph<Float>())
    }

    @Test fun decorativeSpinIsOneLinearTurnPerPeriodRestarting() {
        val gear = ExpressiveMotion.Spatial.decorativeSpin()
        val gearTween = gear.animation as TweenSpec<Float>
        assertEquals(16_000, gearTween.durationMillis)
        assertEquals(LinearEasing, gearTween.easing)
        assertEquals(RepeatMode.Restart, gear.repeatMode)

        val backdrop = ExpressiveMotion.Spatial.decorativeSpin(MaterialChatMotion.Durations.BackdropSpin)
        assertEquals(48_000, (backdrop.animation as TweenSpec<Float>).durationMillis)
        assertEquals(RepeatMode.Restart, backdrop.repeatMode)
    }

    @Test fun bobMatchesWhatTheScrollFabAlreadyDraws() {
        // ScrollToBottomFab: tween(ExtraLong + Quick), RepeatMode.Reverse.
        assertEquals(800, MaterialChatMotion.Durations.Bob)
        assertEquals(
            MaterialChatMotion.Durations.ExtraLong + MaterialChatMotion.Durations.Quick,
            MaterialChatMotion.Durations.Bob
        )
        val bob = ExpressiveMotion.Spatial.bob()
        assertEquals(800, (bob.animation as TweenSpec<Float>).durationMillis)
        assertEquals(RepeatMode.Reverse, bob.repeatMode)
    }

    @Test fun indicatorCyclesMatchTheLoadingIndicatorAndTheM3CircularSpec() {
        // ExpressiveLoadingIndicator's private IndicatorCycle, lifted here value for value.
        val i = MaterialChatMotion.Durations.Indicator
        assertEquals(4000, i.MorphRotation)
        assertEquals(3000, i.MorphShape)
        assertEquals(1332, i.ArcRotation)
        assertEquals(666, i.ArcSweep)
        assertEquals(i.ArcRotation / 2, i.ArcSweep)
        assertEquals(1500, i.LinearPass)
        assertEquals(750, i.LinearWidth)
        assertEquals(i.LinearPass / 2, i.LinearWidth)
    }

    @Test fun statsPillHoldIsWhatTheMessageBubbleDelays() {
        // MessageBubble: delay(3_500) before the auto-expanded stats pill collapses.
        assertEquals(3_500, MaterialChatMotion.Durations.StatsPillHold)
    }

    @Test fun dataRevealIsTheSlowInsightsCountUp() {
        // StatCard / ModelUsageChart: spring(dampingRatio = 0.8f, stiffness = 50f).
        val spec = ExpressiveMotion.Spatial.dataReveal<Float>()
        assertEquals(0.8f, spec.dampingRatio, 1e-6f)
        assertEquals(50f, spec.stiffness, 1e-6f)
        // Deliberately far slower than the M3E slow spatial spring so a figure is seen arriving.
        assertTrue(spec.stiffness * 4 <= ExpressiveMotion.Spatial.slow<Float>().stiffness)
        assertEquals(ExpressiveMotion.Spatial.dataReveal<Int>(), ExpressiveMotion.Spatial.dataReveal<Int>())
    }
}
