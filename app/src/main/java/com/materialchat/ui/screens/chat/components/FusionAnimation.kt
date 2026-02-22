package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated canvas composable showing 3 circles converging toward the center.
 *
 * Used as a visual indicator during the fusion synthesis phase. Three colored
 * circles orbit outward initially, then converge to the center using spring
 * animation, creating a merge/fusion visual effect.
 *
 * @param isActive Whether the animation is currently running
 * @param modifier Modifier for the canvas
 */
@Composable
fun FusionAnimation(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val convergence = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            // Start rotation
            launch {
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
            // Converge circles toward center
            launch {
                convergence.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessVeryLow
                    )
                )
            }
        } else {
            // Reset
            launch { convergence.snapTo(0f) }
            launch { rotation.snapTo(0f) }
        }
    }

    Canvas(modifier = modifier.size(48.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.minDimension / 3f
        val circleRadius = size.minDimension / 10f

        // Current orbit radius shrinks as convergence increases
        val orbitRadius = maxRadius * (1f - convergence.value * 0.8f)
        val rotationRad = Math.toRadians(rotation.value.toDouble())

        val colors = listOf(primaryColor, secondaryColor, tertiaryColor)
        val angleOffsets = listOf(0.0, 2.0943951, 4.1887902) // 120 degrees apart

        colors.forEachIndexed { index, color ->
            val angle = rotationRad + angleOffsets[index]
            val x = centerX + orbitRadius * cos(angle).toFloat()
            val y = centerY + orbitRadius * sin(angle).toFloat()

            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = circleRadius,
                center = Offset(x, y)
            )
        }

        // Center dot that grows as circles converge
        val centerDotRadius = circleRadius * convergence.value * 1.2f
        if (centerDotRadius > 0f) {
            drawCircle(
                color = primaryColor.copy(alpha = convergence.value * 0.6f),
                radius = centerDotRadius,
                center = Offset(centerX, centerY)
            )
        }
    }
}
