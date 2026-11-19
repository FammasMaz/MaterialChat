package com.materialchat.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The pending route an assistant launch intent asks `MainActivity` to take, kept apart from the
 * activity so the rule is a plain JVM unit: no `Intent`, no Robolectric (`AssistantLaunchRouteTest`).
 *
 * [ACTION_OPEN_CHAT] carries an optional conversation id that the nav host reads once when it is
 * first composed. [ACTION_OPEN_SETTINGS] sets a one-shot flag that survives until the nav host has
 * navigated to Settings and calls [settingsHandled]; a later OPEN_SETTINGS launch (onNewIntent)
 * raises it again. Any other action, or a null intent on a system restart, changes nothing.
 */
class AssistantLaunchRoute {
    /** Conversation to open from an OPEN_CHAT launch; null when none was requested. */
    var pendingConversationId: String? = null
        private set

    /** True from an OPEN_SETTINGS launch until the nav host reports it has routed there. */
    var pendingOpenSettings: Boolean by mutableStateOf(false)
        private set

    /**
     * Records the request carried by an intent's [action] and its conversation extra.
     *
     * @return true when [action] was one of the assistant actions and the pending route changed
     */
    fun accept(action: String?, conversationId: String?): Boolean = when (action) {
        ACTION_OPEN_CHAT -> {
            pendingConversationId = conversationId
            true
        }
        ACTION_OPEN_SETTINGS -> {
            pendingOpenSettings = true
            true
        }
        else -> false
    }

    /** The nav host has navigated to Settings; the flag is cleared so it routes only once. */
    fun settingsHandled() {
        pendingOpenSettings = false
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.materialchat.OPEN_CHAT"
        const val ACTION_OPEN_SETTINGS = "com.materialchat.OPEN_SETTINGS"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
