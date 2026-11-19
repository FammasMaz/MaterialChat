package com.materialchat.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.ExpressiveSizes

/**
 * The chip class as one family (W8 part 3; W6 audit F4): `ExpressiveFilterChip`,
 * `ExpressiveAssistChip` and `ExpressiveSuggestionChip` wrap the M3 chips so every site gets
 * the same capsule ([ExpressiveShapes.Chip]), the same height ([ExpressiveSizes.Chip.Height],
 * 40dp, not the M3 32dp default), the same press morph (capsule -> [PressedRadius] on
 * `ExpressiveMotion.Press.Shape`, scale dip on `Press.Scale`) and the click haptic every
 * `ExpressiveButton` fires. Colours, borders and elevation stay the M3 defaults unless a site
 * passes its own, so a migrated site changes only height and press feel.
 *
 * Pure values so `ExpressiveChipsTest` can pin them without a composition.
 */
object ExpressiveChipDefaults {
    /** Row height of every chip: [ExpressiveSizes.Chip.Height]. */
    val Height: Dp get() = ExpressiveSizes.Chip.Height

    /** Resting corner: half the height, so the animated corner at rest *is* the [ExpressiveShapes.Chip] capsule. */
    val RestingRadius: Dp get() = Height / 2

    /** Pressed corner: the same 12dp the chat memory chip and the assistant chip press to. */
    val PressedRadius: Dp = 12.dp

    /** Press scale dip, the `ExpressiveButton` value. */
    const val PressedScale = 0.95f

    /** Leading / trailing glyph size: [ExpressiveSizes.Chip.Icon]. */
    val IconSize: Dp get() = ExpressiveSizes.Chip.Icon
}

/**
 * The shared press state of the three chips: scale on `Press.Scale`, corner radius on
 * `Press.Shape`, and the resting shape is the chip capsule token itself (not an equal-looking
 * copy) so a `shape ==` check against [ExpressiveShapes.Chip] holds while the chip is at rest.
 */
@Composable
private fun rememberChipPress(interactionSource: MutableInteractionSource): Pair<Float, Shape> {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) ExpressiveChipDefaults.PressedScale else 1f,
        animationSpec = ExpressiveMotion.Press.Scale,
        label = "chipScale"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) ExpressiveChipDefaults.PressedRadius else ExpressiveChipDefaults.RestingRadius,
        animationSpec = ExpressiveMotion.Press.Shape,
        label = "chipCornerRadius"
    )
    val shape: Shape = if (cornerRadius == ExpressiveChipDefaults.RestingRadius) ExpressiveShapes.Chip else RoundedCornerShape(cornerRadius)
    return scale to shape
}

/**
 * M3 [FilterChip] at the chip class: 40dp capsule, press morph, click haptic. Same parameters
 * as the M3 chip minus `shape` (the family fixes it).
 */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected)
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val clickWithHaptics = remember(haptics, onClick) {
        { haptics.perform(HapticPattern.CLICK); onClick() }
    }
    val (scale, shape) = rememberChipPress(interactionSource)
    FilterChip(
        selected = selected,
        onClick = clickWithHaptics,
        label = label,
        modifier = modifier.height(ExpressiveChipDefaults.Height).scale(scale),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = interactionSource
    )
}

/**
 * M3 [AssistChip] at the chip class: 40dp capsule, press morph, click haptic.
 */
@Composable
fun ExpressiveAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled)
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val clickWithHaptics = remember(haptics, onClick) {
        { haptics.perform(HapticPattern.CLICK); onClick() }
    }
    val (scale, shape) = rememberChipPress(interactionSource)
    AssistChip(
        onClick = clickWithHaptics,
        label = label,
        modifier = modifier.height(ExpressiveChipDefaults.Height).scale(scale),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = interactionSource
    )
}

/**
 * M3 [SuggestionChip] at the chip class: 40dp capsule, press morph, click haptic.
 */
@Composable
fun ExpressiveSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled)
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val clickWithHaptics = remember(haptics, onClick) {
        { haptics.perform(HapticPattern.CLICK); onClick() }
    }
    val (scale, shape) = rememberChipPress(interactionSource)
    SuggestionChip(
        onClick = clickWithHaptics,
        label = label,
        modifier = modifier.height(ExpressiveChipDefaults.Height).scale(scale),
        enabled = enabled,
        icon = icon,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = interactionSource
    )
}
