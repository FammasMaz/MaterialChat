package com.materialchat.assistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * M3 Expressive voice waveform visualization component.
 *
 * Displays animated bars that respond to audio amplitude data,
 * creating a lively voice visualization effect.
 *
 * Uses M3 Expressive spring physics for playful, bouncy bar animations.
 *
 * @param amplitudeData Audio amplitude data from speech recognition
 * @param modifier Modifier for the waveform container
 * @param barColor Color for the amplitude bars
 * @param barWidth Width of each bar
 * @param barSpacing Spacing between bars
 * @param minBarHeight Minimum height for bars (even at 0 amplitude)
 * @param maxBarHeight Maximum bar height
 */
@Composable
fun VoiceWaveform(
    amplitudeData: AudioAmplitudeData,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp,
    minBarHeight: Dp = 8.dp,
    maxBarHeight: Dp = 48.dp,
    barCount: Int = 12
) {
    val barAnimatables = remember {
        List(barCount) { Animatable(0.1f) }
    }

    // Animate bars based on amplitude data
    LaunchedEffect(amplitudeData) {
        amplitudeData.amplitudes.forEachIndexed { index, amplitude ->
            if (index < barAnimatables.size) {
                launch {
                    barAnimatables[index].animateTo(
                        targetValue = amplitude,
                        animationSpec = ExpressiveMotion.Spatial.playful()
                    )
                }
            }
        }
    }

    val totalWidth = (barWidth * barCount) + (barSpacing * (barCount - 1))

    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(maxBarHeight)
    ) {
        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()
        val minHeightPx = minBarHeight.toPx()
        val maxHeightPx = maxBarHeight.toPx()
        val cornerRadiusPx = barWidthPx / 2

        barAnimatables.forEachIndexed { index, animatable ->
            val amplitude = animatable.value
            val barHeight = minHeightPx + (maxHeightPx - minHeightPx) * amplitude

            val x = index * (barWidthPx + barSpacingPx)
            val y = (maxHeightPx - barHeight) / 2

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }
    }
}

/**
 * Idle waveform animation when not actively listening.
 * Shows gentle oscillating bars.
 */
@Composable
fun IdleWaveform(
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp,
    minBarHeight: Dp = 8.dp,
    maxBarHeight: Dp = 24.dp,
    barCount: Int = 12
) {
    val infiniteTransition = rememberInfiniteTransition(label = "idleWaveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformPhase"
    )

    val totalWidth = (barWidth * barCount) + (barSpacing * (barCount - 1))

    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(maxBarHeight)
    ) {
        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()
        val minHeightPx = minBarHeight.toPx()
        val maxHeightPx = maxBarHeight.toPx()
        val cornerRadiusPx = barWidthPx / 2

        for (index in 0 until barCount) {
            // Create wave pattern with phase offset per bar
            val waveOffset = index * 0.5f
            val amplitude = 0.3f + 0.2f * sin(phase + waveOffset)
            val barHeight = minHeightPx + (maxHeightPx - minHeightPx) * amplitude

            val x = index * (barWidthPx + barSpacingPx)
            val y = (maxHeightPx - barHeight) / 2

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }
    }
}

/**
 * Processing waveform - shows pulsing animation while speech is being processed.
 */
@Composable
fun ProcessingWaveform(
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp,
    barHeight: Dp = 16.dp,
    barCount: Int = 5
) {
    val animatables = remember {
        List(barCount) { Animatable(0.4f) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            animatables.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 100L)
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
            delay(800L)
        }
    }

    val totalWidth = (barWidth * barCount) + (barSpacing * (barCount - 1))

    Row(
        modifier = modifier.width(totalWidth),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatables.forEach { animatable ->
            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
            ) {
                val scale = animatable.value
                val actualHeight = size.height * scale
                val y = (size.height - actualHeight) / 2

                drawRoundRect(
                    color = barColor.copy(alpha = 0.6f + 0.4f * scale),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, actualHeight),
                    cornerRadius = CornerRadius(size.width / 2, size.width / 2)
                )
            }
        }
    }
}
