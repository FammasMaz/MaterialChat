package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.CustomShapes

/**
 * Message input bar component for the chat screen.
 *
 * Features:
 * - Multi-line text input with rounded corners
 * - Animated send/stop button based on streaming state
 * - Keyboard action support (Enter to send)
 * - Disabled state during streaming
 * - Material 3 Expressive styling with spring animations
 * - Haptic feedback on button interactions
 *
 * @param inputText Current text in the input field
 * @param isStreaming Whether a message is currently streaming
 * @param canSend Whether the send button should be enabled
 * @param onInputChange Callback when input text changes
 * @param onSend Callback when send button is clicked
 * @param onCancel Callback when stop button is clicked (during streaming)
 * @param modifier Modifier for the input bar container
 * @param hapticsEnabled Whether haptic feedback is enabled
 */
@Composable
fun MessageInput(
    inputText: String,
    isStreaming: Boolean,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text input field
            MessageTextField(
                value = inputText,
                onValueChange = onInputChange,
                enabled = !isStreaming,
                canSend = canSend,
                onSend = {
                    haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                    onSend()
                },
                modifier = Modifier.weight(1f)
            )

            // Action button (send or stop)
            ActionButton(
                isStreaming = isStreaming,
                canSend = canSend,
                onSend = {
                    haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                    onSend()
                },
                onCancel = {
                    haptics.perform(HapticPattern.CONFIRM, hapticsEnabled)
                    onCancel()
                }
            )
        }
    }
}

/**
 * The text input field for typing messages.
 */
@Composable
private fun MessageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = "Type a message...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        enabled = enabled,
        maxLines = 4,
        shape = CustomShapes.MessageInputContainer,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Send
        ),
        keyboardActions = KeyboardActions(
            onSend = {
                if (canSend) {
                    onSend()
                }
            }
        )
    )
}

/**
 * Animated action button that switches between send and stop icons.
 */
@Composable
private fun ActionButton(
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    // Stop button (shown during streaming)
    AnimatedVisibility(
        visible = isStreaming,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        StopButton(onClick = onCancel)
    }

    // Send button (shown when not streaming)
    AnimatedVisibility(
        visible = !isStreaming,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        SendButton(
            enabled = canSend,
            onClick = onSend
        )
    }
}

/**
 * Send button with animated scale on enabled state change.
 */
@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "send_button_scale"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send message"
        )
    }
}

/**
 * Stop button to cancel streaming.
 */
@Composable
private fun StopButton(
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop generating"
        )
    }
}
