package com.materialchat.ui.screens.mindmap.components

/**
 * Pure layout math for the native-canvas labels drawn by [MindMapCanvas].
 *
 * The canvas draws its text with `android.graphics.Paint`, so the usual Compose text
 * measurement is not available. Everything that decides *what* fits is kept here, free of
 * Android types, so the JVM suite can audit it at every font scale.
 */
internal object MindMapLabelLayout {
    /** Title label size in sp (scaled by the user's font scale at draw time). */
    const val TITLE_SP = 13f

    /** Message-count badge size in sp. */
    const val BADGE_SP = 10f

    /** Node height in dp; mirrors `NODE_HEIGHT_DP` in [MindMapCanvas]. */
    const val NODE_HEIGHT_DP = 60f

    /** Title baseline sits this many dp above the node's vertical centre. */
    const val TITLE_BASELINE_LIFT_DP = 2f

    /** Badge baseline sits this many dp above the node's bottom edge. */
    const val BADGE_PAD_BOTTOM_DP = 8f

    /** Roboto ascent / descent as fractions of the text size (ascent 0.927, descent 0.244). */
    const val ASCENT_FRACTION = 0.93f
    const val DESCENT_FRACTION = 0.25f

    const val ELLIPSIS = "\u2026"

    /**
     * Trims [text] so that `measure(result) <= maxWidth`, appending [ELLIPSIS] when anything
     * was cut. Trims by code point so an emoji prefix is never split into a lone surrogate.
     */
    fun fitToWidth(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (text.isEmpty() || measure(text) <= maxWidth) return text
        val ellipsisWidth = measure(ELLIPSIS)
        var end = text.length
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1)
            val candidate = text.substring(0, end).trimEnd()
            if (candidate.isEmpty()) break
            if (measure(candidate) + ellipsisWidth <= maxWidth) return candidate + ELLIPSIS
        }
        return ELLIPSIS
    }

    /** Bottom of the title's descent band, in dp from the node top, at [fontScale]. */
    fun titleBottomDp(fontScale: Float): Float =
        NODE_HEIGHT_DP / 2f - TITLE_BASELINE_LIFT_DP + TITLE_SP * fontScale * DESCENT_FRACTION

    /** Top of the title's ascent band, in dp from the node top, at [fontScale]. */
    fun titleTopDp(fontScale: Float): Float =
        NODE_HEIGHT_DP / 2f - TITLE_BASELINE_LIFT_DP - TITLE_SP * fontScale * ASCENT_FRACTION

    /** Top of the badge's ascent band, in dp from the node top, at [fontScale]. */
    fun badgeTopDp(fontScale: Float): Float =
        NODE_HEIGHT_DP - BADGE_PAD_BOTTOM_DP - BADGE_SP * fontScale * ASCENT_FRACTION

    /**
     * Vertical clearance in dp between the title's descent and the badge's ascent at
     * [fontScale]. Negative means the two labels overlap.
     */
    fun titleBadgeGapDp(fontScale: Float): Float = badgeTopDp(fontScale) - titleBottomDp(fontScale)
}
