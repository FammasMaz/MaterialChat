package com.materialchat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.materialchat.ui.components.seaglass.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Material 3 Expressive Switch: the thumb carries a state icon (check / close) that
 * swaps on the spatial scale spring and compresses while the switch is pressed, with
 * the high-contrast M3E color roles. Every toggle fires the toggle haptic.
 *
 * @param checked Current state.
 * @param onCheckedChange Invoked with the new state.
 * @param modifier Modifier for the switch (test tags go here).
 * @param enabled Whether the switch accepts input.
 * @param thumbContent Replacement thumb content; the default is the state icon.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Press compresses the icon on the spatial scale spring (overshoots on release).
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "expressiveSwitchIconScale"
    )

    Switch(
        checked = checked,
        onCheckedChange = { newValue ->
            haptics.perform(HapticPattern.TOGGLE)
            onCheckedChange(newValue)
        },
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        thumbContent = thumbContent ?: {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    // Spatial spring for the scale, effects spring for the fade.
                    (scaleIn(animationSpec = ExpressiveMotion.Spatial.scale(), initialScale = 0.4f) +
                        fadeIn(animationSpec = ExpressiveMotion.Effects.reveal())) togetherWith
                        (scaleOut(animationSpec = ExpressiveMotion.Effects.reveal(), targetScale = 0.4f) +
                            fadeOut(animationSpec = ExpressiveMotion.Effects.reveal()))
                },
                label = "expressiveSwitchThumb"
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SwitchDefaults.IconSize)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}
