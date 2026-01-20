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
     * Requires a conversationId argument.
     */
    data object Chat : Screen("chat/{conversationId}") {

        /**
         * Creates the navigation route for a specific conversation.
         * @param conversationId The UUID of the conversation to display.
         */
        fun createRoute(conversationId: String): String = "chat/$conversationId"

        /**
         * Argument key for the conversation ID.
         */
        const val ARG_CONVERSATION_ID = "conversationId"
    }

    /**
     * Settings screen for managing providers, system prompt, and app preferences.
     */
    data object Settings : Screen("settings")

    companion object {
        /**
         * The start destination for the app navigation.
         */
        val startDestination: String = Conversations.route

        /**
         * List of all screens for navigation setup.
         */
        val allScreens: List<Screen> = listOf(Conversations, Chat, Settings)
    }
}
