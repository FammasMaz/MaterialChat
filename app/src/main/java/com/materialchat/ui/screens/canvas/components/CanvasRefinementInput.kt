package com.materialchat.ui.screens.canvas.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Refinement input bar at the bottom of the Smart Canvas screen.
 *
 * A simplified text input field styled similarly to the chat [MessageInput] but
 * focused on iterative artifact refinement. Uses M3 Expressive design tokens
 * with spring-based animations on the send button.
 *
 * @param value The current text in the input field
 * @param onValueChange Callback when the input text changes
 * @param onSend Callback when the send button is tapped or Enter is pressed
 * @param modifier Modifier for the input bar container
 */
@Composable
fun CanvasRefinementInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank()

    // M3 Expressive: Spatial spring for send button scale
    val sendButtonScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.85f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "canvasRefinementSendScale"
    )

    // M3 Expressive: Effects spring for send button color (no overshoot)
    val sendButtonColor by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "canvasRefinementSendColor"
    )

    val sendIconColor by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "canvasRefinementSendIconColor"
    )

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input field
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) onSend()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Refine this artifact...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Send button
            Surface(
                onClick = { if (canSend) onSend() },
                modifier = Modifier
                    .size(40.dp)
                    .scale(sendButtonScale),
                shape = CircleShape,
                color = sendButtonColor,
                enabled = canSend
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send refinement",
                        tint = sendIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
