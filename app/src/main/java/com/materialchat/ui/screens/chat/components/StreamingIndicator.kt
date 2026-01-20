package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.MaterialChatMotion
import kotlin.math.sin

/**
 * Animated streaming indicator showing that a response is being generated.
 *
 * Features:
 * - Three bouncing dots in a wave pattern
 * - 100ms delay between each dot for wave effect
 * - Smooth 60fps animation using infinite transition
 * - Material 3 Expressive styling
 *
 * Based on PRD specification:
 * - Three dots, 8dp diameter
 * - Wave animation with 100ms offset between dots
 * - Color: onSurfaceVariant
 *
 * @param dotSize Size of each dot
 * @param dotSpacing Space between dots
 * @param color Color of the dots
 * @param modifier Modifier for the indicator container
 */
@Composable
fun StreamingIndicator(
    dotSize: Dp = 8.dp,
    dotSpacing: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming_indicator")

    // Animation phase for the wave effect (0 to 2*PI)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MaterialChatMotion.Durations.StreamingIndicator,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "streaming_phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            // Calculate offset for each dot based on phase
            // Each dot is delayed by 100ms (phase offset)
            val dotPhaseOffset = index * (MaterialChatMotion.Durations.StreamingDotDelay /
                MaterialChatMotion.Durations.StreamingIndicator.toFloat()) * (2 * Math.PI).toFloat()

            val bounceOffset = sin(phase + dotPhaseOffset).toFloat()
            val normalizedOffset = (bounceOffset + 1f) / 2f // Normalize to 0-1

            // Calculate vertical offset (bounce height: 4dp)
            val yOffset = -(normalizedOffset * 4f)

            // Calculate alpha (fade in/out with bounce)
            val alpha = 0.4f + (normalizedOffset * 0.6f)

            BouncingDot(
                size = dotSize,
                color = color,
                yOffset = yOffset.dp,
                alpha = alpha
            )
        }
    }
}

/**
 * Individual bouncing dot with vertical offset animation.
 */
@Composable
private fun BouncingDot(
    size: Dp,
    color: Color,
    yOffset: Dp,
    alpha: Float
) {
    Box(
        modifier = Modifier
            .size(size)
            .offset(y = yOffset)
            .alpha(alpha)
            .background(
                color = color,
                shape = CircleShape
            )
    )
}

/**
 * Alternative pulsing streaming indicator.
 * A simpler animation that pulses the dots in sequence.
 *
 * @param dotSize Size of each dot
 * @param dotSpacing Space between dots
 * @param color Color of the dots
 * @param modifier Modifier for the indicator container
 */
@Composable
fun PulsingStreamingIndicator(
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_indicator")

    // Animation progress (0 to 3, representing which dot is highlighted)
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsing_progress"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val isActive = progress.toInt() == index
            val alpha = if (isActive) 1f else 0.4f

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(alpha)
                    .background(
                        color = color,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Typing indicator with scale animation.
 * Similar to messaging apps' "user is typing" indicator.
 *
 * @param dotSize Size of each dot
 * @param dotSpacing Space between dots
 * @param color Color of the dots
 * @param modifier Modifier for the indicator container
 */
@Composable
fun TypingIndicator(
    dotSize: Dp = 8.dp,
    dotSpacing: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_indicator")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "typing_phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            // Each dot has a phase offset
            val dotPhase = phase + (index * Math.PI / 3).toFloat()
            val scale = 0.6f + (sin(dotPhase) + 1f) * 0.2f

            Box(
                modifier = Modifier
                    .size(dotSize * scale)
                    .background(
                        color = color.copy(alpha = 0.4f + scale * 0.3f),
                        shape = CircleShape
                    )
            )
        }
    }
}
