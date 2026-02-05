package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.util.ModelNameParser
import com.materialchat.ui.util.ParsedModelName

/**
 * M3 Expressive Spring Tokens
 * - Fast spatial: For small components (badges, buttons)
 * - Default spatial: For partial screen animations
 */
private object M3Springs {
    // Fast spatial spring - for badge press/scale animations
    val FastSpatial = spring<Float>(
        dampingRatio = 0.6f,  // Some bounce for expressive feel
        stiffness = 400f      // Fast resolution
    )

    // Default effects spring - for color/opacity (no overshoot)
    val DefaultEffects = spring<androidx.compose.ui.graphics.Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Beautiful dual pill badge component for displaying model names.
 *
 * Follows M3 Expressive design with:
 * - 48dp minimum touch targets
 * - Spring-based physics for animations
 * - Pill shapes (full radius)
 * - Proper color tokens
 *
 * Provider badge: tap to open provider filter with search, long-press toggles text
 * Model badge: tap opens model picker with search, long-press toggles text
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
    var providerTextExpanded by remember { mutableStateOf(true) } // Show full text by default
    var modelPickerExpanded by remember { mutableStateOf(false) }
    var modelTextExpanded by remember { mutableStateOf(true) }

    // Track selected provider filter - null means show all
    var selectedProviderFilter by remember { mutableStateOf<String?>(null) }

    // Extract unique providers from available models
    val availableProviders = remember(availableModels) {
        availableModels
            .mapNotNull { model ->
                val parsed = ModelNameParser.parse(model.id)
                if (parsed.provider != "Provider") parsed.provider else null
            }
            .distinct()
            .sorted()
    }

    // Filter models based on selected provider
    val filteredModels = remember(availableModels, selectedProviderFilter) {
        if (selectedProviderFilter == null) {
            availableModels
        } else {
            availableModels.filter { model ->
                val parsed = ModelNameParser.parse(model.id)
                parsed.provider.equals(selectedProviderFilter, ignoreCase = true)
            }
        }
    }

    // Display text for provider badge
    val providerDisplayText = selectedProviderFilter ?: parsedModel.provider
    val providerAbbreviatedText = if (selectedProviderFilter != null) {
        if (selectedProviderFilter!!.length > 16) "${selectedProviderFilter!!.take(14)}…" else selectedProviderFilter!!
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
            onDropdownToggle = { expanded ->
                providerExpanded = expanded
            },
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
 * M3 Expressive Search Field for dropdowns.
 * Uses OutlinedTextField with proper M3 styling.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(48.dp) // Fixed height to prevent expansion
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(24.dp), // M3 Expressive pill shape
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() }
        )
    )

    // Auto-focus search field when shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Provider filter badge with dropdown and search.
 * M3 Expressive: 48dp min touch target, spring animations, pill shape.
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

    // Search state
    var searchQuery by remember { mutableStateOf("") }

    // Clear search when dropdown closes
    LaunchedEffect(isDropdownExpanded) {
        if (!isDropdownExpanded) {
            searchQuery = ""
        }
    }

    // Filter providers by search
    val filteredProviders by remember(availableProviders, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                availableProviders
            } else {
                availableProviders.filter {
                    it.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    // M3 Expressive: Fast spatial spring for press animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = M3Springs.FastSpatial,
        label = "providerBadgeScale"
    )

    // M3 Expressive: Effects spring for color (no bounce)
    val containerColor by animateColorAsState(
        targetValue = when {
            isFiltering -> MaterialTheme.colorScheme.primaryContainer
            isDropdownExpanded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        },
        animationSpec = M3Springs.DefaultEffects,
        label = "providerContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
        },
        animationSpec = M3Springs.DefaultEffects,
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
        // M3 Expressive: 48dp minimum touch target
        Box(
            modifier = Modifier
                .scale(scale)
                .defaultMinSize(minWidth = 40.dp, minHeight = 28.dp) // Compact size
                .clip(RoundedCornerShape(50)) // Full pill shape
                .background(containerColor)
                .alpha(if (enabled) 1f else 0.6f)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = enabled,
                    onClick = { onDropdownToggle(true) },
                    onLongClick = { onTextToggle() }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 400f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelMedium, // M3: labelMedium for compact buttons
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
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
            }
        }

        // Provider dropdown menu with search
        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { onDropdownToggle(false) },
            modifier = Modifier
                .heightIn(max = 350.dp)
                .width(240.dp)
        ) {
            // Search field
            if (availableProviders.size > 3) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search providers",
                )
            }

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
                    onClick = onClearFilter,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp) // M3: 48dp touch target
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
                        onClick = { },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
                filteredProviders.isEmpty() && searchQuery.isNotBlank() -> {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No providers match \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
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
                        onClick = { },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
                else -> {
                    filteredProviders.forEach { provider ->
                        val isSelected = provider.equals(text, ignoreCase = true)

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            animationSpec = M3Springs.DefaultEffects,
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
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Model badge with picker and search functionality.
 * M3 Expressive: 48dp min touch target, spring animations, pill shape.
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

    // Search state
    var searchQuery by remember { mutableStateOf("") }

    // Clear search when dropdown closes
    LaunchedEffect(isPickerExpanded) {
        if (!isPickerExpanded) {
            searchQuery = ""
        }
    }

    // Parse and filter models by search
    val searchedModels by remember(availableModels, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                availableModels
            } else {
                availableModels.filter { model ->
                    val parsed = ModelNameParser.parse(model.id)
                    parsed.model.contains(searchQuery, ignoreCase = true) ||
                    parsed.provider.contains(searchQuery, ignoreCase = true) ||
                    model.name.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    // M3 Expressive: Fast spatial spring for press animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = M3Springs.FastSpatial,
        label = "modelBadgeScale"
    )

    // M3 Expressive: Effects spring for color (no bounce)
    val containerColor by animateColorAsState(
        targetValue = if (isPickerExpanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = M3Springs.DefaultEffects,
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
        animationSpec = M3Springs.DefaultEffects,
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
        // M3 Expressive: 48dp minimum touch target
        Box(
            modifier = Modifier
                .scale(scale)
                .defaultMinSize(minWidth = 40.dp, minHeight = 28.dp) // Compact size
                .clip(RoundedCornerShape(50)) // Full pill shape
                .background(containerColor)
                .alpha(if (!isStreaming) 1f else 0.6f)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = !isStreaming,
                    onClick = { onPickerToggle(true) },
                    onLongClick = { onTextToggle() }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 400f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText.ifEmpty { "Select model" },
                    style = MaterialTheme.typography.labelMedium, // M3: labelMedium for compact buttons
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
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
            }
        }

        // Dropdown menu with models and search
        DropdownMenu(
            expanded = isPickerExpanded,
            onDismissRequest = { onPickerToggle(false) },
            modifier = Modifier
                .heightIn(max = 400.dp)
                .width(280.dp)
        ) {
            // Search field - show when there are enough models
            if (availableModels.size > 5) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search models",
                )
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
                                    text = "Loading models...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
                searchedModels.isEmpty() && searchQuery.isNotBlank() -> {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No models match \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
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
                        onClick = { },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
                else -> {
                    // Show count when filtering or searching
                    if (hasFilter || searchQuery.isNotBlank()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${searchedModels.size} model${if (searchedModels.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            onClick = { },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    searchedModels.forEach { model ->
                        val isSelected = model.id == currentModel || model.name == currentModel

                        // Parse the model to get a cleaner display name
                        val parsedModel = remember(model.id) { ModelNameParser.parse(model.id) }

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            animationSpec = M3Springs.DefaultEffects,
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
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp) // M3: 48dp touch target
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
