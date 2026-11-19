package com.materialchat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatMotion
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import com.materialchat.ui.theme.ExpressiveSizes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Indicator cycle lengths come from MaterialChatMotion.Durations.Indicator; every loop
// below runs behind rememberParticleMotionGate and holds a static frame when gated.

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
    size: Dp = ExpressiveSizes.IndicatorHero
) {
    // Continuous loop: only requests frames while the host motion gate allows it (window
    // started, no power saver, animator scale > 0); otherwise a static soft-burst frame.
    val infiniteTransition = if (rememberParticleMotionGate().motionAllowed) {
        rememberInfiniteTransition(label = "m3LoadingTransition")
    } else {
        null
    }

    // Slow rotation for the shape
    val rotation = infiniteTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = MaterialChatMotion.Tweens.linear(MaterialChatMotion.Durations.Indicator.MorphRotation),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )?.value ?: 0f

    // Shape morph progress: morphs between soft-burst and polygon shapes
    // 0.0 = soft-burst (12-point star/flower)
    // 1.0 = pentagon
    // 2.0 = hexagon
    // 3.0 = soft-burst again
    val morphProgress = infiniteTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = MaterialChatMotion.Tweens.easeInOut(MaterialChatMotion.Durations.Indicator.MorphShape),
            repeatMode = RepeatMode.Restart
        ),
        label = "shapeMorph"
    )?.value ?: 0f

    // Reusable scratch buffers for the morphing-shape draw path. The indicator
    // animates continuously at 60fps while visible (every AI response), so
    // avoiding a Path + vertex list allocation per frame matters.
    val shapePath = remember { Path() }
    val vertexX = remember { FloatArray(MAX_MORPH_VERTICES) }
    val vertexY = remember { FloatArray(MAX_MORPH_VERTICES) }

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
                    path = shapePath,
                    scratchX = vertexX,
                    scratchY = vertexY,
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
 *
 * The caller supplies a reusable [path] and [scratchX]/[scratchY] buffers so no
 * Path or vertex container is allocated per frame.
 */
private fun DrawScope.drawMorphingShape(
    path: Path,
    scratchX: FloatArray,
    scratchY: FloatArray,
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadiusRatio: Float, // 1.0 = polygon, <1.0 = star shape
    points: Int,
    color: Color
) {
    path.reset()
    val innerRadius = outerRadius * innerRadiusRatio

    // For star shapes, we create smooth curves between outer and inner points
    // For polygons, we create rounded corners
    val isStarShape = innerRadiusRatio < 0.99f
    val totalPoints = if (isStarShape) points * 2 else points
    val angleStep = (2 * PI / totalPoints).toFloat()

    // Calculate all vertex positions into preallocated scratch buffers (no boxing).
    for (i in 0 until totalPoints) {
        val angle = (i * angleStep) - (PI / 2).toFloat()
        val r = if (isStarShape && i % 2 == 1) innerRadius else outerRadius
        scratchX[i] = centerX + r * cos(angle)
        scratchY[i] = centerY + r * sin(angle)
    }

    // M3 Expressive: Use high smoothing for soft, rounded corners
    // Higher values = rounder, softer shapes (0.75-0.85 is ideal for M3)
    val smoothing = if (isStarShape) 0.75f else 0.8f

    // Calculate midpoints for smooth curve starting
    val firstMidX = (scratchX[0] + scratchX[1]) / 2
    val firstMidY = (scratchY[0] + scratchY[1]) / 2
    path.moveTo(firstMidX, firstMidY)

    // Draw smooth curves using cubic bezier for M3 Expressive soft curves
    for (i in 0 until totalPoints) {
        val curX = scratchX[(i + 1) % totalPoints]
        val curY = scratchY[(i + 1) % totalPoints]
        val nextX = scratchX[(i + 2) % totalPoints]
        val nextY = scratchY[(i + 2) % totalPoints]

        // Current midpoint (where we are)
        val currentMidX = (scratchX[i] + curX) / 2
        val currentMidY = (scratchY[i] + curY) / 2

        // Next midpoint (where we're going)
        val nextMidX = (curX + nextX) / 2
        val nextMidY = (curY + nextY) / 2

        // Control points - lerp between midpoint and vertex for smoothness
        val cp1x = lerp(currentMidX, curX, smoothing)
        val cp1y = lerp(currentMidY, curY, smoothing)
        val cp2x = lerp(nextMidX, curX, smoothing)
        val cp2y = lerp(nextMidY, curY, smoothing)

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, nextMidX, nextMidY)
    }

    path.close()
    drawPath(path = path, color = color)
}

/**
 * Maximum vertex count the morphing shape can produce (12-point star → 24 vertices).
 */
private const val MAX_MORPH_VERTICES = 24

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

// W7 (audit F14): `M3ExpressiveCircularProgress` (the spinning arc) and
// `M3ExpressiveLinearProgress` (the indeterminate bar) had zero callers and were deleted, with
// the legacy alias block (`ExpressiveLoadingIndicator`, `ExpressivePulsingIndicator`,
// `ExpressiveTypingIndicator`, `ExpressiveCircularProgress`, `ExpressiveSpinningArc`,
// `ExpressiveLoadingWithText`, `ExpressiveInlineLoading`, `ExpressiveFullscreenLoading`), also
// zero callers. Indeterminate arcs are the stock M3 `LoadingIndicator` / `CircularWavyProgressIndicator`.
// W8 part 4: `M3ExpressiveDeterminateProgress`, `M3ExpressiveLoadingWithText`,
// `M3ExpressiveFullscreenLoading` and `MorphingSendButton` (with its private
// `calculateMorphParams` / `drawMorphingShapeFilled`) had zero callers in main, androidTest,
// scripts/ and .github/ (word-exact grep) and were deleted. Chat's send control is
// `ComposerSendButton`; a determinate bar is the stock M3 `LinearProgressIndicator`.

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
    val motionAllowed = rememberParticleMotionGate().motionAllowed

    // The pulse loop obeys the host motion gate: when motion is off the dots rest at their
    // dim state instead of looping. Rise on the playful spatial spring, settle on the slow one.
    LaunchedEffect(motionAllowed) {
        if (!motionAllowed) {
            dots.forEach { it.snapTo(0.4f) }
            return@LaunchedEffect
        }
        while (true) {
            dots.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 150L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = ExpressiveMotion.Spatial.playful()
                    )
                    animatable.animateTo(
                        targetValue = 0.4f,
                        animationSpec = ExpressiveMotion.Spatial.slow()
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
