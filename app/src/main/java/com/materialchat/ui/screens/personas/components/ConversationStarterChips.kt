package com.materialchat.ui.screens.personas.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrollable row of suggestion chips for persona conversation starters.
 *
 * Each chip represents a pre-defined prompt that the user can tap to populate
 * the message input field.
 *
 * @param starters The list of conversation starter strings
 * @param onStarterSelected Called with the selected starter text
 * @param modifier Modifier for the row
 */
@Composable
fun ConversationStarterChips(
    starters: List<String>,
    onStarterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (starters.isEmpty()) return

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        starters.forEach { starter ->
            SuggestionChip(
                onClick = { onStarterSelected(starter) },
                label = {
                    Text(
                        text = starter,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = null
            )
        }
    }
}
