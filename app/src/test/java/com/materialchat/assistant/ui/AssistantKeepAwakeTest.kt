package com.materialchat.assistant.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantKeepAwakeTest {
    @Test fun `holds the screen while the assistant works`() {
        assertTrue(shouldKeepScreenOn(busy = true, idleForMillis = 0))
        assertTrue(shouldKeepScreenOn(busy = true, idleForMillis = 10 * 60_000))
    }

    @Test fun `holds the screen for the grace period after the answer`() {
        assertTrue(shouldKeepScreenOn(busy = false, idleForMillis = 0))
        assertTrue(shouldKeepScreenOn(busy = false, idleForMillis = ASSISTANT_KEEP_AWAKE_GRACE_MILLIS - 1))
    }

    @Test fun `releases the screen once the grace period passes`() {
        assertFalse(shouldKeepScreenOn(busy = false, idleForMillis = ASSISTANT_KEEP_AWAKE_GRACE_MILLIS))
        assertFalse(shouldKeepScreenOn(busy = false, idleForMillis = 5 * 60_000))
    }
}
