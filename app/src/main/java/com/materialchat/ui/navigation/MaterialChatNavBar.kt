package com.materialchat.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive bottom Navigation Bar for MaterialChat.
 *
 * Follows Material 3 Expressive design guidelines:
 * - 80dp container height
 * - 3-5 destinations (4 tabs: Chat, OpenClaw, Explore, Settings)
 * - Spring-animated selection indicator
 * - secondaryContainer for active indicator color
 * - 48dp minimum touch targets
 * - Emphasized typography for selected label
 * - Connection status badge on OpenClaw tab
 *
 * @param currentRoute The current navigation route for highlighting the active tab
 * @param onTabSelected Callback when a tab is selected
 * @param isOpenClawConnected Whether the OpenClaw Gateway is currently connected
 * @param modifier Optional modifier for the navigation bar
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialChatNavBar(
    currentRoute: String?,
    onTabSelected: (TopLevelTab) -> Unit,
    isOpenClawConnected: Boolean,
    modifier: Modifier = Modifier
) {
    // M3 Expressive: Spring-animated indicator scale for the selected tab
    val tabs = TopLevelTab.entries

    NavigationBar(
        modifier = modifier.height(80.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        tabs.forEach { tab ->
            val isSelected = currentRoute == tab.route

            // M3 Expressive: Spatial spring for indicator scale (can bounce)
            val indicatorScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(
                    stiffness = 500f,
                    dampingRatio = 0.7f  // Spatial - subtle bounce
                ),
                label = "indicatorScale_${tab.name}"
            )

            // M3 Expressive: Effects spring for label alpha (no bounce)
            val labelAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.7f,
                animationSpec = spring(
                    stiffness = 500f,
                    dampingRatio = 1.0f  // Effects - no overshoot
                ),
                label = "labelAlpha_${tab.name}"
            )

            val icon = tabIcon(tab)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    // OpenClaw tab gets a connection status badge
                    if (tab == TopLevelTab.OPENCLAW) {
                        BadgedBox(
                            badge = {
                                // Connection status dot: green = connected, red = disconnected
                                val badgeColor by animateColorAsState(
                                    targetValue = if (isOpenClawConnected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    animationSpec = spring(
                                        stiffness = 500f,
                                        dampingRatio = 1.0f  // Effects - smooth color transition
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
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
