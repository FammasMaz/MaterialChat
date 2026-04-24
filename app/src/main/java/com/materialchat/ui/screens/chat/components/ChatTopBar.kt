package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.materialchat.ui.theme.CustomShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.screens.chat.components.ModelPickerDropdown
import com.materialchat.ui.util.ModelNameParser
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback

/**
 * Top app bar for the Chat screen.
 *
 * Features:
 * - Back navigation button
 * - Conversation title with optional emoji and tappable model picker subtitle
 * - Overflow menu with export option
 * - Collapsing behavior on scroll
 *
 * @param title The conversation title
 * @param icon Optional emoji icon for the conversation
 * @param modelName The current model name
 * @param providerName The provider name
 * @param isStreaming Whether a message is currently streaming
 * @param availableModels List of available models from the provider
 * @param isLoadingModels Whether models are being loaded
 * @param beautifulModelNamesEnabled Whether to show beautiful formatted model badges
 * @param onNavigateBack Callback for back navigation
 * @param onExportClick Callback when export is clicked
 * @param onModelSelected Callback when a model is selected from the picker
 * @param onLoadModels Callback to trigger model loading
 * @param scrollBehavior Optional scroll behavior for collapsing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    icon: String? = null,
    isEphemeral: Boolean = false,
    isArchived: Boolean = false,
    modelName: String,
    providerName: String,
    isStreaming: Boolean,
    availableModels: List<AiModel>,
    isLoadingModels: Boolean,
    beautifulModelNamesEnabled: Boolean = false,
    hasBranches: Boolean = false,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit,
    onMindMapClick: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val haptics = rememberHapticFeedback()
    var showMenu by remember { mutableStateOf(false) }
    
    // Track title changes for reveal animation
    var previousTitle by remember { mutableStateOf(title) }
    var displayedTitle by remember { mutableStateOf(title) }
    val revealProgress = remember { Animatable(1f) }
    
    // Animate title reveal when title changes (excluding initial "New Chat")
    LaunchedEffect(title) {
        if (title != previousTitle && previousTitle == "New Chat" && title != "New Chat") {
            // New AI-generated title - animate reveal using M3 Expressive spring physics
            displayedTitle = title
            revealProgress.snapTo(0f)
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 200f // Lower stiffness for smooth character reveal
                )
            )
        } else {
            // Direct update without animation
            displayedTitle = title
            revealProgress.snapTo(1f)
        }
        previousTitle = title
    }
    
    // Calculate visible characters based on reveal progress
    val visibleCharCount = (displayedTitle.length * revealProgress.value).toInt()
    val animatedTitle = if (revealProgress.value < 1f) {
        displayedTitle.take(visibleCharCount)
    } else {
        displayedTitle
    }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Display icon if available
                    if (icon != null) {
                        Text(
                            text = icon,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.alpha(
                                (revealProgress.value * 5f).coerceIn(0f, 1f)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = animatedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(
                            // Smooth fade-in: ramp up alpha over first 20% of animation
                            (revealProgress.value * 5f).coerceIn(0f, 1f)
                        )
                    )
                }
                // M3 spacing between title and model badges (space-xs = 4dp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (beautifulModelNamesEnabled) {
                        // Beautiful formatted dual pill badges
                        val parsedModel = remember(modelName) { ModelNameParser.parse(modelName) }
                        BeautifulModelBadges(
                            parsedModel = parsedModel,
                            isStreaming = isStreaming,
                            availableModels = availableModels,
                            isLoadingModels = isLoadingModels,
                            onModelSelected = onModelSelected,
                            onLoadModels = onLoadModels
                        )
                    } else {
                        // Original display: brand badge + model picker
                        val brand = remember(modelName) { resolveModelBrand(modelName) }
                        if (brand != null) {
                            ModelBrandBadge(brand = brand)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        // Model picker dropdown - tappable model name
                        ModelPickerDropdown(
                            currentModel = modelName,
                            availableModels = availableModels,
                            isLoadingModels = isLoadingModels,
                            isStreaming = isStreaming,
                            onModelSelected = onModelSelected,
                            onLoadModels = onLoadModels
                        )
                    }
                    // Streaming indicator removed - now shown in the morphing send button
                }

                if (isArchived) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Archived",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = { haptics.perform(HapticPattern.CLICK); showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = CustomShapes.Dropdown
            ) {
                DropdownMenuItem(
                    text = { Text("Export") },
                    onClick = {
                        haptics.perform(HapticPattern.CLICK)
                        showMenu = false
                        onExportClick()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null
                        )
                    }
                )
                if (hasBranches) {
                    DropdownMenuItem(
                        text = { Text("Mind Map") },
                        onClick = {
                            haptics.perform(HapticPattern.CLICK)
                            showMenu = false
                            onMindMapClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AccountTree,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isEphemeral) MaterialTheme.colorScheme.tertiaryContainer
                             else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = if (isEphemeral) MaterialTheme.colorScheme.tertiaryContainer
                                     else MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = if (isEphemeral) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = if (isEphemeral) MaterialTheme.colorScheme.onTertiaryContainer
                                         else MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = if (isEphemeral) MaterialTheme.colorScheme.onTertiaryContainer
                                     else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        scrollBehavior = scrollBehavior
    )
}

private data class ModelBrand(
    val name: String,
    val symbol: String
)

private fun resolveModelBrand(modelName: String): ModelBrand? {
    if (modelName.isBlank()) return null
    val normalized = modelName.lowercase()
    return when {
        "claude" in normalized -> ModelBrand("Anthropic", "A")
        "gpt" in normalized || "openai" in normalized -> ModelBrand("OpenAI", "O")
        "gemini" in normalized -> ModelBrand("Google", "G")
        "mistral" in normalized || "mixtral" in normalized -> ModelBrand("Mistral", "M")
        "llama" in normalized || "meta" in normalized -> ModelBrand("Meta", "M")
        "deepseek" in normalized -> ModelBrand("DeepSeek", "D")
        "qwen" in normalized -> ModelBrand("Alibaba", "A")
        "cohere" in normalized || "command" in normalized -> ModelBrand("Cohere", "C")
        "perplexity" in normalized -> ModelBrand("Perplexity", "P")
        "grok" in normalized || "xai" in normalized -> ModelBrand("xAI", "x")
        else -> null
    }
}

@Composable
private fun ModelBrandBadge(
    brand: ModelBrand,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = brand.symbol,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
