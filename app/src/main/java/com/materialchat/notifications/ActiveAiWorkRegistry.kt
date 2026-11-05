package com.materialchat.notifications

/**
 * Tracks which pieces of background AI work currently need the keep-alive
 * notification.
 *
 * The foreground service is process-wide, but several owners can be working at
 * once (a chat stream plus an image generation, for example). Each start must
 * be matched by a stop for the same owner, and the notification should only be
 * torn down when the last owner finishes.
 */
object ActiveAiWorkRegistry {

    private val owners = LinkedHashSet<String>()

    @Synchronized
    fun begin(ownerId: String) {
        owners.add(ownerId)
    }

    /**
     * Marks [ownerId] as finished and reports whether other work is still
     * running, so the caller knows whether to keep the service alive.
     */
    @Synchronized
    fun finish(ownerId: String): Boolean {
        owners.remove(ownerId)
        return owners.isNotEmpty()
    }

    @Synchronized
    fun contains(ownerId: String): Boolean = ownerId in owners

    @Synchronized
    fun activeCount(): Int = owners.size

    @Synchronized
    fun clear() {
        owners.clear()
    }
}
