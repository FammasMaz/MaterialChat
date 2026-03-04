package com.materialchat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * Material 3 Expressive Switch with thumb icons and haptic feedback.
 *
 * Follows the M3 Expressive design language:
 * - Check icon in the thumb when checked
 * - Close icon in the thumb when unchecked
 * - Haptic feedback on every toggle
 *
 * @param checked Whether the switch is checked
 * @param onCheckedChange Callback when the switch state changes
 * @param modifier Modifier for the switch
 * @param enabled Whether the switch is enabled
 * @param thumbContent Optional composable content override for the thumb.
 *        If null, uses the default check/close icon.
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

    Switch(
        checked = checked,
        onCheckedChange = { newValue ->
            haptics.perform(HapticPattern.CLICK)
            onCheckedChange(newValue)
        },
        modifier = modifier,
        enabled = enabled,
        thumbContent = thumbContent ?: {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}
