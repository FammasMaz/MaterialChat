package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometry of the streaming tail glyph (`SmoothStreamingText.StreamingTailEffect`), pinned in
 * `ChatThinkingGeometryTest`.
 *
 * The glyph is `MaterialShapes.SoftBurst`, the first polygon of the M3 loading indicator's
 * indeterminate set, drawn at [StartAngle] 0 as that set draws it (the 18 degree turn the port
 * carried is the indicator's *determinate* orientation). The inset is the same on every side so
 * the glyph is centred in its own slot instead of being nudged 2dp toward the end and bottom.
 */
internal object StreamingTailGeometry {
    val Size: Dp = 12.dp

    /** Inset on every side of the glyph; equal on all four sides by contract. */
    val Inset: Dp = 2.dp

    /** Polygon orientation: unrotated, as the M3 indeterminate indicator draws SoftBurst. */
    const val StartAngle: Int = 0
}

/**
 * Geometry of the thinking header strip (`MessageBubble.ThinkingSection`), pinned in
 * `ChatThinkingGeometryTest`: the chevron and the title share one strip with the same inset on
 * the leading and trailing edge, and the chevron is centred vertically in the strip.
 */
internal object ThinkingHeaderGeometry {
    /** Hit target height of the strip. */
    val MinHeight: Dp = 40.dp

    /** Inset on the leading and trailing edge of the strip; equal by contract. */
    val HorizontalInset: Dp = 8.dp

    /** Inset on the top and bottom of the strip; equal by contract. */
    val VerticalInset: Dp = 6.dp

    /** The disclosure chevron (`Icons.Outlined.KeyboardArrowDown`). */
    val Chevron: Dp = 18.dp

    /** Gap between the chevron and the title column. */
    val ChevronGap: Dp = 6.dp
}
