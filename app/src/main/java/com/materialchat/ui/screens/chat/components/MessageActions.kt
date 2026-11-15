package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.materialchat.ui.components.seaglass.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Message action toolbar: M3 Expressive icon buttons with [IconButtonDefaults.shapes]
 * press morphs, grouped in a tonal pill and laid out by [ButtonGroup] so the pressed
 * button widens while its neighbours give way.
 *
 * Tones: neutral actions are standard icon buttons on the pill, secondary actions
 * (branch, redo) are tonal, and the primary action (regenerate) is filled.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageActions(
    showCopy: Boolean = true,
    showRegenerate: Boolean = false,
    showBranch: Boolean = false,
    showRedoWithModel: Boolean = false,
    showEdit: Boolean = false,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    onRedoWithModel: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val actions = buildList {
        if (showCopy) add(MessageActionItem(Icons.Default.ContentCopy, "Copy message", MessageActionTone.Neutral, onCopy))
        if (showEdit && onEdit != null) add(MessageActionItem(Icons.Outlined.Edit, "Edit message", MessageActionTone.Neutral) { onEdit() })
        if (showBranch && onBranch != null) add(MessageActionItem(Icons.AutoMirrored.Outlined.CallSplit, "Branch conversation", MessageActionTone.Secondary) { onBranch() })
        if (showRedoWithModel && onRedoWithModel != null) add(MessageActionItem(Icons.Outlined.SwapHoriz, "Redo with different model", MessageActionTone.Secondary) { onRedoWithModel() })
        if (showRegenerate && onRegenerate != null) add(MessageActionItem(Icons.Default.Refresh, "Regenerate response", MessageActionTone.Primary) { onRegenerate() })
    }
    if (actions.isEmpty()) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        ButtonGroup(
            modifier = Modifier.padding(2.dp),
            expandedRatio = 0.2f,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            actions.forEach { action ->
                GroupedActionButton(item = action)
            }
        }
    }
}

private data class MessageActionItem(
    val icon: ImageVector,
    val contentDescription: String,
    val tone: MessageActionTone,
    val onClick: () -> Unit
)

private enum class MessageActionTone { Neutral, Primary, Secondary }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ButtonGroupScope.GroupedActionButton(item: MessageActionItem) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val emphasized = item.tone != MessageActionTone.Neutral
    val onClick = {
        haptics.perform(if (emphasized) HapticPattern.MORPH_TRANSITION else HapticPattern.CLICK)
        item.onClick()
    }
    val buttonModifier = Modifier.animateWidth(interactionSource)
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
    when (item.tone) {
        MessageActionTone.Neutral -> IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = buttonModifier,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            interactionSource = interactionSource,
            content = content
        )
        MessageActionTone.Secondary -> FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = buttonModifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            interactionSource = interactionSource,
            content = content
        )
        MessageActionTone.Primary -> FilledIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = buttonModifier,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            interactionSource = interactionSource,
            content = content
        )
    }
}

/**
 * Compact version of message actions shown inline.
 */
@Composable
fun CompactMessageActions(
    isUser: Boolean,
    showRegenerate: Boolean,
    showBranch: Boolean,
    showRedoWithModel: Boolean = false,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onBranch: (() -> Unit)?,
    onRedoWithModel: (() -> Unit)? = null
) {
    MessageActions(
        showCopy = true,
        showRegenerate = showRegenerate && onRegenerate != null,
        showBranch = showBranch && onBranch != null,
        showRedoWithModel = showRedoWithModel && onRedoWithModel != null,
        onCopy = onCopy,
        onRegenerate = onRegenerate,
        onBranch = onBranch,
        onRedoWithModel = onRedoWithModel
    )
}

/**
 * A single element that morphs between a compact 44dp bolt button and an
 * expanded stats pill in place — used for response speed after streaming.
 *
 * All expansion values (width via animateContentSize, horizontal padding via
 * animateDpAsState) share one spring so the bolt slides smoothly instead of
 * jumping when the padding snaps between 0dp and 14dp.
 */
@Composable
fun StatsPillButton(
    expanded: Boolean,
    tpsText: String?,
    ttftText: String?,
    avgText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    totalText: String? = null
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "statsPillScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "statsPillContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "statsPillContent"
    )

    Surface(
        onClick = {
            haptics.perform(HapticPattern.CLICK)
            onClick()
        },
        modifier = modifier
            .height(44.dp)
            .defaultMinSize(minWidth = 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = ExpressiveMotion.Spatial.contentSize()
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = if (expanded) "Hide response speed"
                                     else "Response speed",
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (tpsText != null) {
                        Text(text = tpsText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (ttftText != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall)
                        Text(text = ttftText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (totalText != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall)
                        Text(text = totalText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (avgText != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall)
                        Text(text = avgText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
