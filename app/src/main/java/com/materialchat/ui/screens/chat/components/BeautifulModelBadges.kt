package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.util.ParsedModelName

/**
 * Beautiful dual pill badge component for displaying model names.
 *
 * Shows provider and model as separate expandable badges following M3 Expressive design.
 * Provider badge: tap to toggle abbreviated/full display
 * Model badge: tap opens model picker, long-press toggles abbreviated/full display
 *
 * @param parsedModel The parsed model name with provider and model parts
 * @param isStreaming Whether a message is currently streaming (disables interaction)
 * @param availableModels List of available models from the provider
 * @param isLoadingModels Whether models are being loaded
 * @param onModelSelected Callback when a model is selected
 * @param onLoadModels Callback to trigger model loading
 * @param modifier Optional modifier
 */
@Composable
fun BeautifulModelBadges(
    parsedModel: ParsedModelName,
    isStreaming: Boolean,
    availableModels: List<AiModel>,
    isLoadingModels: Boolean,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var modelPickerExpanded by remember { mutableStateOf(false) }
    var modelTextExpanded by remember { mutableStateOf(true) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider badge - tap to toggle expansion
        ExpandableProviderBadge(
            text = parsedModel.provider,
            abbreviatedText = parsedModel.providerAbbreviated,
            isExpanded = providerExpanded,
            enabled = !isStreaming,
            onToggle = { providerExpanded = !providerExpanded }
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Model badge - tap opens picker, long-press toggles expansion
        ModelPickerBadge(
            text = parsedModel.model,
            abbreviatedText = parsedModel.modelAbbreviated,
            isTextExpanded = modelTextExpanded,
            isPickerExpanded = modelPickerExpanded,
            availableModels = availableModels,
            currentModel = parsedModel.originalRaw,
            isLoadingModels = isLoadingModels,
            isStreaming = isStreaming,
            onPickerToggle = { expanded ->
                modelPickerExpanded = expanded
            },
            onTextToggle = { modelTextExpanded = !modelTextExpanded },
            onModelSelected = onModelSelected,
            onLoadModels = onLoadModels
        )
    }
}

/**
 * Expandable provider badge that toggles between abbreviated and full display.
 */
@Composable
private fun ExpandableProviderBadge(
    text: String,
    abbreviatedText: String,
    isExpanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive spring animation for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        ),
        label = "providerBadgeScale"
    )

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "providerContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "providerContentColor"
    )

    val displayText = if (isExpanded) text else abbreviatedText

    Box(
        modifier = Modifier
            .scale(scale)
            .defaultMinSize(minHeight = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = onToggle
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 380f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Model badge with picker functionality.
 * Tap opens the model picker dropdown.
 * Long-press toggles between abbreviated and full display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelPickerBadge(
    text: String,
    abbreviatedText: String,
    isTextExpanded: Boolean,
    isPickerExpanded: Boolean,
    availableModels: List<AiModel>,
    currentModel: String,
    isLoadingModels: Boolean,
    isStreaming: Boolean,
    onPickerToggle: (Boolean) -> Unit,
    onTextToggle: () -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive spring animation for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        ),
        label = "modelBadgeScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isPickerExpanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "modelContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (!isStreaming) {
            if (isPickerExpanded) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "modelContentColor"
    )

    // Load models when picker opens
    LaunchedEffect(isPickerExpanded) {
        if (isPickerExpanded && availableModels.isEmpty() && !isLoadingModels) {
            onLoadModels()
        }
    }

    val displayText = if (isTextExpanded) text else abbreviatedText

    Box {
        Box(
            modifier = Modifier
                .scale(scale)
                .defaultMinSize(minHeight = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .alpha(if (!isStreaming) 1f else 0.6f)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = !isStreaming,
                    onClick = { onPickerToggle(true) },
                    onLongClick = { onTextToggle() }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = 380f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText.ifEmpty { "Select model" },
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = if (isPickerExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isPickerExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }

        // Dropdown menu with models
        DropdownMenu(
            expanded = isPickerExpanded,
            onDismissRequest = { onPickerToggle(false) },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            when {
                isLoadingModels -> {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                availableModels.isEmpty() -> {
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
                else -> {
                    availableModels.forEach { model ->
                        val isSelected = model.id == currentModel || model.name == currentModel

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
                            label = "menuItemTextColor"
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
                            onClick = {
                                onPickerToggle(false)
                                onModelSelected(model)
                            },
                            modifier = if (isSelected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
    }
}
