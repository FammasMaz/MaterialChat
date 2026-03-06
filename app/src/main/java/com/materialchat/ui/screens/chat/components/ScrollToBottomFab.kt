package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
 * - Pill shape (fully rounded) for friendly, modern feel
 * - primaryContainer color for emphasis, surfaceContainerHigh for neutral
 * - Tonal elevation (no drop shadow) — prevents shadow-before-icon artifacts
 * - Slide + fade entrance (no scale) for clean appearance
 * - Subtle bobbing animation on the arrow icon to suggest downward scroll
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
            animationSpec = ExpressiveMotion.Spatial.default(),
            initialOffsetY = { it }
        ) + fadeIn(
            animationSpec = ExpressiveMotion.Effects.alpha()
        ),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),
            targetOffsetY = { it }
        ) + fadeOut(
            animationSpec = ExpressiveMotion.Effects.alpha()
        )
    ) {
        // Gentle bobbing on the arrow — suggests "scroll down"
        val bobAmplitude = with(LocalDensity.current) { 2.dp.toPx() }
        val infiniteTransition = rememberInfiniteTransition(label = "scroll_hint")
        val bobOffset by infiniteTransition.animateFloat(
            initialValue = -bobAmplitude,
            targetValue = bobAmplitude,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bobOffset"
        )

        Surface(
            onClick = {
                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                onClick()
            },
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { translationY = bobOffset }
                )
                AnimatedVisibility(
                    visible = hasNewContent,
                    enter = expandHorizontally(
                        animationSpec = ExpressiveMotion.Spatial.default()
                    ) + fadeIn(
                        animationSpec = ExpressiveMotion.Effects.alpha()
                    ),
                    exit = shrinkHorizontally(
                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
                    ) + fadeOut(
                        animationSpec = ExpressiveMotion.Effects.alpha()
                    )
                ) {
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
