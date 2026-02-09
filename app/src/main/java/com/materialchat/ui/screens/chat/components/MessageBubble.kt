package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
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
import com.materialchat.ui.screens.chat.MessageGroupPosition
import com.materialchat.ui.screens.chat.SiblingInfo
import com.materialchat.ui.theme.MessageBubbleShapes

/**
 * A chat message bubble component following Material 3 Expressive design.
 *
 * Features:
 * - Different styles for user, assistant, and system messages
 * - Directional corner styling (user: square top-right, assistant: square top-left)
 * - Animated content size for streaming messages
 * - Copy, regenerate, and branch action buttons
 * - Streaming indicator for in-progress responses
 *
 * @param messageItem The message UI item containing message data and display flags
 * @param onCopy Callback when copy button is clicked
 * @param onRegenerate Optional callback when regenerate button is clicked (only for last assistant message)
 * @param onBranch Optional callback when branch button is clicked
 * @param onRedoWithModel Optional callback when redo with model button is clicked
 * @param onNavigatePrevious Optional callback to navigate to previous sibling
 * @param onNavigateNext Optional callback to navigate to next sibling
 * @param modifier Modifier for the bubble container
 */
@Composable
fun MessageBubble(
    messageItem: MessageUiItem,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    onRedoWithModel: (() -> Unit)? = null,
    onNavigatePrevious: (() -> Unit)? = null,
    onNavigateNext: (() -> Unit)? = null,
    alwaysShowThinking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val message = messageItem.message
    val isUser = message.role == MessageRole.USER
    val isAssistant = message.role == MessageRole.ASSISTANT
    val isSystem = message.role == MessageRole.SYSTEM

    val bubbleStyle = getBubbleStyle(
        isUser = isUser,
        isAssistant = isAssistant,
        isSystem = isSystem,
        groupPosition = messageItem.groupPosition
    )

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
                        horizontal = 14.dp,
                        vertical = 10.dp
                    )
                ) {
                    // Thinking content (collapsible for assistant messages)
                    if (isAssistant && !message.thinkingContent.isNullOrEmpty()) {
                        ThinkingSection(
                            thinkingContent = message.thinkingContent,
                            textColor = bubbleStyle.textColor,
                            isStreaming = message.isStreaming,
                            hasContent = message.content.isNotEmpty(),
                            thinkingDurationMs = message.thinkingDurationMs,
                            alwaysShowThinking = alwaysShowThinking
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
                        TypingIndicator(
                            dotSize = 7.dp,
                            dotSpacing = 4.dp,
                            color = bubbleStyle.textColor.copy(alpha = 0.6f)
                        )
                    }

                }
            }

            // Action buttons, model label, and sibling navigation (below the bubble)
            if (isAssistant && !message.isStreaming && message.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))

                // Sibling navigation row (model label + arrows)
                val siblingInfo = messageItem.siblingInfo
                if (siblingInfo != null && siblingInfo.totalCount > 1) {
                    ModelSiblingRow(
                        siblingInfo = siblingInfo,
                        modelName = message.modelName,
                        onNavigatePrevious = onNavigatePrevious,
                        onNavigateNext = onNavigateNext
                    )
                } else if (messageItem.showModelLabel && message.modelName != null) {
                    // Only show model label when the model was switched (differs from conversation default)
                    Text(
                        text = message.modelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action buttons
                    if (messageItem.showActions) {
                        MessageActions(
                            showCopy = true,
                            showRegenerate = messageItem.isLastAssistantMessage && onRegenerate != null,
                            showBranch = onBranch != null,
                            showRedoWithModel = messageItem.isLastAssistantMessage && onRedoWithModel != null,
                            onCopy = onCopy,
                            onRegenerate = onRegenerate,
                            onBranch = onBranch,
                            onRedoWithModel = onRedoWithModel
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Reply time
                    if (message.totalDurationMs != null) {
                        Text(
                            text = formatDuration(message.totalDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            } else if (!isAssistant && messageItem.showActions && message.content.isNotEmpty() && !message.isStreaming) {
                Spacer(modifier = Modifier.height(4.dp))
                MessageActions(
                    showCopy = true,
                    showRegenerate = false,
                    showBranch = onBranch != null,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate,
                    onBranch = onBranch
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
    isSystem: Boolean,
    groupPosition: MessageGroupPosition
): BubbleStyle {
    val configuration = LocalConfiguration.current
    val maxBubbleWidth = (configuration.screenWidthDp * 0.82f).dp
    val maxSystemWidth = (configuration.screenWidthDp * 0.7f).dp
    val surfaceBase = MaterialTheme.colorScheme.surfaceContainer
    val userBubble = lerp(surfaceBase, MaterialTheme.colorScheme.primaryContainer, 0.75f)
    val assistantBubble = lerp(surfaceBase, MaterialTheme.colorScheme.surfaceContainerHigh, 0.7f)
    val systemBubble = lerp(surfaceBase, MaterialTheme.colorScheme.tertiaryContainer, 0.55f)

    return when {
        isUser -> BubbleStyle(
            shape = when (groupPosition) {
                MessageGroupPosition.First -> MessageBubbleShapes.Grouped.UserFirst
                MessageGroupPosition.Middle -> MessageBubbleShapes.Grouped.UserMiddle
                MessageGroupPosition.Last -> MessageBubbleShapes.Grouped.UserLast
                MessageGroupPosition.Single -> MessageBubbleShapes.UserBubble
            },
            backgroundColor = userBubble,
            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            maxWidth = maxBubbleWidth
        )
        isAssistant -> BubbleStyle(
            shape = when (groupPosition) {
                MessageGroupPosition.First -> MessageBubbleShapes.Grouped.AssistantFirst
                MessageGroupPosition.Middle -> MessageBubbleShapes.Grouped.AssistantMiddle
                MessageGroupPosition.Last -> MessageBubbleShapes.Grouped.AssistantLast
                MessageGroupPosition.Single -> MessageBubbleShapes.AssistantBubble
            },
            backgroundColor = assistantBubble,
            textColor = MaterialTheme.colorScheme.onSurface,
            maxWidth = maxBubbleWidth
        )
        else -> BubbleStyle(
            shape = MessageBubbleShapes.SystemBubble,
            backgroundColor = systemBubble,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            maxWidth = maxSystemWidth
        )
    }
}

/**
 * Formats a duration in milliseconds to a human-readable string.
 * Returns "<1s", "Xs", or "Xm Ys" as appropriate.
 */
private fun formatDuration(ms: Long?): String {
    if (ms == null) return ""
    val totalSeconds = ms / 1000
    return when {
        totalSeconds < 1 -> "<1s"
        totalSeconds < 60 -> "${totalSeconds}s"
        else -> {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
        }
    }
}

/**
 * Collapsible thinking section for displaying model reasoning.
 * Shows thinking content in a muted, italic style with expand/collapse toggle.
 */
/**
 * Collapsible thinking section for displaying model reasoning.
 *
 * Behavior:
 * - Expanded while streaming thinking-only content (no response content yet)
 * - Auto-collapses when response content starts streaming
 * - Collapsed after completion (unless alwaysShowThinking is enabled)
 * - User can manually toggle at any time
 *
 * Motion follows M3 Expressive spring physics:
 * - Spatial (expand/shrink): spring with overshoot for expressive feel
 * - Effects (fade): spring with no bounce per M3 guidelines
 */
@Composable
private fun ThinkingSection(
    thinkingContent: String,
    textColor: Color,
    isStreaming: Boolean,
    hasContent: Boolean = false,
    thinkingDurationMs: Long? = null,
    alwaysShowThinking: Boolean = false
) {
    // Determine the current phase to drive auto-collapse
    // Phase key: true = thinking-only (expanded), false = content arrived or done
    val isThinkingPhase = isStreaming && !hasContent

    var isExpanded by remember(isThinkingPhase, isStreaming) {
        mutableStateOf(
            when {
                isThinkingPhase -> true              // Still thinking only — expanded
                isStreaming -> false                   // Content started streaming — auto-collapse
                else -> alwaysShowThinking            // Done — respect user setting
            }
        )
    }

    val headerText = when {
        isThinkingPhase -> "Thinking..."
        thinkingDurationMs != null -> "Thought for ${formatDuration(thinkingDurationMs)}"
        isStreaming -> "Thinking..."
        else -> "Thought"
    }

    Column {
        // Header row — tappable toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
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
                text = headerText,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.6f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Collapsible thinking content with M3 spring physics
        AnimatedVisibility(
            visible = isExpanded,
            // Spatial: spring with overshoot (expressive scheme, default speed)
            enter = fadeIn(
                animationSpec = tween(durationMillis = 150)
            ) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            // Effects: no bounce for opacity; spatial spring for size
            exit = fadeOut(
                animationSpec = tween(durationMillis = 120)
            ) + shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
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
 * Row showing model name with left/right sibling navigation arrows.
 * Displayed below the assistant bubble when multiple siblings exist.
 */
@Composable
private fun ModelSiblingRow(
    siblingInfo: SiblingInfo,
    modelName: String?,
    onNavigatePrevious: (() -> Unit)?,
    onNavigateNext: (() -> Unit)?
) {
    val canGoPrevious = siblingInfo.currentIndex > 0
    val canGoNext = siblingInfo.currentIndex < siblingInfo.totalCount - 1
    val displayName = modelName ?: siblingInfo.siblings.getOrNull(siblingInfo.currentIndex)?.modelName ?: ""

    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Previous arrow
        IconButton(
            onClick = { onNavigatePrevious?.invoke() },
            enabled = canGoPrevious,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous response",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (canGoPrevious) 0.6f else 0.25f
                )
            )
        }

        // Model name and count
        Text(
            text = "$displayName (${siblingInfo.currentIndex + 1}/${siblingInfo.totalCount})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        // Next arrow
        IconButton(
            onClick = { onNavigateNext?.invoke() },
            enabled = canGoNext,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next response",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (canGoNext) 0.6f else 0.25f
                )
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
