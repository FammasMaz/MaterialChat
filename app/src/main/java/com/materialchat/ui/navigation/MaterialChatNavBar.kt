package com.materialchat.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull

/**
 * M3 Expressive floating toolbar navigation for MaterialChat.
 *
 * Uses HorizontalFloatingToolbar with VibrantFloatingActionButton for "New Chat",
 * collapse-to-FAB behavior on scroll, and spring-animated
 * color transitions per M3 Expressive guidelines.
 *
 * @param currentRoute The current navigation route for highlighting the active tab
 * @param onTabSelected Callback when a tab is selected
 * @param onNewChat Callback to create a new conversation
 * @param onNewChatLongPress Callback when the New Chat FAB is long-pressed (e.g. show persona picker)
 * @param expanded Whether the toolbar is expanded (showing icons) or collapsed (FAB only)
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialChatNavBar(
    currentRoute: String?,
    onTabSelected: (TopLevelTab) -> Unit,
    onNewChat: () -> Unit,
    onNewChatLongPress: () -> Unit = {},
    expanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val tabs = TopLevelTab.entries

    // M3 Expressive: Slide FAB toward end position (thumb zone) on toolbar collapse
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val endMarginPx = with(density) { 32.dp.toPx() } // Slightly inset from M3 ScreenOffset

    // Spatial spring for smooth FAB repositioning on collapse
    val collapseProgress by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = ExpressiveMotion.Spatial.fabExpand(),
        label = "fabCollapseProgress"
    )

    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier
            .graphicsLayer {
                // M3 Expressive: Slide FAB toward bottom-end position on collapse
                // Aligns with M3 standard FAB placement (ScreenOffset from end edge)
                val endAlignShift = (screenWidthPx / 2f - endMarginPx - size.width / 2f)
                    .coerceAtLeast(0f)
                translationX = endAlignShift * collapseProgress
            },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            val fabInteractionSource = remember { MutableInteractionSource() }
            val isFabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (isFabPressed) 0.92f else 1f,
                animationSpec = ExpressiveMotion.Spatial.playful(),
                label = "fabScale"
            )

            // Shared element transition: FAB morphs into MessageInput text field
            val sharedTransitionScope = LocalSharedTransitionScope.current
            val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
            val fabModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = SHARED_ELEMENT_FAB_TO_INPUT),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ ->
                                spring(dampingRatio = 0.7f, stiffness = 500f)
                            }
                        )
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        }
                }
            } else {
                Modifier.graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
            }

            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = { haptics.perform(HapticPattern.CLICK); onNewChat() },
                interactionSource = fabInteractionSource,
                modifier = fabModifier.pointerInput(onNewChatLongPress) {
                    awaitEachGesture {
                        // Observe down at Initial pass (parent first) without consuming
                        val first = awaitPointerEvent(PointerEventPass.Initial)
                        if (first.changes.none { it.pressed }) return@awaitEachGesture

                        // Race: release before timeout = tap (let FAB handle), timeout = long press
                        val timeout = viewConfiguration.longPressTimeoutMillis
                        val releasedBeforeTimeout = withTimeoutOrNull(timeout) {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.changes.all { !it.pressed }) break
                            }
                            true
                        }

                        if (releasedBeforeTimeout == null) {
                            // Long press detected — fire callback
                            onNewChatLongPress()
                            // Consume remaining events at Initial pass to prevent FAB onClick
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                ev.changes.forEach { it.consume() }
                                if (ev.changes.all { !it.pressed }) break
                            }
                        }
                    }
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
                    onClick = { haptics.perform(HapticPattern.CLICK); onTabSelected(tab) },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    interactionSource = interactionSource
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = tint
                    )
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
    TopLevelTab.EXPLORE -> Icons.Filled.Explore
    TopLevelTab.SETTINGS -> Icons.Filled.Settings
}
