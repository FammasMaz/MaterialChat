package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.screens.chat.MessageUiItem
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
                    // Thinking content (collapsible for assistant messages)
                    if (isAssistant && !message.thinkingContent.isNullOrEmpty()) {
                        ThinkingSection(
                            thinkingContent = message.thinkingContent,
                            textColor = bubbleStyle.textColor,
                            isStreaming = message.isStreaming
                        )
                        if (message.content.isNotEmpty() || message.hasAttachments) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Image attachments (displayed before text content)
                    if (message.hasAttachments) {
                        AttachmentImagesGrid(
                            attachments = message.attachments
                        )
                        if (message.content.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Message content
                    MessageContent(
                        content = message.content,
                        isStreaming = message.isStreaming,
                        textColor = bubbleStyle.textColor,
                        isAssistant = isAssistant
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
                    showCopy = true,
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
 * Uses MarkdownText for assistant messages to render formatting,
 * and plain Text for user messages.
 */
@Composable
private fun MessageContent(
    content: String,
    isStreaming: Boolean,
    textColor: Color,
    isAssistant: Boolean
) {
    val displayContent = content.ifEmpty { "" }

    if (isAssistant && displayContent.isNotEmpty()) {
        // Render assistant messages with Markdown
        MarkdownText(
            markdown = displayContent,
            textColor = textColor,
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        // Render user messages as plain text
        Text(
            text = displayContent,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            overflow = TextOverflow.Clip
        )
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

/**
 * Collapsible thinking section for displaying model reasoning.
 * Shows thinking content in a muted, italic style with expand/collapse toggle.
 */
@Composable
private fun ThinkingSection(
    thinkingContent: String,
    textColor: Color,
    isStreaming: Boolean
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Column {
        // Header row with toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse thinking" else "Expand thinking",
                modifier = Modifier
                    .size(16.dp)
                    .alpha(0.6f),
                tint = textColor
            )
            Text(
                text = if (isStreaming) "Thinking..." else "Thinking",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.6f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        
        // Collapsible thinking content
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = thinkingContent,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Grid layout for displaying image attachments in a message.
 * Uses FlowRow for responsive layout that wraps images.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentImagesGrid(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentImage(attachment = attachment)
        }
    }
}

/**
 * Single image attachment display.
 */
@Composable
private fun AttachmentImage(
    attachment: Attachment,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(attachment.uri)
            .crossfade(true)
            .build(),
        contentDescription = "Attached image",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
