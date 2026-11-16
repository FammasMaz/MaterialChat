package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Corner radii of one item in a horizontally connected M3 Expressive group: the outer edges of
 * the group keep the large radius, the joins between neighbours the small one.
 */
internal data class PositionCorners(val start: Dp, val end: Dp)

internal fun positionCorners(
    index: Int,
    count: Int,
    outer: Dp = 20.dp,
    inner: Dp = 8.dp
): PositionCorners {
    if (count <= 1) return PositionCorners(outer, outer)
    return PositionCorners(
        start = if (index == 0) outer else inner,
        end = if (index == count - 1) outer else inner
    )
}

internal fun PositionCorners.toShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = start,
    bottomStart = start,
    topEnd = end,
    bottomEnd = end
)

/**
 * Per-position shape that tightens to [pressed] on both edges while the item is pressed, on
 * the spatial shape-morph spring.
 */
@Composable
internal fun rememberPositionMorphShape(
    index: Int,
    count: Int,
    isPressed: Boolean,
    outer: Dp = 20.dp,
    inner: Dp = 8.dp,
    pressed: Dp = 12.dp
): RoundedCornerShape {
    val corners = positionCorners(index, count, outer, inner)
    val start by animateDpAsState(
        targetValue = if (isPressed) pressed else corners.start,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "positionMorphStart"
    )
    val end by animateDpAsState(
        targetValue = if (isPressed) pressed else corners.end,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "positionMorphEnd"
    )
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

/** Press compression on the spatial scale spring; no overshoot past rest on release matters little at 0.96. */
@Composable
internal fun Modifier.expressivePressScale(isPressed: Boolean, pressedScale: Float = 0.96f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "expressivePressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
