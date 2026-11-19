package com.materialchat.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OPEN_SETTINGS launch from the assistant notice must route to Settings exactly once per
 * launch: raised by the intent, cleared by the nav host, raised again by the next intent. An
 * OPEN_CHAT launch carries its conversation id and never touches the settings flag; anything
 * else leaves both untouched.
 */
class AssistantLaunchRouteTest {

    @Test
    fun `open settings is pending until the nav host handles it, then routes again on the next launch`() {
        val route = AssistantLaunchRoute()
        assertFalse(route.pendingOpenSettings)

        assertTrue(route.accept(AssistantLaunchRoute.ACTION_OPEN_SETTINGS, null))
        assertTrue("intent raises the flag", route.pendingOpenSettings)
        assertNull("settings launch never sets a conversation", route.pendingConversationId)

        route.settingsHandled()
        assertFalse("nav host clears it after routing", route.pendingOpenSettings)

        // onNewIntent while the activity is alive: the same action must route a second time.
        assertTrue(route.accept(AssistantLaunchRoute.ACTION_OPEN_SETTINGS, null))
        assertTrue(route.pendingOpenSettings)
    }

    @Test
    fun `open chat carries the conversation id and leaves the settings flag alone`() {
        val route = AssistantLaunchRoute()

        assertTrue(route.accept(AssistantLaunchRoute.ACTION_OPEN_CHAT, "conv-1"))
        assertEquals("conv-1", route.pendingConversationId)
        assertFalse(route.pendingOpenSettings)

        // A chat launch without an id (assistant opened a brand-new chat) clears the previous one.
        assertTrue(route.accept(AssistantLaunchRoute.ACTION_OPEN_CHAT, null))
        assertNull(route.pendingConversationId)
    }

    @Test
    fun `a plain launcher start or a null restart intent changes nothing`() {
        val route = AssistantLaunchRoute()
        route.accept(AssistantLaunchRoute.ACTION_OPEN_SETTINGS, null)
        route.accept(AssistantLaunchRoute.ACTION_OPEN_CHAT, "conv-1")

        assertFalse(route.accept(null, null))
        assertFalse(route.accept("android.intent.action.MAIN", "ignored"))

        assertTrue("unrelated intents keep the pending settings route", route.pendingOpenSettings)
        assertEquals("unrelated intents keep the pending conversation", "conv-1", route.pendingConversationId)
    }

    @Test
    fun `the action strings are the ones the assistant session sends`() {
        // MaterialChatVoiceInteractionSession reads MainActivity.ACTION_OPEN_SETTINGS, which aliases these.
        assertEquals("com.materialchat.OPEN_CHAT", AssistantLaunchRoute.ACTION_OPEN_CHAT)
        assertEquals("com.materialchat.OPEN_SETTINGS", AssistantLaunchRoute.ACTION_OPEN_SETTINGS)
        assertEquals("conversation_id", AssistantLaunchRoute.EXTRA_CONVERSATION_ID)
    }
}
