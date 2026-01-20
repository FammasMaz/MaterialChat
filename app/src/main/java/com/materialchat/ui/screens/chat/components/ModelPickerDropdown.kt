package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel

/**
 * Model picker dropdown for selecting AI models.
 *
 * This component displays the current model name as a clickable text
 * that opens a dropdown menu with available models from the provider.
 * Features Material 3 Expressive animations and styling.
 *
 * @param currentModel The currently selected model name
 * @param availableModels List of available models from the provider
 * @param isLoadingModels Whether models are currently being fetched
 * @param isStreaming Whether a message is currently being streamed (disables picker)
 * @param onModelSelected Callback when a model is selected
 * @param onLoadModels Callback to trigger model loading when dropdown opens
 * @param modifier Optional modifier for the component
 */
@Composable
fun ModelPickerDropdown(
    currentModel: String,
    availableModels: List<AiModel>,
    isLoadingModels: Boolean,
    isStreaming: Boolean,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring-physics scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Load models when dropdown opens
    LaunchedEffect(expanded) {
        if (expanded && availableModels.isEmpty() && !isLoadingModels) {
            onLoadModels()
        }
    }

    Box(modifier = modifier) {
        // Clickable model name with arrow indicator
        ModelButton(
            modelName = currentModel,
            isExpanded = expanded,
            isEnabled = !isStreaming,
            scale = scale,
            interactionSource = interactionSource,
            onClick = { if (!isStreaming) expanded = true }
        )

        // Dropdown menu with models
        ModelDropdownMenu(
            expanded = expanded,
            currentModel = currentModel,
            availableModels = availableModels,
            isLoadingModels = isLoadingModels,
            onDismiss = { expanded = false },
            onModelSelected = { model ->
                expanded = false
                onModelSelected(model)
            }
        )
    }
}

/**
 * The clickable button showing current model name.
 */
@Composable
private fun ModelButton(
    modelName: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    scale: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = isEnabled,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = modelName.ifEmpty { "Select model" },
            style = MaterialTheme.typography.bodySmall,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(2.dp))

        Icon(
            imageVector = if (isExpanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(16.dp),
            tint = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Dropdown menu containing the list of available models.
 */
@Composable
private fun ModelDropdownMenu(
    expanded: Boolean,
    currentModel: String,
    availableModels: List<AiModel>,
    isLoadingModels: Boolean,
    onDismiss: () -> Unit,
    onModelSelected: (AiModel) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .heightIn(max = 300.dp)
    ) {
        when {
            isLoadingModels -> {
                LoadingMenuItem()
            }
            availableModels.isEmpty() -> {
                EmptyMenuItem()
            }
            else -> {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    availableModels.forEach { model ->
                        ModelMenuItem(
                            model = model,
                            isSelected = model.id == currentModel || model.name == currentModel,
                            onClick = { onModelSelected(model) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading state menu item.
 */
@Composable
private fun LoadingMenuItem() {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Loading models...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = { }
    )
}

/**
 * Empty state menu item when no models are available.
 */
@Composable
private fun EmptyMenuItem() {
    DropdownMenuItem(
        text = {
            Text(
                text = "No models available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = { }
    )
}

/**
 * Individual model menu item with selection indicator.
 */
@Composable
private fun ModelMenuItem(
    model: AiModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Animate text color on selection
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "textColor"
    )

    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = textColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        onClick = onClick,
        modifier = if (isSelected) {
            Modifier.background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        } else {
            Modifier
        }
    )
}
