package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.SquircleShape

/**
 * Bottom sheet for picking a model to redo the response with.
 *
 * @param isVisible Whether the sheet is visible
 * @param models Available models to choose from
 * @param isLoading Whether models are still loading
 * @param currentModelName The current model name (shown with a checkmark)
 * @param onModelSelected Callback when a model is selected
 * @param onDismiss Callback when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedoModelPickerSheet(
    isVisible: Boolean,
    models: List<AiModel>,
    isLoading: Boolean,
    currentModelName: String,
    onModelSelected: (AiModel) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = CustomShapes.BottomSheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
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
                        animationSpec = ExpressiveMotion.Spatial.contentSize()
                    )
            ) {
                Text(
                    text = "Redo with Model",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Choose a model to regenerate this response",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading && models.isEmpty()) {
                    // Same loading language as the header's model picker: the M3 Expressive
                    // LoadingIndicator next to the status line.
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Loading models...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(models) { model ->
                            val isCurrentModel = model.id == currentModelName
                            // Selected-state morph: the current model sits on a tonal squircle
                            // that tightens on the spatial spring; every row compresses on press.
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val rowTone by animateColorAsState(
                                targetValue = if (isCurrentModel) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f)
                                },
                                animationSpec = ExpressiveMotion.Effects.color(),
                                label = "redoModelRowTone"
                            )
                            val rowRadius by animateDpAsState(
                                targetValue = when {
                                    isPressed -> 12.dp
                                    isCurrentModel -> 16.dp
                                    else -> 24.dp
                                },
                                animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
                                label = "redoModelRowRadius"
                            )
                            val rowScale by animateFloatAsState(
                                targetValue = if (isPressed) 0.97f else 1f,
                                animationSpec = ExpressiveMotion.Spatial.scale(),
                                label = "redoModelRowScale"
                            )
                            val rowShape = SquircleShape(rowRadius)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = rowScale
                                        scaleY = rowScale
                                    }
                                    .clip(rowShape)
                                    .background(rowTone, rowShape)
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        onModelSelected(model)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrentModel) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrentModel) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (isCurrentModel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Current model",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
