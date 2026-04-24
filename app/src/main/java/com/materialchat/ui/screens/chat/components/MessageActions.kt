package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
 * Subtle message action toolbar.
 *
 * These actions intentionally use separate, matching tonal icon containers instead
 * of a connected group. That keeps the row calm under chat bubbles while still
 * using M3 Expressive press morphs, spring scale, haptics, and emphasis color.
 */
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "messageActionScale"
    )
    val radius by animateDpAsState(
        targetValue = when {
            isPressed -> 13.dp
            emphasized -> 20.dp
            else -> 18.dp
        },
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
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(radius),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (emphasized) 3.dp else 1.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.contentDescription,
                modifier = Modifier.size(if (emphasized) 21.dp else 20.dp),
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
            container = scheme.surfaceContainerHigh.copy(alpha = 0.78f),
            pressedContainer = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant
        )
        MessageActionTone.Primary -> ActionColors(
            container = scheme.primaryContainer,
            pressedContainer = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        )
        MessageActionTone.Secondary -> ActionColors(
            container = scheme.secondaryContainer.copy(alpha = 0.74f),
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
