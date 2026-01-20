package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel

/**
 * Top app bar for the Chat screen.
 *
 * Features:
 * - Back navigation button
 * - Conversation title with model name subtitle
 * - Overflow menu with export option
 * - Collapsing behavior on scroll
 *
 * @param title The conversation title
 * @param modelName The current model name
 * @param providerName The provider name
 * @param isStreaming Whether a message is currently streaming
 * @param onNavigateBack Callback for back navigation
 * @param onExportClick Callback when export is clicked
 * @param scrollBehavior Optional scroll behavior for collapsing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    modelName: String,
    providerName: String,
    isStreaming: Boolean,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    var showMenu by remember { mutableStateOf(false) }

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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        scrollBehavior = scrollBehavior
    )
}

/**
 * Model picker dropdown for selecting a different AI model.
 *
 * @param currentModel The currently selected model name
 * @param availableModels The list of available models
 * @param isLoading Whether models are being loaded
 * @param onModelSelected Callback when a model is selected
 * @param onLoadModels Callback to trigger model loading
 */
@Composable
fun ModelPickerButton(
    currentModel: String,
    availableModels: List<AiModel>,
    isLoading: Boolean,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Load models when dropdown is about to expand
    if (expanded && availableModels.isEmpty() && !isLoading) {
        onLoadModels()
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        if (isLoading) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Loading models...")
                    }
                },
                onClick = { }
            )
        } else if (availableModels.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No models available") },
                onClick = { }
            )
        } else {
            availableModels.forEach { model ->
                val isSelected = model.id == currentModel || model.name == currentModel
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model.name,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onModelSelected(model)
                    }
                )
            }
        }
    }
}
