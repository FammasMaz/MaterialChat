package com.materialchat.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.MaterialChatMotion
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material 3 Expressive wavy loading indicator.
 *
 * This indicator shows three bouncing dots in a wave pattern,
 * following Material 3 Expressive motion guidelines.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the dots.
 * @param dotSize The diameter of each dot.
 * @param amplitude The maximum vertical offset of the wave.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 8.dp,
    amplitude: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingTransition")

    // Create phase animation that cycles from 0 to 2*PI
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MaterialChatMotion.Durations.StreamingIndicator,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            // Each dot has a phase offset of 100ms (represented as phase offset)
            val dotPhaseOffset = index * (2 * PI / 3).toFloat()
            val yOffset = sin(phase + dotPhaseOffset) * amplitude.value

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, -yOffset.roundToInt()) }
                    .size(dotSize)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Pulsing loading indicator with scale animation.
 *
 * This variant shows dots that pulse in scale for a different visual effect.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the dots.
 * @param dotSize The base size of each dot.
 */
@Composable
fun ExpressivePulsingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingTransition")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            // Stagger the animation for each dot with delay offset
            val delayMillis = index * MaterialChatMotion.Durations.StreamingDotDelay

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delayMillis,
                        easing = EaseInOutSine
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotScale$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delayMillis,
                        easing = EaseInOutSine
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha$index"
            )

            Box(
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha)
                    .size(dotSize)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Typing indicator showing animated ellipsis effect.
 *
 * This simulates a "typing..." effect with sequential dot appearance.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the dots.
 * @param dotSize The size of each dot.
 */
@Composable
fun ExpressiveTypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingTransition")

    // Single animation that controls the sequence
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "typingProgress"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            // Calculate visibility based on progress
            val isVisible = progress > index && progress < index + 2

            val alpha = if (isVisible) {
                // Fade in and out smoothly
                val localProgress = progress - index
                when {
                    localProgress < 0.5f -> localProgress * 2 // Fade in
                    localProgress > 1.5f -> (2f - localProgress) * 2 // Fade out
                    else -> 1f // Full visibility
                }.coerceIn(0f, 1f)
            } else {
                0.2f
            }

            Box(
                modifier = Modifier
                    .alpha(alpha)
                    .size(dotSize)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Circular progress indicator with expressive spring animation.
 *
 * This is a styled version of CircularProgressIndicator for consistent design.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the progress track.
 * @param strokeWidth The width of the progress stroke.
 * @param size The overall size of the indicator.
 */
@Composable
fun ExpressiveCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
    size: Dp = 24.dp
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round
    )
}

/**
 * Spinning arc loading indicator with smooth animation.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the arc.
 * @param size The size of the indicator.
 * @param strokeWidth The width of the arc stroke.
 */
@Composable
fun ExpressiveSpinningArc(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 32.dp,
    strokeWidth: Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinningArcTransition")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = modifier.size(size)) {
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Loading indicator with accompanying text.
 *
 * @param text The loading text to display.
 * @param modifier The modifier to apply.
 * @param color The color for both indicator and text.
 */
@Composable
fun ExpressiveLoadingWithText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveLoadingIndicator(color = color)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = 0.8f)
        )
    }
}

/**
 * Inline loading indicator for use within text or buttons.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the dots.
 */
@Composable
fun ExpressiveInlineLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    ExpressiveLoadingIndicator(
        modifier = modifier,
        color = color,
        dotSize = 4.dp,
        amplitude = 3.dp
    )
}

/**
 * Large full-screen loading overlay indicator.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the indicator.
 */
@Composable
fun ExpressiveFullscreenLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ExpressiveSpinningArc(
            color = color,
            size = 48.dp,
            strokeWidth = 4.dp
        )
    }
}
