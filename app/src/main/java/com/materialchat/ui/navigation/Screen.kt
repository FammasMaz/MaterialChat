package com.materialchat.ui.navigation

/**
 * Defines all navigation destinations in the MaterialChat app.
 * Uses sealed class for type-safe navigation with route strings.
 */
sealed class Screen(val route: String) {

    /**
     * Conversations list screen - the main/home screen.
     * Displays all past conversations sorted by last updated.
     */
    data object Conversations : Screen("conversations")

    /**
     * Chat screen for a specific conversation.
     * Requires a conversationId argument. Supports optional autoSend query param.
     */
    data object Chat : Screen("chat/{conversationId}?autoSend={autoSend}&overrideModel={overrideModel}") {

        /**
         * Creates the navigation route for a specific conversation.
         * @param conversationId The UUID of the conversation to display.
         * @param autoSend Whether to automatically send/regenerate on navigation.
         * @param overrideModel Optional model name to use for auto-sent regeneration.
         */
        fun createRoute(
            conversationId: String,
            autoSend: Boolean = false,
            overrideModel: String? = null
        ): String {
            var route = "chat/$conversationId?autoSend=$autoSend"
            if (overrideModel != null) {
                route += "&overrideModel=$overrideModel"
            }
            return route
        }

        const val ARG_CONVERSATION_ID = "conversationId"
        const val ARG_AUTO_SEND = "autoSend"
        const val ARG_OVERRIDE_MODEL = "overrideModel"
    }

    /**
     * Settings screen for managing providers, system prompt, and app preferences.
     */
    data object Settings : Screen("settings")

    /**
     * Insights screen for viewing conversation intelligence dashboard.
     */
    data object Insights : Screen("insights")

    companion object {
        /**
         * The start destination for the app navigation.
         */
        val startDestination: String = Conversations.route

        /**
         * List of all screens for navigation setup.
         */
        val allScreens: List<Screen> = listOf(Conversations, Chat, Settings, Insights)
    }
}
