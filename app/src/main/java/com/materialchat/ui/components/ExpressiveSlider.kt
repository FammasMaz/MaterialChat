package com.materialchat.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.seaglass.Slider
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive slider for settings screens.
 *
 * Keeps the 1.5 wide track with its stop indicator and track/thumb gap, and adds
 * the expressive handle feedback: while pressed or dragged the handle grows from
 * [ThumbRestingHeight] to [ThumbActiveHeight] on the theme's shape-morph spring
 * (the library already narrows the handle width on press). Every parameter maps
 * one-to-one onto [Slider], so call sites keep their value ranges, steps and
 * per-stop haptics.
 *
 * @param value Current value.
 * @param onValueChange Invoked while the user drags.
 * @param modifier Modifier for the slider.
 * @param enabled Whether the slider accepts input.
 * @param valueRange Range of allowed values.
 * @param steps Discrete stops between the range ends; 0 for continuous.
 * @param onValueChangeFinished Invoked when the drag ends.
 * @param colors Slider colours; defaults to primary on surfaceContainerHighest.
 * @param interactionSource Interaction source shared with the handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = isDragged || isPressed
    val thumbHeight by animateDpAsState(
        targetValue = if (active) ThumbActiveHeight else ThumbRestingHeight,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "sliderThumbHeight"
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        steps = steps,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(ThumbWidth, thumbHeight)
            )
        },
        valueRange = valueRange
    )
}

/** M3 slider handle width at rest; the library narrows it while pressed. */
internal val ThumbWidth: Dp = 4.dp

/** Handle height while idle (M3 slider handle). */
internal val ThumbRestingHeight: Dp = 44.dp

/** Handle height while pressed or dragged (M3E emphasised handle). */
internal val ThumbActiveHeight: Dp = 52.dp
