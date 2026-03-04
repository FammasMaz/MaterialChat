package com.materialchat.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive floating toolbar navigation for MaterialChat.
 *
 * Uses HorizontalFloatingToolbar with VibrantFloatingActionButton for "New Chat",
 * scroll-to-hide behavior via FloatingToolbarScrollBehavior, and spring-animated
 * color transitions per M3 Expressive guidelines.
 *
 * @param currentRoute The current navigation route for highlighting the active tab
 * @param onTabSelected Callback when a tab is selected
 * @param onNewChat Callback to create a new conversation
 * @param isOpenClawConnected Whether the OpenClaw Gateway is currently connected
 * @param scrollBehavior Scroll behavior for auto-hiding on scroll
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialChatNavBar(
    currentRoute: String?,
    onTabSelected: (TopLevelTab) -> Unit,
    onNewChat: () -> Unit,
    isOpenClawConnected: Boolean,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    modifier: Modifier = Modifier
) {
    val tabs = TopLevelTab.entries

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            val fabInteractionSource = remember { MutableInteractionSource() }
            val isFabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (isFabPressed) 0.92f else 1f,
                animationSpec = ExpressiveMotion.Spatial.playful(),
                label = "fabScale"
            )
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onNewChat,
                interactionSource = fabInteractionSource,
                modifier = Modifier.graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New Chat"
                )
            }
        },
        content = {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                val icon = tabIcon(tab)

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                // M3 Expressive: Spatial spring for scale press feedback
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.85f else 1f,
                    animationSpec = ExpressiveMotion.Spatial.scale(),
                    label = "navScale_${tab.name}"
                )

                // M3 Expressive: Effects spring for icon tint
                // Vibrant toolbar uses primaryContainer background, so content must use onPrimaryContainer
                val tint by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    },
                    animationSpec = spring(
                        stiffness = 500f,
                        dampingRatio = 1.0f  // Effects - no overshoot
                    ),
                    label = "navTint_${tab.name}"
                )

                IconButton(
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    interactionSource = interactionSource
                ) {
                    // OpenClaw tab gets a connection status badge
                    if (tab == TopLevelTab.OPENCLAW) {
                        BadgedBox(
                            badge = {
                                val badgeColor by animateColorAsState(
                                    targetValue = if (isOpenClawConnected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    animationSpec = spring(
                                        stiffness = 500f,
                                        dampingRatio = 1.0f
                                    ),
                                    label = "badgeColor"
                                )
                                Badge(
                                    modifier = Modifier.size(8.dp),
                                    containerColor = badgeColor
                                )
                            }
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = tint
                            )
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = tint
                        )
                    }
                }
            }
        }
    )
}

/**
 * Returns the icon for a given top-level tab.
 */
private fun tabIcon(tab: TopLevelTab): ImageVector = when (tab) {
    TopLevelTab.CHAT -> Icons.AutoMirrored.Filled.Chat
    TopLevelTab.OPENCLAW -> Icons.Filled.Hub
    TopLevelTab.EXPLORE -> Icons.Filled.Explore
    TopLevelTab.SETTINGS -> Icons.Filled.Settings
}
