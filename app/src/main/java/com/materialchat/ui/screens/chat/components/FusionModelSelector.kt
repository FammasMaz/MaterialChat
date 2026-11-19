package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonColors
import com.materialchat.ui.components.ExpressiveButtonStyle
import com.materialchat.ui.components.SheetGutter
import com.materialchat.ui.components.SheetHeader
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes

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
            // Same sheet family as settings: top-only squircle, low container tier, default
            // drag handle, headline title with a close affordance.
            shape = ExpressiveShapes.BottomSheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SheetGutter)
                    .padding(bottom = 32.dp)
                    .animateContentSize(
                        animationSpec = ExpressiveMotion.Spatial.contentSize()
                    )
            ) {
                SheetHeader(title = "Response fusion", onClose = onDismiss)

                Text(
                    text = "Select 2-3 models to query in parallel, then pick a judge to synthesize the best response.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
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
                        text = "Loading models\u2026",
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
                                    .clip(ExpressiveShapes.MenuSelection)
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
                                        MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = "Judge model",
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
                                    .clip(ExpressiveShapes.MenuSelection)
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

                // The shared expressive button (press shape morph, scale dip, click haptic)
                // instead of a raw M3 Button on CustomShapes.Button (W7 chat part 2), on the
                // theme's tertiary filled pair (W8 theme C3) so the Start action matches the
                // sheet's tertiary checkmarks and judge star; the pair is in the theme's
                // contrast gate.
                ExpressiveButton(
                    onClick = onStartFusion,
                    enabled = selectedModelIds.size >= 2 && judgeModelId != null,
                    modifier = Modifier.fillMaxWidth(),
                    text = "Start fusion",
                    leadingIcon = Icons.AutoMirrored.Filled.Send,
                    style = ExpressiveButtonStyle.Filled,
                    colors = ExpressiveButtonColors.tertiary()
                )
            }
        }
    }
}
