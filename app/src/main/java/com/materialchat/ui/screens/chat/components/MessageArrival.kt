package com.materialchat.ui.screens.chat.components

/**
 * Decides which message bubbles get the expressive arrival animation.
 *
 * Only a message that has just been created should scale/fade in. Items that merely scroll
 * into view, the initial load of a conversation, and bulk swaps (sibling navigation, branch
 * loads) must compose at rest, otherwise every item recycled by the LazyColumn would "arrive"
 * again. The tracker therefore:
 *
 * - treats the first [sync] as the baseline (nothing animates), and any later sync that adds
 *   more than [BULK_LIMIT] ids as a bulk load (nothing animates);
 * - marks the one or two fresh ids of an ordinary send / stream start / regenerate as arriving;
 * - hands the arrival out exactly once per id, and only within [ARRIVAL_WINDOW_MS] of the sync
 *   so an item that was created off-screen does not animate when it is scrolled into view later.
 */
internal class MessageArrivalTracker(
    private val now: () -> Long = System::currentTimeMillis
) {
    private val known = HashSet<String>()
    private val arriving = HashMap<String, Long>()
    private var primed = false

    /** Called from composition whenever the list of messages changes. */
    fun sync(ids: List<String>) {
        val fresh = ids.filter { it !in known }
        known.addAll(fresh)
        if (!primed || fresh.size > BULK_LIMIT) {
            primed = true
            return
        }
        val stamp = now()
        fresh.forEach { arriving[it] = stamp }
    }

    /** True once, for a message created in the last [ARRIVAL_WINDOW_MS]. */
    fun shouldAnimate(id: String): Boolean {
        val stamp = arriving.remove(id) ?: return false
        return now() - stamp <= ARRIVAL_WINDOW_MS
    }

    companion object {
        const val BULK_LIMIT = 2
        const val ARRIVAL_WINDOW_MS = 1_500L
    }
}

/** Scale a bubble starts from when it arrives. */
internal const val MESSAGE_ARRIVAL_SCALE = 0.96f

/** One subtle settle of the container when its stream completes. */
internal const val STREAM_END_SETTLE_SCALE = 0.985f
