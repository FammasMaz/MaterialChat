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
import com.materialchat.ui.screens.chat.ChatScreen
import com.materialchat.ui.screens.conversations.ConversationsScreen
import com.materialchat.ui.screens.settings.SettingsScreen
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
 * Handles navigation between Conversations, Chat, and Settings screens.
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
                    }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString(Screen.Chat.ARG_CONVERSATION_ID)
                    ?: return@composable

                CompositionLocalProvider(
                    LocalAnimatedVisibilityScope provides this@composable
                ) {
                    ChatScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // Settings screen
            composable(route = Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        } // Close CompositionLocalProvider
    }
}
