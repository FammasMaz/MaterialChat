package com.materialchat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
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
 * Draws a filled morphing shape with smooth M3 Expressive cubic bezier curves.
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

/**
 * M3 Expressive Morphing Send Button.
 *
 * A send button that morphs into an animated loading indicator when streaming.
 * Follows Material 3 Expressive design guidelines with:
 * - Shape morphing animation (soft-burst → pentagon → hexagon → circle)
 * - Slow rotation during loading
 * - Container pulse for "breathing" effect
 * - Spring-based transitions between states
 * - Haptic feedback integration
 *
 * States:
 * - IDLE: Disabled, grayed out send icon
 * - READY: Enabled, colored send icon
 * - LOADING: Animated shape-morphing indicator
 * - STOP: Error container with close icon (during loading, tap to cancel)
 *
 * @param isStreaming Whether the AI is currently streaming a response
 * @param canSend Whether the send action is available
 * @param onSend Callback when send button is tapped
 * @param onCancel Callback when stop button is tapped during streaming
 * @param modifier Modifier for the button
 * @param size Size of the button (default 48.dp for M3 touch target)
 */
@Composable
fun MorphingSendButton(
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Container color animation (Effects spring - no overshoot)
    val containerColor = when {
        isStreaming -> MaterialTheme.colorScheme.primaryContainer
        canSend -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        isStreaming -> MaterialTheme.colorScheme.primary
        canSend -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // M3 Expressive spring-based alpha transitions
    val iconAlpha by animateFloatAsState(
        targetValue = if (isStreaming) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconAlpha"
    )

    val shapeAlpha by animateFloatAsState(
        targetValue = if (isStreaming) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "shapeAlpha"
    )

    // M3 Expressive scale animation for icon/shape transitions (spatial spring with bounce)
    val iconScale by animateFloatAsState(
        targetValue = if (isStreaming) 0.6f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "iconScale"
    )

    val shapeScale by animateFloatAsState(
        targetValue = if (isStreaming) 1f else 0.6f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "shapeScale"
    )

    // Press scale animation (Spatial spring - with bounce)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "pressScale"
    )

    // Container pulse animation during streaming
    val infiniteTransition = rememberInfiniteTransition(label = "morphingSendButtonTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Shape morph progress (0-4 for full cycle through shapes)
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphProgress"
    )

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

    // Accessibility description
    val semanticsDescription = when {
        isStreaming -> "Loading, tap to stop"
        canSend -> "Send message"
        else -> "Send message, disabled"
    }

    // Apply combined scale (press + pulse when streaming)
    val combinedScale = if (isStreaming) {
        pressScale * pulseScale
    } else {
        pressScale
    }

    Surface(
        onClick = {
            if (isStreaming) {
                onCancel()
            } else if (canSend) {
                onSend()
            }
        },
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .semantics { contentDescription = semanticsDescription },
        shape = CircleShape,
        color = containerColor,
        enabled = canSend || isStreaming,
        interactionSource = interactionSource
    ) {
        // Use Box with fixed size for consistent centering
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Both icon and shape use graphicsLayer for transforms - no layout changes
            // This ensures perfectly smooth M3 Expressive spring transitions

            // Morphing shape (visible when streaming)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = shapeAlpha
                        scaleX = shapeScale
                        scaleY = shapeScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = this.size.width / 2
                    val centerY = this.size.height / 2
                    val radius = minOf(this.size.width, this.size.height) / 2

                    rotate(rotation) {
                        val normalizedProgress = morphProgress % 4f
                        val (points, innerRadiusRatio) = calculateMorphParams(normalizedProgress)

                        drawMorphingShapeFilled(
                            centerX = centerX,
                            centerY = centerY,
                            outerRadius = radius,
                            innerRadiusRatio = innerRadiusRatio,
                            points = points.toInt().coerceAtLeast(3),
                            color = contentColor
                        )
                    }
                }

                // Small close icon overlay
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
            }

            // Send icon (visible when not streaming)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = iconAlpha
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
    }
}

/**
 * Calculate morph parameters for the shape animation.
 * Progress goes through: soft-burst → pentagon → hexagon → circle → soft-burst
 */
private fun calculateMorphParams(progress: Float): Pair<Float, Float> {
    return when {
        progress < 1f -> {
            // Soft-burst (10 points, star) → Pentagon (5 points, solid)
            val t = progress
            val points = lerp(10f, 5f, t)
            val innerRatio = lerp(0.55f, 1f, t)
            Pair(points, innerRatio)
        }
        progress < 2f -> {
            // Pentagon → Hexagon
            val t = progress - 1f
            val points = lerp(5f, 6f, t)
            Pair(points, 1f)
        }
        progress < 3f -> {
            // Hexagon → Circle (many points)
            val t = progress - 2f
            val points = lerp(6f, 24f, t)
            Pair(points, 1f)
        }
        else -> {
            // Circle → Soft-burst
            val t = progress - 3f
            val points = lerp(24f, 10f, t)
            val innerRatio = lerp(1f, 0.55f, t)
            Pair(points, innerRatio)
        }
    }
}

/**
 * Draws a filled morphing shape with smooth M3 Expressive cubic bezier curves.
 * Uses high smoothing factors for soft, rounded corners as per M3 design guidelines.
 */
private fun DrawScope.drawMorphingShapeFilled(
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
