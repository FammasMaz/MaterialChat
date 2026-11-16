package com.materialchat.ui.screens.conversations.components

import androidx.compose.ui.unit.Dp

/**
 * Where a swipe-reveal action sits inside its reveal row.
 */
enum class SwipeActionSlot {
    /** The only action in the row: every corner uses the outer radius. */
    Only,

    /** First of two actions: outer corners on the start edge, inner on the end edge. */
    Leading,

    /** Second of two actions: inner corners on the start edge, outer on the end edge. */
    Trailing
}

/**
 * Corner spec for a swipe action pill in [slot].
 *
 * Pure arithmetic so the per-position geometry can be asserted on the JVM.
 *
 * @param slot Position of the action within the reveal row.
 * @param outer Radius on edges that face away from a neighbouring action.
 * @param inner Radius on the edge shared with a neighbouring action.
 */
fun swipeActionCorners(slot: SwipeActionSlot, outer: Dp, inner: Dp): SwipeCornerSpec = when (slot) {
    SwipeActionSlot.Only -> SwipeCornerSpec(outer, outer, outer, outer)
    SwipeActionSlot.Leading -> SwipeCornerSpec(topStart = outer, topEnd = inner, bottomStart = outer, bottomEnd = inner)
    SwipeActionSlot.Trailing -> SwipeCornerSpec(topStart = inner, topEnd = outer, bottomStart = inner, bottomEnd = outer)
}
