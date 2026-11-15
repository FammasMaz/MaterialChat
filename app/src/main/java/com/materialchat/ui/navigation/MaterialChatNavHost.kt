package com.materialchat.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import com.materialchat.ui.screens.generatedimages.GeneratedImagesScreen
import com.materialchat.ui.screens.insights.InsightsScreen
import com.materialchat.ui.screens.memories.MemoriesScreen
import com.materialchat.ui.screens.mindmap.MindMapScreen
import com.materialchat.ui.screens.onboarding.OnboardingScreen
import com.materialchat.ui.screens.personas.PersonaStudioScreen
import com.materialchat.ui.screens.settings.InteractionSettingsScreen
import com.materialchat.ui.screens.settings.ModelAssignmentsScreen
import com.materialchat.ui.screens.settings.OnDeviceModelsScreen
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
 * and the Explore hub.
 *
 * Uses Material 3 Expressive spring-physics animations for transitions:
 * - Forward: the incoming screen slides in from a quarter width on the slow
 *   spatial spring while the outgoing screen parallaxes away and fades.
 * - Back: the outgoing screen scales down to 0.9 and slides toward the edge, so a
 *   predictive-back gesture (which seeks this transition) reads as the screen
 *   being peeled off; the previous screen scales back up underneath it.
 * All specs come from [ExpressiveMotion]; nothing is hand-tuned here.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialChatNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.startDestination
) {
    // SharedTransitionScope provided by parent (MaterialChatApp) for FAB-to-Input morph
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = ExpressiveMotion.Effects.reveal()) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = ExpressiveMotion.Spatial.screenSlide(),
                    initialOffset = { it / 4 }
                )
        },
        exitTransition = {
            fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = ExpressiveMotion.Spatial.collapse(),
                    targetOffset = { it / 8 }
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = ExpressiveMotion.Effects.reveal()) +
                scaleIn(
                    initialScale = 0.95f,
                    animationSpec = ExpressiveMotion.Spatial.expand()
                ) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = ExpressiveMotion.Spatial.screenSlide(),
                    initialOffset = { it / 8 }
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = ExpressiveMotion.Spatial.container()
                ) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = ExpressiveMotion.Spatial.screenSlide(),
                    targetOffset = { it / 4 }
                )
        }
    ) {
        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Conversations.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenProviderSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

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
                onNavigateToInteractionSettings = {
                    navController.navigate(Screen.InteractionSettings.route)
                },
                onNavigateToModelAssignments = {
                    navController.navigate(Screen.ModelAssignments.route)
                },
                onNavigateToOnDeviceModels = {
                    navController.navigate(Screen.OnDeviceModels.route)
                },
                onNavigateToMemories = {
                    navController.navigate(Screen.Memories.route)
                }
            )
        }

        composable(route = Screen.InteractionSettings.route) {
            InteractionSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.OnDeviceModels.route) {
            OnDeviceModelsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.ModelAssignments.route) {
            ModelAssignmentsScreen(
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

        composable(route = Screen.Memories.route) {
            MemoriesScreen(
                onNavigateBack = { navController.popBackStack() }
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

        // Generated image library
        composable(route = Screen.GeneratedImages.route) {
            GeneratedImagesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConversation = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
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
                onNavigateToGeneratedImages = {
                    navController.navigate(Screen.GeneratedImages.route)
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
}
