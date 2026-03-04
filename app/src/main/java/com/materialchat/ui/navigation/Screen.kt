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

    data object Arena : Screen("arena")
    data object ArenaLeaderboard : Screen("arena/leaderboard")
    data object PersonaStudio : Screen("personas")
    data object Bookmarks : Screen("bookmarks")

    /**
     * Smart Canvas screen for rendering live artifacts (HTML, Mermaid, SVG, LaTeX).
     */
    data object Canvas : Screen("canvas/{artifactData}") {
        fun createRoute(artifactData: String): String {
            return "canvas/${java.net.URLEncoder.encode(artifactData, "UTF-8")}"
        }
        const val ARG_ARTIFACT_DATA = "artifactData"
    }

    /**
     * Mind Map screen for visualizing conversation branches as a tree graph.
     */
    data object MindMap : Screen("mindmap/{conversationId}") {
        fun createRoute(conversationId: String): String {
            return "mindmap/$conversationId"
        }
        const val ARG_CONVERSATION_ID = "conversationId"
    }

    /**
     * Workflows list screen showing all prompt chain workflows.
     */
    data object Workflows : Screen("workflows")

    /**
     * Workflow builder screen for creating/editing a workflow.
     */
    data object WorkflowBuilder : Screen("workflow-builder/{workflowId}") {
        fun createRoute(workflowId: String? = null): String {
            return "workflow-builder/${workflowId ?: "new"}"
        }
        const val ARG_WORKFLOW_ID = "workflowId"
    }

    /**
     * Workflow execution screen for running a workflow step-by-step.
     */
    data object WorkflowExecution : Screen("workflow-execution/{workflowId}") {
        fun createRoute(workflowId: String): String {
            return "workflow-execution/$workflowId"
        }
        const val ARG_WORKFLOW_ID = "workflowId"
    }

    /**
     * OpenClaw Gateway dashboard screen - shows connection status and quick actions.
     */
    data object OpenClawDashboard : Screen("openclaw")

    /**
     * OpenClaw chat screen for a gateway session.
     * @property sessionKey Optional session key; null starts a new session.
     */
    data object OpenClawChat : Screen("openclaw/chat/{sessionKey}") {
        fun createRoute(sessionKey: String? = null): String {
            val key = sessionKey ?: "new"
            return "openclaw/chat/${java.net.URLEncoder.encode(key, "UTF-8")}"
        }
        const val ARG_SESSION_KEY = "sessionKey"
    }

    /**
     * OpenClaw sessions list screen showing all gateway chat sessions.
     */
    data object OpenClawSessions : Screen("openclaw/sessions")

    /**
     * Explore hub screen - central navigation for Arena, Insights, Bookmarks, Workflows, Personas.
     */
    data object Explore : Screen("explore")

    companion object {
        val startDestination: String = "conversations"
        val allScreens: List<Screen>
            get() = listOf(
                Conversations, Chat, Settings,
                Arena, ArenaLeaderboard, Insights, PersonaStudio, Bookmarks,
                Canvas, MindMap, Workflows, WorkflowBuilder, WorkflowExecution,
                OpenClawDashboard, OpenClawChat, OpenClawSessions, Explore
            )
    }
}

/**
 * Top-level navigation tabs for the bottom Navigation Bar.
 * Maps each tab to its root screen route and display label.
 *
 * Uses string literals for routes to avoid static initialization order issues
 * with the Screen sealed class data objects.
 */
enum class TopLevelTab(val route: String, val label: String) {
    CHAT("conversations", "Chat"),
    OPENCLAW("openclaw", "OpenClaw"),
    EXPLORE("explore", "Explore"),
    SETTINGS("settings", "Settings")
}
