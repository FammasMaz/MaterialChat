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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Action buttons for chat messages (copy, regenerate, branch, redo with model).
 *
 * Uses a small connected Material 3 Expressive button group. Each item morphs its
 * corner family on press instead of being a plain transparent icon button.
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
        if (showCopy) {
            add(MessageActionItem(Icons.Default.ContentCopy, "Copy message", MessageActionTone.Neutral, onCopy))
        }
        if (showEdit && onEdit != null) {
            add(MessageActionItem(Icons.Outlined.Edit, "Edit message", MessageActionTone.Secondary) { onEdit() })
        }
        if (showBranch && onBranch != null) {
            add(MessageActionItem(Icons.AutoMirrored.Outlined.CallSplit, "Branch conversation", MessageActionTone.Tertiary) { onBranch() })
        }
        if (showRedoWithModel && onRedoWithModel != null) {
            add(MessageActionItem(Icons.Outlined.SwapHoriz, "Redo with different model", MessageActionTone.Secondary) { onRedoWithModel() })
        }
        if (showRegenerate && onRegenerate != null) {
            add(MessageActionItem(Icons.Default.Refresh, "Regenerate response", MessageActionTone.Primary) { onRegenerate() })
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        actions.forEachIndexed { index, action ->
            ActionButton(
                item = action,
                index = index,
                count = actions.size
            )
        }
    }
}

private data class MessageActionItem(
    val icon: ImageVector,
    val contentDescription: String,
    val tone: MessageActionTone,
    val onClick: () -> Unit
)

private enum class MessageActionTone { Neutral, Primary, Secondary, Tertiary }

@Composable
private fun ActionButton(
    item: MessageActionItem,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "messageActionScale"
    )
    val shape = expressiveActionShape(index, count, isPressed)
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
    val width by animateDpAsState(
        targetValue = if (item.tone == MessageActionTone.Primary) 54.dp else 48.dp,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "messageActionWidth"
    )

    Surface(
        onClick = {
            haptics.perform(
                if (item.tone == MessageActionTone.Primary) HapticPattern.MORPH_TRANSITION
                else HapticPattern.CLICK
            )
            item.onClick()
        },
        modifier = modifier
            .size(width = width, height = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (item.tone == MessageActionTone.Neutral) 1.dp else 3.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun expressiveActionShape(
    index: Int,
    count: Int,
    pressed: Boolean
): androidx.compose.foundation.shape.RoundedCornerShape {
    @Composable
    fun animated(target: Dp, label: String): Dp {
        val value by animateDpAsState(
            targetValue = target,
            animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
            label = label
        )
        return value
    }

    val isFirst = index == 0
    val isLast = index == count - 1
    val outer = if (pressed) 18.dp else 24.dp
    val inner = if (pressed) 26.dp else 13.dp
    val single = if (pressed) 14.dp else 24.dp

    return if (count == 1) {
        androidx.compose.foundation.shape.RoundedCornerShape(animated(single, "singleActionShape"))
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = animated(if (isFirst) outer else inner, "actionTopStart"),
            topEnd = animated(if (isLast) outer else inner, "actionTopEnd"),
            bottomStart = animated(if (isFirst) outer else inner, "actionBottomStart"),
            bottomEnd = animated(if (isLast) outer else inner, "actionBottomEnd")
        )
    }
}

@Composable
private fun actionColors(tone: MessageActionTone): ActionColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        MessageActionTone.Neutral -> ActionColors(
            container = scheme.surfaceContainerHigh,
            pressedContainer = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant
        )
        MessageActionTone.Primary -> ActionColors(
            container = scheme.primaryContainer,
            pressedContainer = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        )
        MessageActionTone.Secondary -> ActionColors(
            container = scheme.secondaryContainer,
            pressedContainer = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer
        )
        MessageActionTone.Tertiary -> ActionColors(
            container = scheme.tertiaryContainer,
            pressedContainer = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer
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
