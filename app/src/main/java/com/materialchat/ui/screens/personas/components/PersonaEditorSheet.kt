package com.materialchat.ui.screens.personas.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.Persona
import com.materialchat.domain.model.PersonaTone

/**
 * Modal bottom sheet for creating or editing a persona.
 *
 * @param persona The persona to edit, or null to create a new one
 * @param onDismiss Called when the sheet is dismissed
 * @param onSave Called with the final persona when the user taps Save
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonaEditorSheet(
    persona: Persona?,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = persona != null

    // Form state
    var name by remember { mutableStateOf(persona?.name ?: "") }
    var emoji by remember { mutableStateOf(persona?.emoji ?: "\uD83E\uDD16") }
    var description by remember { mutableStateOf(persona?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(persona?.systemPrompt ?: "") }
    var tagsText by remember { mutableStateOf(persona?.expertiseTags?.joinToString(", ") ?: "") }
    var tone by remember { mutableStateOf(persona?.tone ?: PersonaTone.BALANCED) }
    var startersText by remember {
        mutableStateOf(persona?.conversationStarters?.joinToString("\n") ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Text(
                text = if (isEditing) "Edit Persona" else "Create Persona",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Emoji + Name row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 4) emoji = it },
                    label = { Text("Emoji") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(0.25f)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(0.75f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // System Prompt
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Expertise Tags (comma-separated)
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Expertise Tags (comma-separated)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tone selector
            Text(
                text = "Tone",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PersonaTone.entries.forEach { toneOption ->
                    FilterChip(
                        selected = tone == toneOption,
                        onClick = { tone = toneOption },
                        label = {
                            Text(
                                text = toneOption.name.lowercase()
                                    .replaceFirstChar { it.uppercase() }
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Conversation Starters (one per line)
            OutlinedTextField(
                value = startersText,
                onValueChange = { startersText = it },
                label = { Text("Conversation Starters (one per line)") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val tags = tagsText
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        val starters = startersText
                            .split("\n")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                        val saved = (persona ?: Persona(
                            name = "",
                            systemPrompt = ""
                        )).copy(
                            name = name.trim(),
                            emoji = emoji.ifBlank { "\uD83E\uDD16" },
                            description = description.trim(),
                            systemPrompt = systemPrompt.trim(),
                            expertiseTags = tags,
                            tone = tone,
                            conversationStarters = starters,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSave(saved)
                    },
                    enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isEditing) "Update" else "Create")
                }
            }
        }
    }
}
