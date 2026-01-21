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
import kotlin.math.sin

/**
 * Animated streaming indicator showing that a response is being generated.
 * 
 * Uses a smooth wave animation with staggered dots for a Google Messages-like effect.
 *
 * @param dotSize Size of each dot
 * @param dotSpacing Space between dots
 * @param color Color of the dots
 * @param modifier Modifier for the indicator container
 */
@Composable
fun StreamingIndicator(
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming_dots")
    
    // Smooth continuous phase animation (0 to 2π)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            // Each dot offset by 1/3 of the cycle for wave effect
            val dotPhase = phase + (index * 2.0 * Math.PI / 3.0).toFloat()
            
            // Full sine wave for continuous smooth motion, shifted up
            val bounceY = sin(dotPhase.toDouble()).toFloat() * -4f - 10f
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(y = bounceY.dp)
                    .background(
                        color = color.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            )
        }
    }
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
