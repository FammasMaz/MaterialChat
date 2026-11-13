package com.materialchat.assistant.ui

/**
 * Vertical spacing that keeps the assistant card directly above the keyboard.
 *
 * The voice interaction session window is edge-to-edge and never resizes for the IME
 * (`SOFT_INPUT_ADJUST_NOTHING` plus `setFitInsetsTypes(0)` on API 30+), so the keyboard arrives as
 * an inset of the full-height window instead of shrinking the Compose host. The card therefore has
 * to reserve the IME's own height to sit directly above the keyboard. Once the keyboard closes the
 * navigation bar is the only obstruction left.
 *
 * The previous implementation derived the obstruction from
 * `View.getWindowVisibleDisplayFrame()`, which mixes window-frame and screen coordinates and does
 * not report the IME for a window that does not fit insets. On a device with Gboard it returned a
 * frame bottom 341dp from the top of a 1068dp screen, so the scrim reserved 727dp instead of the
 * 435dp keyboard and pushed the card to the top of the screen.
 */
internal object AssistantKeyboardSpacing {

    /**
     * Space between the card's bottom edge and the keyboard, or the navigation bar once the
     * keyboard is closed. `scripts/check-assistant-window.sh` asserts this same gap (8dp +-2dp)
     * against the real card edge on device.
     */
    const val GAP_DP = 8

    /**
     * Bottom padding for the overlay scrim, in pixels.
     *
     * The IME covers the navigation bar, so the two obstructions are alternatives rather than
     * additive: the larger one wins and the result is never negative.
     */
    fun bottomPaddingPx(imeBottomPx: Int, navigationBarBottomPx: Int): Int =
        maxOf(imeBottomPx, navigationBarBottomPx, 0)

    /**
     * API 30+ reports the IME as a WindowInsets.ime inset of the non-resizing session window.
     * API 26-29 do not deliver an IME inset to this window, so they resize it instead and measure
     * the remaining overlap from the visible display frame.
     */
    fun usesImeInsets(sdkInt: Int): Boolean = sdkInt >= 30
}
