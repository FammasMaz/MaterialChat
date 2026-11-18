package com.materialchat.assistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.launch

/**
 * Spring for one waveform bar moving from [current] to [target]: expressive overshoot on the way
 * up (the spatial playful spring) and a critically damped settle on the way down, so a loud
 * syllable snaps in and the decay never re-rises past where it is heading.
 */
internal fun waveformBarSpec(current: Float, target: Float): SpringSpec<Float> =
    if (target >= current) ExpressiveMotion.Spatial.playful() else ExpressiveMotion.Spatial.collapse()

/**
 * The pill's capturing glyph: bars that follow the recognizer's amplitude samples on the shared
 * spatial springs. The only waveform the assistant draws; the idle and processing variants that
 * once lived here belonged to the retired input bar and had their own hand-rolled timing.
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
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp,
    minBarHeight: Dp = 8.dp,
    maxBarHeight: Dp = 48.dp,
    barCount: Int = 12
) {
    val barAnimatables = remember {
        List(barCount) { Animatable(0.1f) }
    }

    // Animate bars based on amplitude data (emissions are throttled in SpeechRecognitionManager)
    LaunchedEffect(amplitudeData.amplitudes) {
        amplitudeData.amplitudes.forEachIndexed { index, amplitude ->
            if (index < barAnimatables.size) {
                launch {
                    val bar = barAnimatables[index]
                    bar.animateTo(
                        targetValue = amplitude,
                        animationSpec = waveformBarSpec(current = bar.value, target = amplitude)
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
