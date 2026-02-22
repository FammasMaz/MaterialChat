package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.theme.CustomShapes

/**
 * Bottom sheet for selecting models for fusion mode.
 *
 * Allows users to:
 * - Select 2-3 models for parallel querying
 * - Choose a judge model for synthesis
 * - Start the fusion operation
 *
 * @param isVisible Whether the sheet is visible
 * @param models Available models to choose from
 * @param isLoading Whether models are still loading
 * @param selectedModelIds Set of currently selected model IDs
 * @param judgeModelId The currently selected judge model ID
 * @param onModelToggle Callback when a model checkbox is toggled
 * @param onJudgeModelSelect Callback when a judge model is selected
 * @param onStartFusion Callback when the "Start Fusion" button is tapped
 * @param onDismiss Callback when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FusionModelSelector(
    isVisible: Boolean,
    models: List<AiModel>,
    isLoading: Boolean,
    selectedModelIds: Set<String>,
    judgeModelId: String?,
    onModelToggle: (AiModel) -> Unit,
    onJudgeModelSelect: (AiModel) -> Unit,
    onStartFusion: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = CustomShapes.BottomSheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                Text(
                    text = "Response Fusion",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Select 2-3 models to query in parallel, then pick a judge to synthesize the best response.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Models section
                Text(
                    text = "Models (${selectedModelIds.size}/3)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoading && models.isEmpty()) {
                    Text(
                        text = "Loading models...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(models) { model ->
                            val isSelected = selectedModelIds.contains(model.id)
                            val canSelect = isSelected || selectedModelIds.size < 3

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = canSelect) { onModelToggle(model) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { if (canSelect) onModelToggle(model) },
                                    enabled = canSelect,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.tertiary,
                                        checkmarkColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                )

                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else if (!canSelect) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Judge model section
                if (selectedModelIds.size >= 2) {
                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Judge Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "The judge synthesizes all responses into one answer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 180.dp)
                    ) {
                        items(models) { model ->
                            val isJudge = model.id == judgeModelId

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onJudgeModelSelect(model) }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isJudge) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isJudge) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isJudge) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (isJudge) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected judge",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }

                // Start Fusion button
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onStartFusion,
                    enabled = selectedModelIds.size >= 2 && judgeModelId != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Fusion",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
