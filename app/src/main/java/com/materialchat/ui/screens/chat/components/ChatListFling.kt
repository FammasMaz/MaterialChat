package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Velocity below which a fling of the message list is considered settled. 24dp/s is well
 * under one pixel per frame at any density: the list is visibly still, so cutting the decay
 * there is not perceptible.
 */
internal val CHAT_LIST_FLING_SETTLE_VELOCITY_DP_PER_SEC = 24.dp

/** True when a decaying fling has slowed enough to be ended and the scroll session closed. */
internal fun flingHasSettled(velocityPxPerSec: Float, settleVelocityPxPerSec: Float): Boolean =
    abs(velocityPxPerSec) < settleVelocityPxPerSec

/**
 * A fling for the message list that settles instead of trailing off.
 *
 * The default spline decay keeps the scroll session open for its whole computed duration,
 * including a long sub-pixel tail where the list looks still. While that session is open the
 * list counts as `isScrollInProgress`: it consumes every pointer-down to "stop the fling" and
 * cancels every horizontal drag inside a Markdown table (the inner scrollable sees its moves
 * consumed in the Final pass). Closing the session once the velocity drops below
 * [settleVelocityPxPerSec] removes that dead window. Reaching the end of the list still
 * returns the remaining velocity so the stretch overscroll behaves as before.
 */
internal class SettlingFlingBehavior(
    private val decay: DecayAnimationSpec<Float>,
    private val settleVelocityPxPerSec: Float
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (flingHasSettled(initialVelocity, settleVelocityPxPerSec)) return 0f
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = velocity
            when {
                // Hit the end of the list: hand the rest to overscroll, like the default.
                abs(delta - consumed) > 0.5f -> cancelAnimation()
                flingHasSettled(velocity, settleVelocityPxPerSec) -> {
                    velocityLeft = 0f
                    cancelAnimation()
                }
            }
        }
        return velocityLeft
    }
}

@Composable
internal fun rememberChatListFlingBehavior(): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val density = LocalDensity.current
    return remember(decay, density) {
        SettlingFlingBehavior(
            decay = decay,
            settleVelocityPxPerSec = with(density) { CHAT_LIST_FLING_SETTLE_VELOCITY_DP_PER_SEC.toPx() }
        )
    }
}
