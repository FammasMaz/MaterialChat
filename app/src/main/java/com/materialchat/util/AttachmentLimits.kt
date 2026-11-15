package com.materialchat.util

/**
 * Per-message image limits shared by the chat composer and the assistant overlay, so both
 * refuse extras at the same count and with the same wording.
 */
object AttachmentLimits {
    /** Maximum number of image attachments per message. */
    const val MAX_PER_MESSAGE = 4

    /** The notice shown when a further image would exceed [MAX_PER_MESSAGE]. */
    fun limitNotice(): String = "Maximum $MAX_PER_MESSAGE images allowed"

    /** The notice shown when a multi-selection had to be trimmed to fit the limit. */
    fun skippedNotice(dropped: Int): String = "Only $MAX_PER_MESSAGE images per message; $dropped skipped"
}
