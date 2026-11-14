package com.materialchat.assistant.ui

/** How long the screen stays on after the assistant stops working, so a reply can be read. */
const val ASSISTANT_KEEP_AWAKE_GRACE_MILLIS = 60_000L

/**
 * Whether the assistant session should hold the screen on.
 *
 * On the lock screen the display timeout used to cut off a spoken reply mid-sentence and hide
 * the session. The session keeps the screen on while it is working (listening, thinking,
 * streaming, speaking) and for a grace period afterwards; once that passes the screen is
 * released so a forgotten assistant cannot hold the display on indefinitely.
 *
 * @param busy the assistant is listening, thinking, streaming or speaking
 * @param idleForMillis how long it has been idle; 0 while busy
 */
fun shouldKeepScreenOn(
    busy: Boolean,
    idleForMillis: Long,
    graceMillis: Long = ASSISTANT_KEEP_AWAKE_GRACE_MILLIS
): Boolean = busy || idleForMillis < graceMillis
