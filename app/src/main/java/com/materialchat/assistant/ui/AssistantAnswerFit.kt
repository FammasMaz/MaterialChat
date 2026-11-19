package com.materialchat.assistant.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the answer surface shares its height between the exchange, a notice and the toolbar when
 * the host squeezes it (the IME up on a short window). Pure arithmetic on the same tokens the
 * composables draw, so the JVM test can walk the squeeze states without a device.
 *
 * W5 (state walk round 3): the W4 layout gave the notice and the answer list one weight each.
 * Column weights never redistribute what a `fill = false` child leaves unused, so on a 640dp
 * window with the keyboard up the answer surface (260dp, content 228dp after its insets) left
 * 148dp for the two of them, 74dp each, and 74dp is exactly the notice's own chrome
 * ([noticeChrome] with an action): the message had no height at any font scale. Now the notice
 * is measured first at its natural height up to [noticeCap], and the answer list, which scrolls,
 * takes the rest; the exchange keeps at least [AnswerFloor] so it never vanishes.
 */
internal object AssistantAnswerFit {
    /** The answer list keeps at least one touch target of the exchange, scrollable, under a notice. */
    val AnswerFloor: Dp = AssistantSizes.Target

    /** The toolbar strip: 48dp targets inside the 4dp tight inset top and bottom. */
    val ToolbarHeight: Dp = AssistantSizes.Target + AssistantSpacing.Tight * 2

    /** The answer surface's own insets around its content column (20dp top, 12dp bottom). */
    val SurfaceInsets: Dp = AssistantSpacing.SurfaceInset + AssistantSpacing.Inset

    /** The notice surface's non-message height: its insets, and the gap plus the 40dp chip when it has an action. */
    fun noticeChrome(hasAction: Boolean): Dp =
        AssistantSpacing.Inset * 2 + (if (hasAction) AssistantSpacing.Row + AssistantSizes.ChipHeight else 0.dp)

    /**
     * The most height the notice may take inside the answer surface's content column of
     * [contentHeight]: what is left after the toolbar, the Using-screen marker ([markerHeight],
     * zero when absent), the column gaps between the children present and the exchange's floor.
     */
    fun noticeCap(contentHeight: Dp, markerHeight: Dp): Dp {
        val children = 3 + (if (markerHeight > 0.dp) 1 else 0)
        val gaps = AssistantSpacing.Inset * (children - 1)
        return (contentHeight - ToolbarHeight - markerHeight - gaps - AnswerFloor).coerceAtLeast(0.dp)
    }

    /** The height the notice's message gets inside a notice of [noticeHeight]. */
    fun noticeMessageHeight(noticeHeight: Dp, hasAction: Boolean): Dp =
        (noticeHeight - noticeChrome(hasAction)).coerceAtLeast(0.dp)

    /** What the W4 1:1 weights gave the notice: half of what the toolbar, the marker and the gaps left. */
    fun weightedShare(contentHeight: Dp, markerHeight: Dp): Dp {
        val children = 3 + (if (markerHeight > 0.dp) 1 else 0)
        val gaps = AssistantSpacing.Inset * (children - 1)
        return ((contentHeight - ToolbarHeight - markerHeight - gaps) / 2).coerceAtLeast(0.dp)
    }
}
