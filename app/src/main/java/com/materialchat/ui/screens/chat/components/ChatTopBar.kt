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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.screens.chat.components.ModelPickerDropdown

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
    modelName: String,
    providerName: String,
    isStreaming: Boolean,
    availableModels: List<AiModel>,
    isLoadingModels: Boolean,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Track title changes for reveal animation
    var previousTitle by remember { mutableStateOf(title) }
    var displayedTitle by remember { mutableStateOf(title) }
    val revealProgress = remember { Animatable(1f) }
    
    // Animate title reveal when title changes (excluding initial "New Chat")
    LaunchedEffect(title) {
        if (title != previousTitle && previousTitle == "New Chat" && title != "New Chat") {
            // New AI-generated title - animate reveal
            displayedTitle = title
            revealProgress.snapTo(0f)
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = title.length * 30 + 200, // Dynamic duration based on title length
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    if (isStreaming) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Export") },
                    onClick = {
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
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        scrollBehavior = scrollBehavior
    )
}

private data class ModelBrand(
    val name: String,
    val symbol: String,
    val color: Color
)

private fun resolveModelBrand(modelName: String): ModelBrand? {
    if (modelName.isBlank()) return null
    val normalized = modelName.lowercase()
    return when {
        "claude" in normalized -> ModelBrand("Anthropic", "A", Color(0xFFF97316))
        "gpt" in normalized || "openai" in normalized -> ModelBrand("OpenAI", "O", Color(0xFF111827))
        "gemini" in normalized -> ModelBrand("Google", "G", Color(0xFF4285F4))
        "mistral" in normalized || "mixtral" in normalized -> ModelBrand("Mistral", "M", Color(0xFFF59E0B))
        "llama" in normalized || "meta" in normalized -> ModelBrand("Meta", "M", Color(0xFF0EA5E9))
        "deepseek" in normalized -> ModelBrand("DeepSeek", "D", Color(0xFF06B6D4))
        "qwen" in normalized -> ModelBrand("Alibaba", "A", Color(0xFFEF4444))
        "cohere" in normalized || "command" in normalized -> ModelBrand("Cohere", "C", Color(0xFF6366F1))
        "perplexity" in normalized -> ModelBrand("Perplexity", "P", Color(0xFF7C3AED))
        "grok" in normalized || "xai" in normalized -> ModelBrand("xAI", "x", Color(0xFF111827))
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
