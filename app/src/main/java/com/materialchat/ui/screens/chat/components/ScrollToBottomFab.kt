package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive scroll-to-bottom FAB.
 *
 * Appears when the user scrolls up in the chat, providing a quick way to jump
 * back to the latest message. Shows a "New" indicator when new content has
 * arrived while scrolled away.
 *
 * Follows M3 Expressive guidelines:
 * - Pill shape (fully rounded)
 * - primaryContainer color for emphasis
 * - Spring-based entrance/exit animation with overshoot
 * - 48dp minimum touch target
 * - Haptic feedback on tap
 */
@Composable
fun ScrollToBottomFab(
    visible: Boolean,
    hasNewContent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = ExpressiveMotion.Spatial.playful(),
            initialOffsetY = { it }
        ) + scaleIn(
            animationSpec = ExpressiveMotion.Spatial.scale(),
            initialScale = 0.6f
        ) + fadeIn(
            animationSpec = ExpressiveMotion.Effects.alpha()
        ),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),
            targetOffsetY = { it }
        ) + scaleOut(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),
            targetScale = 0.6f
        ) + fadeOut(
            animationSpec = ExpressiveMotion.Effects.alpha()
        )
    ) {
        Surface(
            onClick = {
                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                onClick()
            },
            shape = RoundedCornerShape(999.dp),
            color = if (hasNewContent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (hasNewContent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shadowElevation = 2.dp,
            modifier = Modifier.size(height = 40.dp, width = if (hasNewContent) 100.dp else 40.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (hasNewContent) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(20.dp)
                )
                if (hasNewContent) {
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
