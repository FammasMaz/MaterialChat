package com.materialchat.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether the neural-expressive glow is drawn around floating surfaces. */
val LocalNeuralExpressiveEnabled = staticCompositionLocalOf { true }

/**
 * Soft, blurred boundary light with slowly drifting dots, drawn around a floating surface.
 *
 * The halo is a blurred stroke rather than a stack of translucent rectangles, so it stays one
 * cheap draw call; the dots ride the rounded-rect perimeter and pulse as they travel, which reads
 * as a living edge without animating the surface itself.
 *
 * Purely decorative: turning it off removes every draw, so it can be switched from settings
 * without changing layout.
 */
@Composable
fun Modifier.neuralExpressiveGlow(
    enabled: Boolean = LocalNeuralExpressiveEnabled.current,
    cornerRadius: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 7
): Modifier {
    if (!enabled || dotCount <= 0) return this

    val transition = rememberInfiniteTransition(label = "neuralExpressive")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing)
        ),
        label = "neuralExpressiveProgress"
    )

    val density = LocalDensity.current
    val blurPx = with(density) { 10.dp.toPx() }
    val strokePx = with(density) { 1.4f.dp.toPx() }
    val dotRadiusPx = with(density) { 2.1f.dp.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    return this.drawBehind {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@drawBehind
        val radius = cornerPx.coerceAtMost(minOf(width, height) / 2f)
        val rect = android.graphics.RectF(0f, 0f, width, height)

        // Halo: a blurred stroke reads as light bleeding off the edge.
        paint.reset()
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = strokePx * 2.4f
        paint.color = color.toArgb()
        paint.alpha = (255 * 0.42f).toInt()
        paint.maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(rect, radius, radius, paint)
        }

        // A thin, unblurred edge keeps the boundary legible under the halo.
        paint.maskFilter = null
        paint.strokeWidth = strokePx
        paint.alpha = (255 * 0.30f).toInt()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(rect, radius, radius, paint)
        }

        // Dots travelling the perimeter, each pulsing on its own phase.
        val perimeter = perimeterOf(width, height, radius)
        if (perimeter <= 0f) return@drawBehind
        repeat(dotCount) { index ->
            val phase = (progress + index / dotCount.toFloat()) % 1f
            val point = pointOnRoundedRect(phase * perimeter, width, height, radius)
            val pulse = 0.5f + 0.5f * sin((phase * 2f * PI).toFloat())
            drawCircle(
                color = color.copy(alpha = 0.16f + 0.42f * pulse),
                radius = dotRadiusPx * (0.55f + 0.75f * pulse),
                center = point
            )
        }
    }
}

private fun perimeterOf(width: Float, height: Float, radius: Float): Float {
    val straight = (width - 2f * radius).coerceAtLeast(0f) + (height - 2f * radius).coerceAtLeast(0f)
    return 2f * straight + 2f * PI.toFloat() * radius
}

/**
 * Position at [distance] along a rounded rectangle's boundary, clockwise from the start of the
 * top edge. Straight runs and quarter arcs are walked in order so dots follow the actual outline
 * instead of an ellipse.
 */
private fun pointOnRoundedRect(distance: Float, width: Float, height: Float, radius: Float): Offset {
    val topRun = (width - 2f * radius).coerceAtLeast(0f)
    val sideRun = (height - 2f * radius).coerceAtLeast(0f)
    val arc = PI.toFloat() * radius / 2f
    var remaining = distance

    if (remaining <= topRun) return Offset(radius + remaining, 0f)
    remaining -= topRun

    if (remaining <= arc) {
        val angle = -PI.toFloat() / 2f + (remaining / arc) * (PI.toFloat() / 2f)
        return Offset(width - radius + radius * cos(angle), radius + radius * sin(angle))
    }
    remaining -= arc

    if (remaining <= sideRun) return Offset(width, radius + remaining)
    remaining -= sideRun

    if (remaining <= arc) {
        val angle = (remaining / arc) * (PI.toFloat() / 2f)
        return Offset(width - radius + radius * cos(angle), height - radius + radius * sin(angle))
    }
    remaining -= arc

    if (remaining <= topRun) return Offset(width - radius - remaining, height)
    remaining -= topRun

    if (remaining <= arc) {
        val angle = PI.toFloat() / 2f + (remaining / arc) * (PI.toFloat() / 2f)
        return Offset(radius + radius * cos(angle), height - radius + radius * sin(angle))
    }
    remaining -= arc

    if (remaining <= sideRun) return Offset(0f, height - radius - remaining)
    remaining -= sideRun

    val angle = PI.toFloat() + (remaining / arc).coerceAtMost(1f) * (PI.toFloat() / 2f)
    return Offset(radius + radius * cos(angle), radius + radius * sin(angle))
}
