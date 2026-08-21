package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Message action toolbar using compact icon buttons with the app's springy
 * expressive button language.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            ActionButton(item = action)
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

@Composable
private fun ActionButton(
    item: MessageActionItem,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val emphasized = item.tone != MessageActionTone.Neutral

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "messageActionScale"
    )
    val radius by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 22.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "messageActionRadius"
    )
    val colors = actionColors(item.tone)
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) colors.pressedContainer else colors.container,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "messageActionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = colors.content,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "messageActionContent"
    )

    Surface(
        onClick = {
            haptics.perform(if (emphasized) HapticPattern.MORPH_TRANSITION else HapticPattern.CLICK)
            item.onClick()
        },
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(radius),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
private fun actionColors(tone: MessageActionTone): ActionColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        MessageActionTone.Neutral -> ActionColors(
            container = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
            pressedContainer = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant
        )
        MessageActionTone.Primary -> ActionColors(
            container = scheme.primaryContainer,
            pressedContainer = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        )
        MessageActionTone.Secondary -> ActionColors(
            container = scheme.secondaryContainer.copy(alpha = 0.88f),
            pressedContainer = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer
        )
    }
}

private data class ActionColors(
    val container: Color,
    val pressedContainer: Color,
    val content: Color
)

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
 */
@Composable
fun StatsPillButton(
    expanded: Boolean,
    tpsText: String?,
    ttftText: String?,
    avgText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            .size(height = 44.dp, width = 44.dp)
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
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)
                )
                .padding(horizontal = if (expanded) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = if (expanded) "Hide response speed"
                                     else "Response speed",
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            AnimatedVisibility(visible = expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (tpsText != null) {
                        Text(text = tpsText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (ttftText != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall)
                        Text(text = "TTFT $ttftText", style = MaterialTheme.typography.labelSmall)
                    }
                    if (avgText != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall)
                        Text(text = "avg $avgText", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
