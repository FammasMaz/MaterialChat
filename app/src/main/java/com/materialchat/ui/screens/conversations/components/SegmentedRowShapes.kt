package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Corner tokens for the M3 Expressive segmented conversation list.
 *
 * Rows sit in a tight stack separated by [Gap]; the stack's outer corners use
 * [OuterRadius] and the seams between rows use [InnerRadius], the same geometry
 * `ListItemDefaults.segmentedShapes` produces. A pressed row morphs every corner
 * to [OuterRadius] so it visibly lifts out of the group.
 */
object SegmentedRowShapes {
    /** Outer corners of the stack (M3 largeIncreased). */
    val OuterRadius: Dp = 20.dp

    /** Corners on the seam between neighbouring rows. */
    val InnerRadius: Dp = 4.dp

    /** Gap between rows, matching the segmented list-item gap. */
    val Gap: Dp = 2.dp

    /** Corner spec a row morphs to while pressed or swiped. */
    val Pressed: SwipeCornerSpec = SwipeCornerSpec(OuterRadius, OuterRadius, OuterRadius, OuterRadius)

    /**
     * Resting corners for the row at [index] in a stack of [count] rows.
     */
    fun corners(index: Int, count: Int): SwipeCornerSpec {
        val top = if (index == 0) OuterRadius else InnerRadius
        val bottom = if (index == count - 1) OuterRadius else InnerRadius
        return SwipeCornerSpec(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }
}

/**
 * Shape that springs between [resting] and [pressed] corners with the theme's
 * shape-morph physics. Overshoot is clamped so a radius never goes negative.
 *
 * @param resting Corners while the row is idle.
 * @param pressed Corners while the row is pressed.
 * @param isPressed Current press state from the row's interaction source.
 */
@Composable
fun pressMorphShape(
    resting: SwipeCornerSpec,
    pressed: SwipeCornerSpec,
    isPressed: Boolean
): Shape {
    val progress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "rowPressMorph"
    )
    val fraction = progress.coerceIn(0f, 1f)
    return RoundedCornerShape(
        topStart = lerp(resting.topStart, pressed.topStart, fraction),
        topEnd = lerp(resting.topEnd, pressed.topEnd, fraction),
        bottomStart = lerp(resting.bottomStart, pressed.bottomStart, fraction),
        bottomEnd = lerp(resting.bottomEnd, pressed.bottomEnd, fraction)
    )
}
