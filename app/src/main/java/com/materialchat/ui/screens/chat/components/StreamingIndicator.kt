package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * M3 Expressive Streaming Indicator with shape morphing.
 *
 * Uses the authentic M3 Expressive shape-morphing animation that transforms
 * between soft-burst (star/flower) and polygon shapes with a container background.
 */
@Composable
fun StreamingIndicator(
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    // Use the M3 shape morphing indicator for streaming
    M3StreamingShapeIndicator(
        modifier = modifier,
        color = color,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        size = 24.dp
    )
}

/**
 * M3 Expressive Shape Morphing Streaming Indicator.
 *
 * A compact version of the M3 loading indicator with shape morphing,
 * perfect for inline use during AI response streaming.
 */
@Composable
fun M3StreamingShapeIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streamingTransition")

    // Rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Shape morph progress: soft-burst → pentagon → hexagon → soft-burst
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "shapeMorph"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Container circle background
        Box(
            modifier = Modifier
                .size(size)
                .background(containerColor, CircleShape)
        )

        // Morphing shape
        Canvas(modifier = Modifier.size(size * 0.55f)) {
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val radius = minOf(this.size.width, this.size.height) / 2

            rotate(rotation) {
                val (points, innerRadiusRatio) = when {
                    morphProgress < 1f -> {
                        val t = morphProgress
                        Pair(lerp(10f, 5f, t), lerp(0.55f, 1f, t))
                    }
                    morphProgress < 2f -> {
                        val t = morphProgress - 1f
                        Pair(lerp(5f, 6f, t), 1f)
                    }
                    else -> {
                        val t = morphProgress - 2f
                        Pair(lerp(6f, 10f, t), lerp(1f, 0.55f, t))
                    }
                }

                drawMorphingShape(
                    centerX = centerX,
                    centerY = centerY,
                    outerRadius = radius,
                    innerRadiusRatio = innerRadiusRatio,
                    points = points.toInt().coerceAtLeast(3),
                    color = color
                )
            }
        }
    }
}

/**
 * M3 Expressive Streaming Dots with spring-based animation.
 * Alternative indicator using pulsing dots.
 */
@Composable
fun M3StreamingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 8.dp,
    dotSpacing: Dp = 6.dp
) {
    val dots = remember { listOf(Animatable(0.3f), Animatable(0.3f), Animatable(0.3f)) }

    LaunchedEffect(Unit) {
        while (true) {
            dots.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 120L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                    animatable.animateTo(
                        targetValue = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
            delay(800L)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEach { animatable ->
            val value = animatable.value
            Canvas(modifier = Modifier.size(dotSize)) {
                drawCircle(
                    color = color.copy(alpha = 0.4f + value * 0.6f),
                    radius = (size.minDimension / 2) * (0.5f + value * 0.5f)
                )
            }
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun DrawScope.drawMorphingShape(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadiusRatio: Float,
    points: Int,
    color: Color
) {
    val path = Path()
    val innerRadius = outerRadius * innerRadiusRatio

    val isStarShape = innerRadiusRatio < 0.99f
    val totalPoints = if (isStarShape) points * 2 else points
    val angleStep = (2 * PI / totalPoints).toFloat()

    // Calculate all vertex positions
    val vertices = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until totalPoints) {
        val angle = (i * angleStep) - (PI / 2).toFloat()
        val r = if (isStarShape && i % 2 == 1) innerRadius else outerRadius
        val x = centerX + r * cos(angle)
        val y = centerY + r * sin(angle)
        vertices.add(Pair(x, y))
    }

    // M3 Expressive: Use high smoothing for soft, rounded corners
    // Higher values = rounder, softer shapes (0.75-0.85 is ideal for M3)
    val smoothing = if (isStarShape) 0.75f else 0.8f

    // Calculate midpoints for smooth curve starting
    val firstMidX = (vertices[0].first + vertices[1].first) / 2
    val firstMidY = (vertices[0].second + vertices[1].second) / 2
    path.moveTo(firstMidX, firstMidY)

    // Draw smooth curves using cubic bezier for M3 Expressive soft curves
    for (i in vertices.indices) {
        val current = vertices[(i + 1) % vertices.size]
        val next = vertices[(i + 2) % vertices.size]

        // Current midpoint (where we are)
        val currentMidX = (vertices[i].first + current.first) / 2
        val currentMidY = (vertices[i].second + current.second) / 2

        // Next midpoint (where we're going)
        val nextMidX = (current.first + next.first) / 2
        val nextMidY = (current.second + next.second) / 2

        // Control points - lerp between midpoint and vertex for smoothness
        val cp1x = lerp(currentMidX, current.first, smoothing)
        val cp1y = lerp(currentMidY, current.second, smoothing)
        val cp2x = lerp(nextMidX, current.first, smoothing)
        val cp2y = lerp(nextMidY, current.second, smoothing)

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, nextMidX, nextMidY)
    }

    path.close()
    drawPath(path = path, color = color)
}

/**
 * Pulsing streaming indicator - alternative with dots.
 */
@Composable
fun PulsingStreamingIndicator(
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    M3StreamingDots(
        modifier = modifier,
        color = color,
        dotSize = dotSize,
        dotSpacing = dotSpacing
    )
}

/**
 * Typing indicator - THREE M3 shape-morphing indicators in a row.
 * Each shape morphs with a staggered animation for a wave effect.
 */
@Composable
fun TypingIndicator(
    dotSize: Dp = 8.dp,
    dotSpacing: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    M3TripleShapeIndicator(
        modifier = modifier,
        color = color,
        containerColor = color.copy(alpha = 0.15f),
        shapeSize = 16.dp,
        spacing = 4.dp
    )
}

/**
 * Three M3 Expressive shape-morphing indicators in a row.
 * Each shape has a staggered animation phase for a beautiful wave effect.
 */
@Composable
fun M3TripleShapeIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    shapeSize: Dp = 20.dp,
    spacing: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tripleShapeTransition")

    // Shared rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Three staggered morph animations
    val morphProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "shapeMorph1"
    )

    val morphProgress2 by infiniteTransition.animateFloat(
        initialValue = 1f,  // Offset by 1/3 of cycle
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "shapeMorph2"
    )

    val morphProgress3 by infiniteTransition.animateFloat(
        initialValue = 2f,  // Offset by 2/3 of cycle
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "shapeMorph3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Three shape indicators with staggered animations
        listOf(morphProgress1, morphProgress2, morphProgress3).forEach { progress ->
            val normalizedProgress = progress % 3f  // Keep in 0-3 range

            Box(
                modifier = Modifier.size(shapeSize),
                contentAlignment = Alignment.Center
            ) {
                // Container circle
                Box(
                    modifier = Modifier
                        .size(shapeSize)
                        .background(containerColor, CircleShape)
                )

                // Morphing shape
                Canvas(modifier = Modifier.size(shapeSize * 0.55f)) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = minOf(size.width, size.height) / 2

                    rotate(rotation) {
                        val (points, innerRadiusRatio) = when {
                            normalizedProgress < 1f -> {
                                val t = normalizedProgress
                                Pair(lerp(10f, 5f, t), lerp(0.55f, 1f, t))
                            }
                            normalizedProgress < 2f -> {
                                val t = normalizedProgress - 1f
                                Pair(lerp(5f, 6f, t), 1f)
                            }
                            else -> {
                                val t = normalizedProgress - 2f
                                Pair(lerp(6f, 10f, t), lerp(1f, 0.55f, t))
                            }
                        }

                        drawMorphingShape(
                            centerX = centerX,
                            centerY = centerY,
                            outerRadius = radius,
                            innerRadiusRatio = innerRadiusRatio,
                            points = points.toInt().coerceAtLeast(3),
                            color = color
                        )
                    }
                }
            }
        }
    }
}
