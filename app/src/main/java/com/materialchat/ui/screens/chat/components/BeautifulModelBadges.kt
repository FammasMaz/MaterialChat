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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.materialchat.ui.util.ModelNameParser
import com.materialchat.ui.util.ParsedModelName

/**
 * Beautiful dual pill badge component for displaying model names.
 *
 * Shows provider and model as separate expandable badges following M3 Expressive design.
 * Provider badge: tap to open provider filter dropdown, long-press toggles text expansion
 * Model badge: tap opens model picker (filtered by provider), long-press toggles text expansion
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
    var providerTextExpanded by remember { mutableStateOf(false) }
    var modelPickerExpanded by remember { mutableStateOf(false) }
    var modelTextExpanded by remember { mutableStateOf(true) }

    // Track selected provider filter - null means show all
    var selectedProviderFilter by remember { mutableStateOf<String?>(null) }

    // Extract unique providers from available models
    val availableProviders by remember(availableModels) {
        derivedStateOf {
            availableModels
                .mapNotNull { model ->
                    val parsed = ModelNameParser.parse(model.id)
                    if (parsed.provider != "Provider") parsed.provider else null
                }
                .distinct()
                .sorted()
        }
    }

    // Filter models based on selected provider
    val filteredModels by remember(availableModels, selectedProviderFilter) {
        derivedStateOf {
            if (selectedProviderFilter == null) {
                availableModels
            } else {
                availableModels.filter { model ->
                    val parsed = ModelNameParser.parse(model.id)
                    parsed.provider.equals(selectedProviderFilter, ignoreCase = true)
                }
            }
        }
    }

    // Display text for provider badge
    val providerDisplayText = selectedProviderFilter ?: parsedModel.provider
    val providerAbbreviatedText = if (selectedProviderFilter != null) {
        if (selectedProviderFilter!!.length > 10) "${selectedProviderFilter!!.take(8)}..." else selectedProviderFilter!!
    } else {
        parsedModel.providerAbbreviated
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider badge - tap to open filter dropdown, long-press to toggle expansion
        ProviderFilterBadge(
            text = providerDisplayText,
            abbreviatedText = providerAbbreviatedText,
            isTextExpanded = providerTextExpanded,
            isDropdownExpanded = providerExpanded,
            isFiltering = selectedProviderFilter != null,
            availableProviders = availableProviders,
            isLoadingModels = isLoadingModels,
            enabled = !isStreaming,
            onDropdownToggle = { expanded -> providerExpanded = expanded },
            onTextToggle = { providerTextExpanded = !providerTextExpanded },
            onProviderSelected = { provider ->
                selectedProviderFilter = provider
                providerExpanded = false
            },
            onClearFilter = {
                selectedProviderFilter = null
                providerExpanded = false
            },
            onLoadModels = onLoadModels
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Model badge - tap opens picker, long-press toggles expansion
        ModelPickerBadge(
            text = parsedModel.model,
            abbreviatedText = parsedModel.modelAbbreviated,
            isTextExpanded = modelTextExpanded,
            isPickerExpanded = modelPickerExpanded,
            availableModels = filteredModels,
            currentModel = parsedModel.originalRaw,
            isLoadingModels = isLoadingModels,
            isStreaming = isStreaming,
            hasFilter = selectedProviderFilter != null,
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
 * Provider filter badge with dropdown for selecting provider filter.
 * Tap opens dropdown, long-press toggles text expansion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderFilterBadge(
    text: String,
    abbreviatedText: String,
    isTextExpanded: Boolean,
    isDropdownExpanded: Boolean,
    isFiltering: Boolean,
    availableProviders: List<String>,
    isLoadingModels: Boolean,
    enabled: Boolean,
    onDropdownToggle: (Boolean) -> Unit,
    onTextToggle: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onClearFilter: () -> Unit,
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
        label = "providerBadgeScale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isFiltering -> MaterialTheme.colorScheme.primaryContainer
            isDropdownExpanded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        },
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

    // Load models when dropdown opens
    LaunchedEffect(isDropdownExpanded) {
        if (isDropdownExpanded && availableProviders.isEmpty() && !isLoadingModels) {
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
                .alpha(if (enabled) 1f else 0.6f)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = enabled,
                    onClick = { onDropdownToggle(true) },
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
                    text = displayText,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = if (isDropdownExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isDropdownExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }

        // Provider dropdown menu
        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { onDropdownToggle(false) },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            // Clear filter option (if filtering)
            if (isFiltering) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Show all models",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    onClick = onClearFilter
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

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
                                    text = "Loading providers...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { }
                    )
                }
                availableProviders.isEmpty() -> {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = "No providers found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Load models to see providers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        },
                        onClick = { }
                    )
                }
                else -> {
                    availableProviders.forEach { provider ->
                        val isSelected = provider.equals(text, ignoreCase = true)

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
                            label = "providerItemTextColor"
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = provider,
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
                            onClick = { onProviderSelected(provider) },
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

/**
 * Model badge with picker functionality.
 * Tap opens the model picker dropdown (filtered by provider if set).
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
    hasFilter: Boolean,
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
                            Column {
                                Text(
                                    text = if (hasFilter) "No models for this provider" else "No models available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasFilter) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Try selecting a different provider",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        onClick = { }
                    )
                }
                else -> {
                    // Show filtered count if filtering
                    if (hasFilter) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${availableModels.size} models",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            onClick = { },
                            modifier = Modifier.height(24.dp)
                        )
                    }

                    availableModels.forEach { model ->
                        val isSelected = model.id == currentModel || model.name == currentModel

                        // Parse the model to get a cleaner display name
                        val parsedModel = remember(model.id) { ModelNameParser.parse(model.id) }

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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = parsedModel.model,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!hasFilter) {
                                            // Show provider when not filtering
                                            Text(
                                                text = parsedModel.provider,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

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
