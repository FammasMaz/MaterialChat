package com.materialchat.ui.components

import kotlin.math.abs

/**
 * Whether an embedded math WebView should take a horizontal drag away from its Compose
 * ancestors (a Markdown table's horizontal scroll, the bubble's swipe strip, the chat list).
 *
 * The WebView is hosted through Compose's view interop: once it asks its parent to stop
 * intercepting, every later pointer change of that gesture is consumed before any Compose
 * scrollable sees it. Claiming on the first horizontal pixel therefore froze whatever the
 * equation sat on. The WebView may only claim a drag that
 * - is clearly horizontal,
 * - has travelled at least the platform touch slop, and
 * - it can actually scroll in that direction (a fitting inline equation never can).
 *
 * [dx]/[dy] are the finger's travel since ACTION_DOWN in view pixels. A finger moving right
 * (positive [dx]) reveals content on the left, so it needs [canScrollLeft]; moving left needs
 * [canScrollRight].
 */
internal fun shouldMathWebViewClaimHorizontalDrag(
    dx: Float,
    dy: Float,
    touchSlop: Float,
    canScrollLeft: Boolean,
    canScrollRight: Boolean
): Boolean {
    val horizontal = abs(dx)
    if (horizontal <= abs(dy)) return false
    if (horizontal < touchSlop) return false
    return if (dx > 0f) canScrollLeft else canScrollRight
}
