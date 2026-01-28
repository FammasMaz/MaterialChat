package com.materialchat.ui.screens.conversations.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive branch connector - draws a smooth curved line connecting
 * a branch conversation to its parent in the hierarchy.
 *
 * Uses `outlineVariant` for subtle visual connection per M3 color guidelines.
 * The curve uses a smooth bezier path for an expressive, organic feel.
 *
 * @param isFirst Whether this is the first branch in the list (draws from top)
 * @param isLast Whether this is the last branch in the list (no continuation)
 * @param isOnly Whether this is the only branch (draws complete curve)
 * @param color The connector color (defaults to outlineVariant)
 * @param strokeWidth Width of the connector line
 * @param modifier Modifier for the connector container
 */
@Composable
fun BranchConnector(
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isOnly: Boolean = false,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(32.dp)
            .fillMaxHeight()
    ) {
        val strokePx = strokeWidth.toPx()
        val width = size.width
        val height = size.height

        // Anchor point - where the vertical line is positioned
        val anchorX = width * 0.35f

        // Horizontal endpoint - where the curve meets the branch item
        val endX = width - 4.dp.toPx()

        // Vertical center for the curve's horizontal section
        val centerY = height / 2

        // Curve control point offset for smooth bezier
        val curveRadius = 12.dp.toPx()

        val path = Path().apply {
            if (isOnly) {
                // Single branch - draw complete curved connector from top
                moveTo(anchorX, 0f)
                lineTo(anchorX, centerY - curveRadius)
                // Smooth curve to horizontal
                quadraticBezierTo(
                    anchorX, centerY,
                    anchorX + curveRadius, centerY
                )
                lineTo(endX, centerY)
            } else if (isFirst) {
                // First branch - start from top, curve to this item
                moveTo(anchorX, 0f)
                lineTo(anchorX, centerY - curveRadius)
                quadraticBezierTo(
                    anchorX, centerY,
                    anchorX + curveRadius, centerY
                )
                lineTo(endX, centerY)
                // Continue vertical line down for next branches
                moveTo(anchorX, centerY)
                lineTo(anchorX, height)
            } else if (isLast) {
                // Last branch - come from top, curve to this item, stop
                moveTo(anchorX, 0f)
                lineTo(anchorX, centerY - curveRadius)
                quadraticBezierTo(
                    anchorX, centerY,
                    anchorX + curveRadius, centerY
                )
                lineTo(endX, centerY)
            } else {
                // Middle branch - vertical line through, with horizontal branch
                moveTo(anchorX, 0f)
                lineTo(anchorX, height)
                // Branch out to this item
                moveTo(anchorX, centerY - curveRadius)
                quadraticBezierTo(
                    anchorX, centerY,
                    anchorX + curveRadius, centerY
                )
                lineTo(endX, centerY)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * A decorative dot at the end of a branch connector, providing visual termination.
 * Uses M3 Expressive styling with secondary color for emphasis.
 *
 * @param color The dot color
 * @param size Size of the dot
 * @param modifier Modifier for the dot
 */
@Composable
fun BranchDot(
    color: Color = MaterialTheme.colorScheme.secondary,
    size: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(size)) {
        drawCircle(
            color = color,
            radius = size.toPx() / 2,
            center = Offset(size.toPx() / 2, this.size.height / 2)
        )
    }
}
