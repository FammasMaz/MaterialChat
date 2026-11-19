package com.materialchat.ui.screens.workflows.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.ExpressiveSizes

/**
 * Editor row for a single workflow step.
 *
 * Displays a step number badge, a prompt template text field, and a delete button.
 * Shows template variable hints ({{input}}, {{step_N}}) below the text field.
 *
 * @param stepNumber The 1-indexed step number
 * @param promptTemplate The current prompt template text
 * @param onPromptChange Callback when the prompt template text changes
 * @param onDelete Callback when the delete button is clicked
 * @param canDelete Whether this step can be deleted (at least 1 step required)
 * @param modifier Modifier for the row
 */
@Composable
fun StepEditor(
    stepNumber: Int,
    promptTemplate: String,
    onPromptChange: (String) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.DisclosurePanel,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Step number badge
                Surface(
                    shape = ExpressiveShapes.TileBadge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ExpressiveSizes.Compact)
                ) {
                    Text(
                        text = "$stepNumber",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Step $stepNumber",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Delete button
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(ExpressiveSizes.Target)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete step $stepNumber",
                            modifier = Modifier.size(ExpressiveSizes.Icon),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Prompt template text field
            OutlinedTextField(
                value = promptTemplate,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("Prompt template") },
                placeholder = { Text("Enter prompt for this step…") },
                minLines = 3,
                maxLines = 8,
                shape = ExpressiveShapes.MenuSelection,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Template variable hints
            Text(
                text = buildString {
                    append("Variables: {{input}}")
                    if (stepNumber > 1) {
                        for (i in 1 until stepNumber) {
                            append(", {{step_$i}}")
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
