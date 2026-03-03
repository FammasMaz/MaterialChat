package com.materialchat.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.materialchat.ui.screens.arena.ArenaScreen
import com.materialchat.ui.screens.arena.components.ArenaLeaderboard
import com.materialchat.ui.screens.bookmarks.BookmarksScreen
import com.materialchat.ui.screens.canvas.SmartCanvasScreen
import com.materialchat.ui.screens.chat.ChatScreen
import com.materialchat.ui.screens.conversations.ConversationsScreen
import com.materialchat.ui.screens.explore.ExploreScreen
import com.materialchat.ui.screens.insights.InsightsScreen
import com.materialchat.ui.screens.mindmap.MindMapScreen
import com.materialchat.ui.screens.openclaw.OpenClawChatScreen
import com.materialchat.ui.screens.openclaw.OpenClawDashboardScreen
import com.materialchat.ui.screens.openclaw.OpenClawSessionsScreen
import com.materialchat.ui.screens.personas.PersonaStudioScreen
import com.materialchat.ui.screens.settings.SettingsScreen
import com.materialchat.ui.screens.workflows.WorkflowBuilderScreen
import com.materialchat.ui.screens.workflows.WorkflowExecutionScreen
import com.materialchat.ui.screens.workflows.WorkflowsScreen
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * CompositionLocal for SharedTransitionScope - enables FAB-to-Input morph animation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * CompositionLocal for AnimatedVisibilityScope - required for shared element transitions.
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared element key for FAB-to-Input container transform.
 */
const val SHARED_ELEMENT_FAB_TO_INPUT = "fab_to_input_container"

/**
 * Main navigation host for MaterialChat app.
 * Handles navigation between all screens including Conversations, Chat, Settings,
 * OpenClaw Gateway screens, and the Explore hub.
 *
 * Uses Material 3 Expressive spring-physics animations for transitions:
 * - Spatial springs for position/slide (can bounce)
 * - Effects springs for opacity/fade (no bounce)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialChatNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.startDestination
) {
    // SharedTransitionLayout enables container transform animations (FAB-to-Input morph)
    SharedTransitionLayout {
        // Provide SharedTransitionScope to child composables
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this@SharedTransitionLayout
        ) {
            NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            // M3 Expressive: Spatial spring for slide + Effects spring for fade
            enterTransition = {
                fadeIn(
                    animationSpec = spring(
                        dampingRatio = 1.0f,  // Effects - no bounce for opacity
                        stiffness = 400f
                    )
                ) + slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = spring(
                        dampingRatio = 0.8f,  // Spatial - subtle bounce
                        stiffness = 300f
                    )
                )
            },
            exitTransition = {
                fadeOut(
                    animationSpec = spring(
                        dampingRatio = 1.0f,  // Effects - no bounce
                        stiffness = 400f
                    )
                ) + slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = spring(
                        dampingRatio = 1.0f,  // Smooth exit
                        stiffness = 300f
                    ),
                    targetOffset = { it / 4 }  // Subtle parallax effect
                )
            },
            popEnterTransition = {
                fadeIn(
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 400f
                    )
                ) + slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = spring(
                        dampingRatio = 0.8f,  // Spatial - subtle bounce
                        stiffness = 300f
                    )
                )
            },
            popExitTransition = {
                fadeOut(
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 400f
                    )
                ) + slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 300f
                    ),
                    targetOffset = { it / 4 }
                )
            }
        ) {
            // Conversations list screen (home)
            composable(route = Screen.Conversations.route) {
                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides this@composable
                ) {
                    ConversationsScreen(
                        onNavigateToChat = { conversationId ->
                            navController.navigate(Screen.Chat.createRoute(conversationId))
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }
            }

            // Chat screen with conversation ID argument
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument(Screen.Chat.ARG_CONVERSATION_ID) {
                        type = NavType.StringType
                    },
                    navArgument(Screen.Chat.ARG_AUTO_SEND) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument(Screen.Chat.ARG_OVERRIDE_MODEL) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString(Screen.Chat.ARG_CONVERSATION_ID)
                    ?: return@composable

                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides this@composable
                ) {
                    ChatScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToBranch = { newId, autoSend, overrideModel ->
                            navController.popBackStack()
                            navController.navigate(Screen.Chat.createRoute(newId, autoSend, overrideModel))
                        },
                        onNavigateToCanvas = { artifact ->
                            val artifactData = "${artifact.type.name}:::${artifact.language ?: ""}:::${artifact.code}"
                            navController.navigate(Screen.Canvas.createRoute(artifactData))
                        },
                        onNavigateToMindMap = { conversationId ->
                            navController.navigate(Screen.MindMap.createRoute(conversationId))
                        }
                    )
                }
            }

            // Settings screen
            composable(route = Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Arena mode screen
            composable(route = Screen.Arena.route) {
                ArenaScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLeaderboard = {
                        navController.navigate(Screen.ArenaLeaderboard.route)
                    }
                )
            }

            // Arena leaderboard screen
            composable(route = Screen.ArenaLeaderboard.route) {
                ArenaLeaderboard(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Insights dashboard screen
            composable(route = Screen.Insights.route) {
                InsightsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Persona studio screen
            composable(route = Screen.PersonaStudio.route) {
                PersonaStudioScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Bookmarks knowledge base screen
            composable(route = Screen.Bookmarks.route) {
                BookmarksScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToConversation = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    }
                )
            }

            // Smart Canvas screen for rendering live artifacts
            composable(
                route = Screen.Canvas.route,
                arguments = listOf(
                    navArgument(Screen.Canvas.ARG_ARTIFACT_DATA) {
                        type = NavType.StringType
                    }
                )
            ) {
                SmartCanvasScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Mind Map screen for conversation branch visualization
            composable(
                route = Screen.MindMap.route,
                arguments = listOf(
                    navArgument(Screen.MindMap.ARG_CONVERSATION_ID) {
                        type = NavType.StringType
                    }
                )
            ) {
                MindMapScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToConversation = { conversationId ->
                        navController.popBackStack()
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    }
                )
            }

            // Workflows list screen
            composable(route = Screen.Workflows.route) {
                WorkflowsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBuilder = { workflowId ->
                        navController.navigate(Screen.WorkflowBuilder.createRoute(workflowId))
                    },
                    onNavigateToExecution = { workflowId ->
                        navController.navigate(Screen.WorkflowExecution.createRoute(workflowId))
                    }
                )
            }

            // Workflow builder screen
            composable(
                route = Screen.WorkflowBuilder.route,
                arguments = listOf(
                    navArgument(Screen.WorkflowBuilder.ARG_WORKFLOW_ID) {
                        type = NavType.StringType
                    }
                )
            ) {
                WorkflowBuilderScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Workflow execution screen
            composable(
                route = Screen.WorkflowExecution.route,
                arguments = listOf(
                    navArgument(Screen.WorkflowExecution.ARG_WORKFLOW_ID) {
                        type = NavType.StringType
                    }
                )
            ) {
                WorkflowExecutionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // OpenClaw Gateway dashboard screen
            composable(route = Screen.OpenClawDashboard.route) {
                OpenClawDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { sessionKey ->
                        navController.navigate(Screen.OpenClawChat.createRoute(sessionKey))
                    },
                    onNavigateToSessions = {
                        navController.navigate(Screen.OpenClawSessions.route)
                    }
                )
            }

            // OpenClaw chat screen with optional session key
            composable(
                route = Screen.OpenClawChat.route,
                arguments = listOf(
                    navArgument(Screen.OpenClawChat.ARG_SESSION_KEY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                OpenClawChatScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // OpenClaw sessions list screen
            composable(route = Screen.OpenClawSessions.route) {
                OpenClawSessionsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { sessionKey ->
                        navController.navigate(Screen.OpenClawChat.createRoute(sessionKey))
                    }
                )
            }

            // Explore hub screen
            composable(route = Screen.Explore.route) {
                ExploreScreen(
                    onNavigateToArena = {
                        navController.navigate(Screen.Arena.route)
                    },
                    onNavigateToInsights = {
                        navController.navigate(Screen.Insights.route)
                    },
                    onNavigateToBookmarks = {
                        navController.navigate(Screen.Bookmarks.route)
                    },
                    onNavigateToWorkflows = {
                        navController.navigate(Screen.Workflows.route)
                    },
                    onNavigateToPersonas = {
                        navController.navigate(Screen.PersonaStudio.route)
                    }
                )
            }
        }
        } // Close CompositionLocalProvider
    }
}
