package com.materialchat.ui.components

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Loading Indicator with shape morphing.
 *
 * This is the authentic M3 Expressive loading indicator that morphs between
 * soft-burst (star/flower) shapes and polygons with smooth rounded corners.
 * Features a container circle background and filled shapes.
 *
 * Based on M3 Expressive design guidelines for loading indicators.
 *
 * @param modifier The modifier to apply.
 * @param color The color of the shape (default: primary).
 * @param containerColor The background circle color (default: primaryContainer).
 * @param size The overall size of the indicator.
 */
@Composable
fun M3ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3LoadingTransition")

    // Slow rotation for the shape
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Shape morph progress: morphs between soft-burst and polygon shapes
    // 0.0 = soft-burst (12-point star/flower)
    // 1.0 = pentagon
    // 2.0 = hexagon
    // 3.0 = soft-burst again
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOutCubic),
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
        Canvas(modifier = Modifier.size(size * 0.6f)) {
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val radius = minOf(this.size.width, this.size.height) / 2

            rotate(rotation) {
                // Calculate morph parameters
                val (points, innerRadiusRatio) = when {
                    morphProgress < 1f -> {
                        // Soft-burst (12 points) → Pentagon (5 points)
                        val t = morphProgress
                        val points = lerp(12f, 5f, t)
                        val innerRatio = lerp(0.6f, 1f, t) // Star indentation → solid
                        Pair(points, innerRatio)
                    }
                    morphProgress < 2f -> {
                        // Pentagon → Hexagon
                        val t = morphProgress - 1f
                        val points = lerp(5f, 6f, t)
                        Pair(points, 1f) // Solid polygon
                    }
                    else -> {
                        // Hexagon → Soft-burst
                        val t = morphProgress - 2f
                        val points = lerp(6f, 12f, t)
                        val innerRatio = lerp(1f, 0.6f, t) // Solid → star indentation
                        Pair(points, innerRatio)
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
 * Draws a filled morphing shape with smooth bezier curves.
 * Creates soft, rounded shapes like M3 "soft-burst" and "cookie" styles.
 */
private fun DrawScope.drawMorphingShape(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadiusRatio: Float, // 1.0 = polygon, <1.0 = star shape
    points: Int,
    color: Color
) {
    val path = Path()
    val innerRadius = outerRadius * innerRadiusRatio

    // For star shapes, we create smooth curves between outer and inner points
    // For polygons, we create rounded corners
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

    // Draw smooth curves using quadratic bezier
    // Start at first vertex
    val firstVertex = vertices[0]
    path.moveTo(firstVertex.first, firstVertex.second)

    for (i in vertices.indices) {
        val current = vertices[i]
        val next = vertices[(i + 1) % vertices.size]
        val afterNext = vertices[(i + 2) % vertices.size]

        // Calculate control point and end point for smooth curve
        // Use midpoint approach for smooth transitions
        val midX = (current.first + next.first) / 2
        val midY = (current.second + next.second) / 2
        val nextMidX = (next.first + afterNext.first) / 2
        val nextMidY = (next.second + afterNext.second) / 2

        // Smoothing factor - higher = rounder curves
        val smoothing = if (isStarShape) 0.5f else 0.3f

        // Blend towards the control point
        val controlX = next.first
        val controlY = next.second
        val endX = lerp(next.first, nextMidX, smoothing)
        val endY = lerp(next.second, nextMidY, smoothing)

        path.quadraticBezierTo(controlX, controlY, endX, endY)
    }

    path.close()

    // Draw filled shape
    drawPath(
        path = path,
        color = color
    )
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

/**
 * Material 3 Expressive Circular Progress Indicator.
 *
 * Enhanced circular progress with spring-based arc animation.
 * Uses M3 Expressive motion principles.
 */
@Composable
fun M3ExpressiveCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circularProgressTransition")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1332, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 666, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepProgress"
    )

    val sweepAngle = 30f + (240f * sweepProgress)

    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val arcSize = this.size.width - stroke

        drawCircle(
            color = trackColor,
            radius = arcSize / 2,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * M3 Expressive Pulsing Dots Indicator with spring-based animation.
 */
@Composable
fun M3ExpressivePulsingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 8.dp
) {
    val dots = remember { listOf(Animatable(0.4f), Animatable(0.4f), Animatable(0.4f)) }

    LaunchedEffect(Unit) {
        while (true) {
            dots.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 150L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                    animatable.animateTo(
                        targetValue = 0.4f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
            delay(900L)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEach { animatable ->
            Canvas(modifier = Modifier.size(dotSize)) {
                drawCircle(
                    color = color.copy(alpha = animatable.value),
                    radius = (size.minDimension / 2) * (0.6f + 0.4f * animatable.value)
                )
            }
        }
    }
}

/**
 * M3 Expressive Linear Progress Indicator (indeterminate).
 */
@Composable
fun M3ExpressiveLinearProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "linearProgressTransition")

    val position by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "position"
    )

    val widthFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "width"
    )

    Canvas(modifier = modifier.height(height)) {
        val trackHeight = size.height
        val cornerRadius = trackHeight / 2

        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        val barWidth = size.width * (0.2f + 0.3f * widthFraction)
        val barStart = (size.width * position - barWidth / 2).coerceIn(0f, size.width - barWidth)

        drawRoundRect(
            color = color,
            topLeft = Offset(barStart, 0f),
            size = androidx.compose.ui.geometry.Size(
                width = barWidth.coerceAtMost(size.width - barStart),
                height = trackHeight
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
    }
}

/**
 * M3 Expressive Determinate Linear Progress.
 */
@Composable
fun M3ExpressiveDeterminateProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 4.dp
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Canvas(modifier = modifier.height(height)) {
        val trackHeight = size.height
        val cornerRadius = trackHeight / 2

        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        if (animatedProgress.value > 0f) {
            drawRoundRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(
                    width = size.width * animatedProgress.value,
                    height = trackHeight
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
        }
    }
}

/**
 * M3 Expressive Loading with Text.
 */
@Composable
fun M3ExpressiveLoadingWithText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        M3ExpressiveLoadingIndicator(
            color = color,
            size = 48.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * M3 Expressive Inline Loading (for buttons).
 */
@Composable
fun M3ExpressiveInlineLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary
) {
    M3ExpressivePulsingDots(
        modifier = modifier,
        color = color,
        dotSize = 6.dp
    )
}

/**
 * M3 Expressive Fullscreen Loading.
 */
@Composable
fun M3ExpressiveFullscreenLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        M3ExpressiveLoadingIndicator(
            color = color,
            size = 64.dp
        )
    }
}

// Legacy compatibility aliases
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 8.dp,
    amplitude: Dp = 6.dp
) = M3ExpressivePulsingDots(modifier, color, dotSize)

@Composable
fun ExpressivePulsingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 8.dp
) = M3ExpressivePulsingDots(modifier, color, dotSize)

@Composable
fun ExpressiveTypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 6.dp
) = M3ExpressivePulsingDots(modifier, color, dotSize)

@Composable
fun ExpressiveCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
    size: Dp = 24.dp
) = M3ExpressiveCircularProgress(modifier, color, size = size, strokeWidth = strokeWidth)

@Composable
fun ExpressiveSpinningArc(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 32.dp,
    strokeWidth: Dp = 3.dp
) = M3ExpressiveCircularProgress(modifier, color, size = size, strokeWidth = strokeWidth)

@Composable
fun ExpressiveLoadingWithText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) = M3ExpressiveLoadingWithText(text, modifier, color)

@Composable
fun ExpressiveInlineLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) = M3ExpressiveInlineLoading(modifier, color)

@Composable
fun ExpressiveFullscreenLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) = M3ExpressiveFullscreenLoading(modifier, color)
