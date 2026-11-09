package com.materialchat.assistant.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The assistant can look like it vanished after an app update: Android still
 * records MaterialChat as the selected assistant, but its binding to the
 * assistant service is gone, so the gesture does nothing until the user
 * re-selects an assistant. These cases pin the decision that drives the
 * reconnect prompt.
 */
class AssistantBindingStatusTest {

    @Test
    fun `not selected is never reported as needing reconnect`() {
        assertEquals(
            AssistantBindingStatus.NotSelected,
            AssistantBindingStatus.resolve(isSelectedAssistant = false, isServiceActive = false)
        )
        assertEquals(
            AssistantBindingStatus.NotSelected,
            AssistantBindingStatus.resolve(isSelectedAssistant = false, isServiceActive = true)
        )
    }

    @Test
    fun `selected and active is connected`() {
        assertEquals(
            AssistantBindingStatus.Connected,
            AssistantBindingStatus.resolve(isSelectedAssistant = true, isServiceActive = true)
        )
    }

    @Test
    fun `selected but inactive needs reconnect`() {
        // The post-update case: the setting still names MaterialChat, but the
        // platform is not routing to it.
        assertEquals(
            AssistantBindingStatus.NeedsReconnect,
            AssistantBindingStatus.resolve(isSelectedAssistant = true, isServiceActive = false)
        )
    }
}
