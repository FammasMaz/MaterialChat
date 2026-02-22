package com.materialchat.ui.screens.arena.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Prompt text field for the arena screen.
 *
 * Includes a send button to start the battle.
 *
 * @param prompt The current prompt text
 * @param onPromptChanged Callback when the prompt text changes
 * @param onSend Callback when the send button is clicked
 * @param enabled Whether the input is interactive
 * @param canSend Whether the send button should be enabled
 */
@Composable
fun ArenaPromptInput(
    prompt: String,
    onPromptChanged: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true,
    canSend: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("Enter a prompt for both models...")
            },
            enabled = enabled,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() }
            ),
            maxLines = 4,
            minLines = 1
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onSend,
            enabled = canSend,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Start Battle"
            )
        }
    }
}
