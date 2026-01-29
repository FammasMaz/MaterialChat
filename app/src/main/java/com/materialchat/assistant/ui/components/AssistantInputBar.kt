package com.materialchat.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.VoiceState
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive input bar for the assistant overlay.
 *
 * Features:
 * - Text input with rounded container
 * - Voice/Send button morphing based on state
 * - Voice waveform visualization when listening
 * - Spring-based animations
 * - M3 Expressive press feedback with scale and shape morphing
 *
 * @param textInput Current text input value
 * @param onTextChange Callback when text changes
 * @param voiceState Current voice interaction state
 * @param amplitudeData Audio amplitude data for waveform
 * @param onVoiceClick Callback when voice button is clicked
 * @param onSendClick Callback when send button is clicked
 * @param onStopVoice Callback to stop voice input
 * @param modifier Modifier for the input bar
 */
@Composable
fun AssistantInputBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    voiceState: VoiceState,
    amplitudeData: AudioAmplitudeData,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit,
    onStopVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening
    val isProcessing = voiceState is VoiceState.Processing
    val hasText = textInput.isNotBlank()

    // Animate container expansion for listening state
    val containerHeight by animateDpAsState(
        targetValue = if (isListening) 80.dp else 56.dp,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "containerHeight"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isListening) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "containerColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        if (isListening) {
            // Listening mode - show waveform
            ListeningContent(
                amplitudeData = amplitudeData,
                onStop = onStopVoice
            )
        } else {
            // Normal input mode
            NormalInputContent(
                textInput = textInput,
                onTextChange = onTextChange,
                hasText = hasText,
                isProcessing = isProcessing,
                voiceState = voiceState,
                onVoiceClick = onVoiceClick,
                onSendClick = onSendClick
            )
        }
    }
}

@Composable
private fun ListeningContent(
    amplitudeData: AudioAmplitudeData,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice waveform
        VoiceWaveform(
            amplitudeData = amplitudeData,
            barColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            maxBarHeight = 48.dp,
            modifier = Modifier.weight(1f)
        )

        // Stop button
        IconButton(
            onClick = onStop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Stop listening",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun NormalInputContent(
    textInput: String,
    onTextChange: (String) -> Unit,
    hasText: Boolean,
    isProcessing: Boolean,
    voiceState: VoiceState,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text input field
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Placeholder
            if (textInput.isEmpty()) {
                val placeholderText = when (voiceState) {
                    is VoiceState.Processing -> voiceState.partialText.ifEmpty { "Processing..." }
                    is VoiceState.Thinking -> "Thinking..."
                    else -> "Ask anything..."
                }
                Text(
                    text = placeholderText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Actual text field
            BasicTextField(
                value = textInput,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (hasText) onSendClick() }
                )
            )
        }

        // Voice/Send button
        ActionButton(
            hasText = hasText,
            isProcessing = isProcessing,
            onVoiceClick = onVoiceClick,
            onSendClick = onSendClick
        )
    }
}

/**
 * M3 Expressive action button with proper press feedback.
 *
 * Features:
 * - Scale animation on press (0.9x) using interactionSource
 * - Shape morphing on press (circle to rounded square)
 * - Color transition between voice and send states
 */
@Composable
private fun ActionButton(
    hasText: Boolean,
    isProcessing: Boolean,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonColor by animateColorAsState(
        targetValue = if (hasText) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "buttonColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (hasText) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "iconColor"
    )

    // M3 Expressive: Scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "buttonScale"
    )

    // M3 Expressive: Shape morphing on press (circle to rounded square)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 24.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "buttonCorner"
    )

    Surface(
        onClick = {
            if (hasText) {
                onSendClick()
            } else {
                onVoiceClick()
            }
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        color = buttonColor,
        enabled = !isProcessing
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                ProcessingWaveform(
                    barColor = MaterialTheme.colorScheme.primary,
                    barCount = 3,
                    barWidth = 3.dp,
                    barSpacing = 2.dp,
                    barHeight = 16.dp
                )
            } else {
                Icon(
                    imageVector = if (hasText) {
                        Icons.AutoMirrored.Filled.Send
                    } else {
                        Icons.Default.Mic
                    },
                    contentDescription = if (hasText) "Send" else "Voice input",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
