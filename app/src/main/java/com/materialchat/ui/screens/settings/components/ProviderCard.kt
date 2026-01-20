package com.materialchat.ui.screens.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.ProviderType
import com.materialchat.ui.screens.settings.ProviderUiItem
import com.materialchat.ui.theme.CustomShapes

/**
 * Material 3 Expressive provider card component.
 *
 * Displays provider information with:
 * - Provider icon based on type (cloud for OpenAI, computer for Ollama)
 * - Name and default model
 * - Base URL
 * - API key status indicator
 * - Active provider checkmark
 * - Action buttons (Set Active, Test, Delete)
 *
 * @param providerItem The provider UI item to display
 * @param onActivate Callback when set active is tapped
 * @param onEdit Callback when the card is tapped to edit
 * @param onDelete Callback when delete is tapped
 * @param onTestConnection Callback when test is tapped
 * @param modifier Optional modifier
 */
@Composable
fun ProviderCard(
    providerItem: ProviderUiItem,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val provider = providerItem.provider
    var isPressed by remember { mutableStateOf(false) }

    // Spring-physics scale animation for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    // Animate background color for active state
    val backgroundColor by animateColorAsState(
        targetValue = if (provider.isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "backgroundColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(CustomShapes.ProviderCard)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onEdit() }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = CustomShapes.ProviderCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with icon, name, and active indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Provider type icon
                    ProviderTypeIcon(type = provider.type)

                    Spacer(modifier = Modifier.width(12.dp))

                    // Provider info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = provider.defaultModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Active indicator
                if (provider.isActive) {
                    ActiveIndicator()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Base URL
            Text(
                text = provider.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // API Key status (only for OpenAI-compatible providers)
            if (provider.type == ProviderType.OPENAI_COMPATIBLE) {
                Spacer(modifier = Modifier.height(4.dp))
                ApiKeyStatusIndicator(hasApiKey = providerItem.hasApiKey)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!provider.isActive) {
                    TextButton(onClick = onActivate) {
                        Text("Set Active")
                    }
                }
                TextButton(onClick = onTestConnection) {
                    Text("Test")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Icon representing the provider type.
 */
@Composable
private fun ProviderTypeIcon(
    type: ProviderType,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CustomShapes.Fab)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (type) {
                ProviderType.OPENAI_COMPATIBLE -> Icons.Outlined.Cloud
                ProviderType.OLLAMA_NATIVE -> Icons.Outlined.Computer
            },
            contentDescription = when (type) {
                ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible provider"
                ProviderType.OLLAMA_NATIVE -> "Ollama local provider"
            },
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Checkmark indicator for the active provider.
 */
@Composable
private fun ActiveIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CustomShapes.SendButton)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Active",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Indicator showing whether an API key is configured.
 */
@Composable
private fun ApiKeyStatusIndicator(
    hasApiKey: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (hasApiKey) Icons.Outlined.Key else Icons.Outlined.KeyOff,
            contentDescription = null,
            tint = if (hasApiKey) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            },
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (hasApiKey) "API key configured" else "No API key",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasApiKey) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            }
        )
    }
}
