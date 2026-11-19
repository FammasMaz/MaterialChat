package com.materialchat.util

/**
 * Per-message image limits shared by the chat composer and the assistant overlay, so both
 * refuse extras at the same count and with the same wording.
 */
object AttachmentLimits {
    /** Maximum number of image attachments per message. */
    const val MAX_PER_MESSAGE = 4

    /** How many more images a message holding [existing] can take; never negative. */
    fun room(existing: Int): Int = (MAX_PER_MESSAGE - existing).coerceAtLeast(0)

    /**
     * Whether one more image can be attached to a message holding [existing]. The composer
     * asks this before launching the picker or the camera, so at the limit the user gets
     * [limitNotice] at once instead of after a round trip through another app.
     */
    fun hasRoom(existing: Int): Boolean = room(existing) > 0

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
