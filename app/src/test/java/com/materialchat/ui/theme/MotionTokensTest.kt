package com.materialchat.ui.theme

import androidx.compose.animation.core.EaseInOutCubic
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
        // W7 part 5: pressMorph() IS fast() (one spec, two names: the press name documents intent).
        assertEquals(ExpressiveMotion.Spatial.fast<Float>(), ExpressiveMotion.Spatial.pressMorph<Float>())
        assertEquals(ExpressiveMotion.Spatial.fast<Float>(), ExpressiveMotion.Press.Progress)
        assertEquals(ExpressiveMotion.Spatial.fast<Float>(), ExpressiveMotion.Press.Scale)
        assertEquals(ExpressiveMotion.Spatial.fast<androidx.compose.ui.unit.Dp>(), ExpressiveMotion.Press.Shape)
    }

    @Test fun disclosureTokensAreTheExpandAndCollapseSprings() {
        // Received from AssistantMotionTest (W7 part 8): the theme pins its own token values.
        val expand = ExpressiveMotion.Disclosure.Expand
        assertEquals(0.75f, expand.dampingRatio, 1e-6f)
        assertEquals(420f, expand.stiffness, 1e-6f)
        val shrink = ExpressiveMotion.Disclosure.Shrink
        assertEquals(1.0f, shrink.dampingRatio, 1e-6f)
        assertEquals(420f, shrink.stiffness, 1e-6f)
        assertEquals(ExpressiveMotion.Spatial.expand<androidx.compose.ui.unit.IntSize>(), expand)
        assertEquals(ExpressiveMotion.Spatial.collapse<androidx.compose.ui.unit.IntSize>(), shrink)
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

    @Test fun headerPulseIsAOneSecondFourLinearRestartLoop() {
        // Token value only (W7 part 8): which surface draws it is that surface's test.
        assertEquals(1_400, MaterialChatMotion.Durations.HeaderPulse)
        val pulse = ExpressiveMotion.Spatial.decorativeSpin(MaterialChatMotion.Durations.HeaderPulse)
        val tween = pulse.animation as TweenSpec<Float>
        assertEquals(1_400, tween.durationMillis)
        assertEquals(LinearEasing, tween.easing)
        assertEquals(RepeatMode.Restart, pulse.repeatMode)
    }

    @Test fun pulseIsTheFusionCardBreathHalfPeriod() {
        // FusionProgressView: bob(Durations.Long) today, a 1 s reversing breath -> bob(Pulse).
        assertEquals(500, MaterialChatMotion.Durations.Pulse)
        assertEquals(MaterialChatMotion.Durations.Long, MaterialChatMotion.Durations.Pulse)
        val breath = ExpressiveMotion.Spatial.bob(MaterialChatMotion.Durations.Pulse)
        assertEquals(500, (breath.animation as TweenSpec<Float>).durationMillis)
        assertEquals(1_000, 2 * MaterialChatMotion.Durations.Pulse)
        assertEquals(RepeatMode.Reverse, breath.repeatMode)
    }

    @Test fun fusionOrbitIsOneLinearTurnPerThreeSeconds() {
        // FusionAnimation: decorativeSpin(Indicator.MorphShape) for want of a name -> FusionOrbit.
        assertEquals(3_000, MaterialChatMotion.Durations.FusionOrbit)
        assertEquals(MaterialChatMotion.Durations.Indicator.MorphShape, MaterialChatMotion.Durations.FusionOrbit)
        val orbit = ExpressiveMotion.Spatial.decorativeSpin(MaterialChatMotion.Durations.FusionOrbit)
        val tween = orbit.animation as TweenSpec<Float>
        assertEquals(3_000, tween.durationMillis)
        assertEquals(LinearEasing, tween.easing)
        assertEquals(RepeatMode.Restart, orbit.repeatMode)
    }

    @Test fun indicatorCyclesMatchTheLoadingIndicatorAndTheChatStreamingIndicators() {
        // M3ExpressiveLoadingIndicator's cycle, lifted here value for value, plus the three chat
        // StreamingIndicator periods (W7 part 5: 3000 rotation, 2400 morph, 1400 shimmer).
        val i = MaterialChatMotion.Durations.Indicator
        assertEquals(4000, i.MorphRotation)
        assertEquals(3000, i.MorphShape)
        assertEquals(3000, i.StreamingSpin)
        assertEquals(2400, i.StreamingMorph)
        assertEquals(1400, i.StreamingShimmer)
        // The shimmer shares the masthead pulse period but is its own token.
        assertEquals(MaterialChatMotion.Durations.HeaderPulse, i.StreamingShimmer)
    }

    @Test fun tweenHelpersCarryTheDurationAndTheNamedEasing() {
        // W7 part 5: MindMapCanvas / StreamingIndicator / M3ExpressiveLoadingIndicator wrap these
        // in infiniteRepeatable instead of calling tween( directly.
        val linear = MaterialChatMotion.Tweens.linear<Float>(MaterialChatMotion.Durations.StreamingIndicator)
        assertEquals(1_200, linear.durationMillis)
        assertEquals(LinearEasing, linear.easing)
        assertEquals(0, linear.delay)
        val eased = MaterialChatMotion.Tweens.easeInOut<Float>(MaterialChatMotion.Durations.Indicator.StreamingMorph)
        assertEquals(2_400, eased.durationMillis)
        assertEquals(EaseInOutCubic, eased.easing)
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

    @Test fun dataRevealIsTheOneInsightsSpecDerivedFromTheM3eSpatialTokens() {
        // W4 ruling: one reveal spec for every insights chart. Damping is the M3E default
        // spatial damping (ExpressiveMotionTokens.defaultSpatialSpec, 0.8) and stiffness is a
        // quarter of the M3E slow spatial token (200 / 4 = 50). The retired per-chart variants
        // (ActivityHeatmap 0.9/40, ModelComparisonCard and ResponseTimeChart 0.7/60) must not
        // creep back in as the token's values.
        val spec = ExpressiveMotion.Spatial.dataReveal<Float>()
        assertEquals(ExpressiveMotion.Spatial.default<Float>().dampingRatio, spec.dampingRatio, 1e-6f)
        assertEquals(ExpressiveMotion.Spatial.slow<Float>().stiffness / 4f, spec.stiffness, 1e-6f)
        assertTrue(spec.dampingRatio != 0.9f && spec.dampingRatio != 0.7f)
        assertTrue(spec.stiffness != 40f && spec.stiffness != 60f)
        // Never overshoots by a visible digit: under-damped but close to critical.
        assertTrue(spec.dampingRatio >= 0.75f && spec.dampingRatio < 1f)
    }
}
