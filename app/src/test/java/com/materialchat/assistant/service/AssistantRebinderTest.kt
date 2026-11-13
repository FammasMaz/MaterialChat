package com.materialchat.assistant.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After an update Android keeps its dead binding because the assistant component name is
 * unchanged. The repair must fire in exactly that state and nowhere else: toggling the
 * component while the user has deliberately selected another assistant would fight them.
 */
class AssistantRebinderTest {

    private fun shouldRebind(selected: Boolean, active: Boolean): Boolean =
        AssistantRebinder.shouldRebind(isSelectedAssistant = selected, isServiceActive = active)

    @Test fun `repairs a dead binding when we are the selected assistant`() {
        assertTrue(shouldRebind(selected = true, active = false))
    }

    @Test fun `leaves a working binding alone`() {
        assertFalse(shouldRebind(selected = true, active = true))
    }

    @Test fun `never touches the binding when another assistant is selected`() {
        assertFalse(shouldRebind(selected = false, active = false))
        assertFalse(shouldRebind(selected = false, active = true))
    }
}
