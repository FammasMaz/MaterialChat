package com.materialchat.assistant.ui

/**
 * The pill's photo reveal: whether the two attach actions ("Take photo", "Attach photo") are
 * opened inside the pill. Pure state so the rules are testable without a composition:
 *
 * - The affordance (the `+` in the pill's trailing cluster) toggles the reveal.
 * - Choosing an action closes it: the camera or picker takes over.
 * - It closes whenever the affordance itself leaves the pill (keyboard mode ends with nothing
 *   attached), when the keyboard is hidden, and when the pill hides (session dismissed).
 * - Pending photos keep the pill opened on their own, independently of the reveal, so the
 *   thumbnails stay visible and removable until the question is sent.
 */
internal data class AssistantAttachRevealState(val open: Boolean = false) {
    /** The affordance was tapped. */
    fun toggled(): AssistantAttachRevealState = copy(open = !open)

    /** One of the two actions was chosen; the reveal folds back into the pill. */
    fun modeChosen(): AssistantAttachRevealState = copy(open = false)

    /** The affordance is shown or hidden; a hidden affordance cannot keep the reveal open. */
    fun affordanceVisibilityChanged(visible: Boolean): AssistantAttachRevealState =
        if (visible) this else copy(open = false)

    /** Keyboard mode ended (the user tapped the mic, hide keyboard, or sent): the pill loses focus. */
    fun keyboardHidden(): AssistantAttachRevealState = copy(open = false)

    /** The pill is leaving the screen. */
    fun pillHidden(): AssistantAttachRevealState = copy(open = false)

    companion object {
        /**
         * The `+` lives in the pill only where attaching makes sense: in keyboard mode, or while
         * something is already attached. The resting voice pill is untouched.
         */
        fun affordanceVisible(keyboardMode: Boolean, attachmentCount: Int): Boolean =
            keyboardMode || attachmentCount > 0

        /** The pill is taller than its 76dp row: the reveal is open or photos are attached. */
        fun expanded(open: Boolean, attachmentCount: Int): Boolean = open || attachmentCount > 0
    }
}
