package com.materialchat.data.local.preferences

/**
 * How long after the assistant's last activity a new invocation keeps talking in the same chat.
 *
 * [NEVER] (the default) starts a fresh chat on every invocation. Starting fresh only clears the
 * overlay's own state; the saved conversation stays in the app's chat list.
 */
enum class AssistantContinuation(val windowMs: Long?, val label: String) {
    NEVER(null, "Never"),
    ONE_MINUTE(60_000L, "Within 1 minute"),
    FIVE_MINUTES(5 * 60_000L, "Within 5 minutes"),
    FIFTEEN_MINUTES(15 * 60_000L, "Within 15 minutes"),
    ONE_HOUR(60 * 60_000L, "Within 1 hour");

    /**
     * True when a chat last touched at [lastActivityAtMs] should continue at [nowMs].
     *
     * No recorded activity never continues. A clock that moved backwards counts as no time
     * elapsed rather than as an expired window, so a time change cannot drop a live chat.
     */
    fun continues(lastActivityAtMs: Long?, nowMs: Long): Boolean {
        val window = windowMs ?: return false
        if (lastActivityAtMs == null) return false
        return (nowMs - lastActivityAtMs).coerceAtLeast(0L) <= window
    }

    companion object {
        val DEFAULT = NEVER

        /** Unknown or missing stored names fall back to [DEFAULT]. */
        fun fromStored(name: String?): AssistantContinuation =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
