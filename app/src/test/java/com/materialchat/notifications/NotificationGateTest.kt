package com.materialchat.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGateTest {
    @Test fun `posts only when the switch, the permission and the background state allow it`() {
        assertTrue(shouldNotifyChatComplete(switchEnabled = true, canPostNotifications = true, appInForeground = false))
    }

    @Test fun `the settings switch is honoured`() {
        // The switch used to be drawn but never read, so "off" still produced notifications.
        assertFalse(shouldNotifyChatComplete(switchEnabled = false, canPostNotifications = true, appInForeground = false))
    }

    @Test fun `a missing system permission blocks the notification`() {
        assertFalse(shouldNotifyChatComplete(switchEnabled = true, canPostNotifications = false, appInForeground = false))
    }

    @Test fun `foreground chats do not notify`() {
        assertFalse(shouldNotifyChatComplete(switchEnabled = true, canPostNotifications = true, appInForeground = true))
    }
}
