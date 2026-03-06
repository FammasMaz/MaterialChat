package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated bookmark burst overlay for double-tap-to-bookmark.
 *
 * This is an M3 Expressive "hero moment" — it combines multiple expressive
 * tactics for maximum emotional impact:
 * - **Shape**: Icon scales from 0 to over 1.0 (overshoot) then settles
 * - **Motion**: Spring physics with bounce (spatial spring)
 * - **Color**: Primary color for prominence
 * - **Haptic**: EMPHASIS pattern for tactile confirmation
 *
 * The animation auto-plays when [trigger] increments, and auto-fades after completion.
 *
 * @param trigger Increment this value to trigger the burst animation
 * @param modifier Modifier for positioning the overlay
 * @param hapticsEnabled Whether haptic feedback is enabled
 */
@Composable
fun BookmarkBurstAnimation(
    trigger: Int,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var lastTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(trigger) {
        if (trigger > lastTrigger && trigger > 0) {
            lastTrigger = trigger
            haptics.perform(HapticPattern.EMPHASIS, hapticsEnabled)

            // Burst in: scale from 0 with playful overshoot
            launch {
                scale.snapTo(0f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.4f, // Very bouncy for hero moment
                        stiffness = 350f
                    )
                )
            }
            launch {
                alpha.snapTo(0f)
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = ExpressiveMotion.Effects.alpha()
                )
            }

            // Hold briefly, then fade out
            delay(600L)

            launch {
                scale.animateTo(
                    targetValue = 1.2f,
                    animationSpec = ExpressiveMotion.Spatial.scale()
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = ExpressiveMotion.Effects.alpha()
                )
            }
        }
    }

    if (alpha.value > 0.01f) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
            )
        }
    }
}
