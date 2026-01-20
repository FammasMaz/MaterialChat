package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.MessageUiItem
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.MessageBubbleShapes

/**
 * A chat message bubble component following Material 3 Expressive design.
 *
 * Features:
 * - Different styles for user, assistant, and system messages
 * - Directional corner styling (user: square top-right, assistant: square top-left)
 * - Animated content size for streaming messages
 * - Copy and regenerate action buttons
 * - Streaming indicator for in-progress responses
 *
 * @param messageItem The message UI item containing message data and display flags
 * @param onCopy Callback when copy button is clicked
 * @param onRegenerate Optional callback when regenerate button is clicked (only for last assistant message)
 * @param modifier Modifier for the bubble container
 */
@Composable
fun MessageBubble(
    messageItem: MessageUiItem,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val message = messageItem.message
    val isUser = message.role == MessageRole.USER
    val isAssistant = message.role == MessageRole.ASSISTANT
    val isSystem = message.role == MessageRole.SYSTEM

    val bubbleStyle = getBubbleStyle(isUser, isAssistant, isSystem)

    val alignment = when {
        isUser -> Alignment.CenterEnd
        isAssistant -> Alignment.CenterStart
        else -> Alignment.Center
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = bubbleStyle.shape,
                color = bubbleStyle.backgroundColor,
                modifier = Modifier
                    .widthIn(min = 40.dp, max = bubbleStyle.maxWidth)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    // Message content
                    MessageContent(
                        content = message.content,
                        isStreaming = message.isStreaming,
                        textColor = bubbleStyle.textColor
                    )

                    // Streaming indicator
                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamingIndicator()
                    }
                }
            }

            // Action buttons (copy, regenerate)
            if (messageItem.showActions && message.content.isNotEmpty() && !message.isStreaming) {
                Spacer(modifier = Modifier.height(4.dp))
                MessageActions(
                    isUser = isUser,
                    showRegenerate = messageItem.isLastAssistantMessage && onRegenerate != null,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate
                )
            }
        }
    }
}

/**
 * Message content text component.
 */
@Composable
private fun MessageContent(
    content: String,
    isStreaming: Boolean,
    textColor: Color
) {
    val displayContent = content.ifEmpty {
        if (isStreaming) "..." else ""
    }

    Text(
        text = displayContent,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        overflow = TextOverflow.Clip
    )
}

/**
 * Action buttons for a message (copy, regenerate).
 */
@Composable
private fun MessageActions(
    isUser: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy button
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy message",
                modifier = Modifier.size(16.dp)
            )
        }

        // Regenerate button (only for last assistant message)
        if (showRegenerate && onRegenerate != null) {
            IconButton(
                onClick = onRegenerate,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Regenerate response",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Data class holding visual styling for a message bubble.
 */
private data class BubbleStyle(
    val shape: Shape,
    val backgroundColor: Color,
    val textColor: Color,
    val maxWidth: Dp
)

/**
 * Determines the visual style for a message bubble based on role.
 */
@Composable
private fun getBubbleStyle(
    isUser: Boolean,
    isAssistant: Boolean,
    isSystem: Boolean
): BubbleStyle {
    return when {
        isUser -> BubbleStyle(
            shape = MessageBubbleShapes.UserBubble,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            maxWidth = 340.dp
        )
        isAssistant -> BubbleStyle(
            shape = MessageBubbleShapes.AssistantBubble,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            maxWidth = 340.dp
        )
        else -> BubbleStyle(
            shape = MessageBubbleShapes.SystemBubble,
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            maxWidth = 280.dp
        )
    }
}
