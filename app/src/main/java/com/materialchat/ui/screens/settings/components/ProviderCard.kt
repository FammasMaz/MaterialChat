package com.materialchat.ui.screens.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.materialchat.ui.components.ExpressiveButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.materialchat.ui.theme.SquircleShape
import com.materialchat.ui.theme.ExpressiveSizes
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
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.settings.ProviderUiItem
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.components.HomeSettingsSlot
import com.materialchat.ui.components.rememberHomeSettingsPalette
import com.materialchat.ui.theme.ExpressiveShapes

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val haptics = rememberHapticFeedback()

    // Press scale: same list-item press spring as conversation rows
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = ExpressiveMotion.Press.Scale,
        label = "cardScale"
    )

    // Pressed morph: the squircle rounds further on the shape-morph spring, paired with the scale.
    val cardRadius by animateDpAsState(
        targetValue = if (isPressed) 34.dp else 28.dp,
        animationSpec = ExpressiveMotion.Press.Shape,
        label = "cardCorner"
    )
    val cardShape = SquircleShape(cardRadius)

    // Animate background color for active state
    val backgroundColor by animateColorAsState(
        targetValue = if (provider.isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            // One tone above the screen background: at surfaceContainerLow the card silhouette
            // was invisible against the settings surface and the rows read as floating text.
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "backgroundColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(cardShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { if (!provider.type.isOnDevice) onEdit() }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (provider.isActive) 3.dp else 1.dp)
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

            // API key / native OAuth credential status
            if (provider.type.requiresStoredCredential) {
                Spacer(modifier = Modifier.height(4.dp))
                ApiKeyStatusIndicator(hasApiKey = providerItem.hasApiKey, isActive = provider.isActive)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions: M3E split button. The primary action leads (Set Active, or Test once
            // active); the remaining actions sit behind the trailing chevron so every action
            // of the old button row is still reachable.
            var actionsExpanded by remember { mutableStateOf(false) }
            val chevronRotation by animateFloatAsState(
                targetValue = if (actionsExpanded) 180f else 0f,
                animationSpec = ExpressiveMotion.Spatial.default(),
                label = "providerActionsChevron"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.TonalLeadingButton(
                                onClick = {
                                    haptics.perform(HapticPattern.CLICK)
                                    if (provider.isActive) onTestConnection() else onActivate()
                                }
                            ) {
                                Text(text = if (provider.isActive) "Test" else "Set active")
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TonalTrailingButton(
                                checked = actionsExpanded,
                                onCheckedChange = { actionsExpanded = it }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "More provider actions",
                                    modifier = Modifier
                                        .size(SplitButtonDefaults.TrailingIconSize)
                                        .graphicsLayer { rotationZ = chevronRotation }
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false },
                        shape = CustomShapes.Dropdown
                    ) {
                        if (!provider.isActive) {
                            DropdownMenuItem(
                                text = { Text("Test") },
                                onClick = {
                                    actionsExpanded = false
                                    onTestConnection()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                actionsExpanded = false
                                onDelete()
                            },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                        )
                    }
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
            .size(ExpressiveSizes.Tile)
            .clip(ExpressiveShapes.Tile)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (type) {
                ProviderType.OPENAI_COMPATIBLE -> Icons.Outlined.Cloud
                ProviderType.OLLAMA_NATIVE -> Icons.Outlined.Computer
                ProviderType.CODEX_NATIVE,
                ProviderType.GITHUB_COPILOT_NATIVE,
                ProviderType.ANTIGRAVITY_NATIVE -> Icons.Outlined.SmartToy
                ProviderType.LITERT_LM_LOCAL,
                ProviderType.AICORE_GEMINI_NANO -> Icons.Outlined.Memory
            },
            contentDescription = "${type.displayName} provider",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(ExpressiveSizes.TileIcon)
        )
    }
}

/**
 * Checkmark indicator for the active provider.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveIndicator(
    modifier: Modifier = Modifier
) {
    // Cookie badge: the one expressive shape on the card, reserved for "active".
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.onPrimaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Active",
            tint = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(ExpressiveSizes.IconSmall)
        )
    }
}

/**
 * Indicator showing whether a provider credential is configured.
 */
@Composable
private fun ApiKeyStatusIndicator(
    hasApiKey: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // The active card sits on primaryContainer, where `error` fell to 4.45:1 on dark VIOLET;
    // every colour here is resolved against the card fill it is actually on.
    val palette = rememberHomeSettingsPalette()
    val iconColor = if (hasApiKey) {
        palette[if (isActive) HomeSettingsSlot.ProviderCredentialOkActive else HomeSettingsSlot.ProviderCredentialOk]
    } else {
        palette[if (isActive) HomeSettingsSlot.ProviderCredentialErrorActive else HomeSettingsSlot.ProviderCredentialError]
    }
    val textColor = if (hasApiKey) {
        palette[if (isActive) HomeSettingsSlot.ProviderMetaActive else HomeSettingsSlot.ProviderMeta]
    } else {
        iconColor
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (hasApiKey) Icons.Outlined.Key else Icons.Outlined.KeyOff,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (hasApiKey) "Credential configured" else "No credential",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
