package com.materialchat.util

/**
 * Per-message image limits shared by the chat composer and the assistant overlay, so both
 * refuse extras at the same count and with the same wording.
 */
object AttachmentLimits {
    /** Maximum number of image attachments per message. */
    const val MAX_PER_MESSAGE = 4

    /**
     * The notice shown when a further image would exceed [MAX_PER_MESSAGE]. Sentence form,
     * matching the assistant notice rules (sentence case, ends with a period, the last
     * sentence names what the user can do).
     */
    fun limitNotice(): String =
        "You can attach up to $MAX_PER_MESSAGE images per message. Remove one to add another."

    /** The notice shown when a multi-selection had to be trimmed to fit the limit. */
    fun skippedNotice(dropped: Int): String {
        val skipped = if (dropped == 1) "1 was skipped" else "$dropped were skipped"
        return "Only the first $MAX_PER_MESSAGE images were attached; $skipped."
    }
}
