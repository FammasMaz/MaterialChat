package com.materialchat.ui.screens.openclaw.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.openclaw.OpenClawChatMessage
import com.materialchat.domain.model.openclaw.OpenClawChatRole
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.theme.MessageBubbleShapes

/**
 * Chat bubble for OpenClaw messages supporting role-based styling,
 * thinking content, and tool call cards.
 *
 * Uses M3 Expressive shape system for bubble corners and spring-based
 * content size animations.
 *
 * @param message The OpenClaw chat message to display
 * @param modifier Modifier for the bubble
 */
@Composable
fun OpenClawMessageBubble(
    message: OpenClawChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == OpenClawChatRole.USER
    val isSystem = message.role == OpenClawChatRole.SYSTEM

    val alignment = when {
        isUser -> Alignment.End
        else -> Alignment.Start
    }

    val bubbleShape = when {
        isUser -> MessageBubbleShapes.UserBubble
        isSystem -> MessageBubbleShapes.SystemBubble
        else -> MessageBubbleShapes.AssistantBubble
    }

    val containerColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isSystem -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        // Role label
        if (!isUser) {
            Text(
                text = when (message.role) {
                    OpenClawChatRole.ASSISTANT -> "Agent"
                    OpenClawChatRole.SYSTEM -> "System"
                    OpenClawChatRole.TOOL -> "Tool"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        Surface(
            shape = bubbleShape,
            color = containerColor,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 500f
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Thinking content (expandable)
                if (!message.thinkingContent.isNullOrBlank()) {
                    ThinkingSection(
                        thinkingContent = message.thinkingContent,
                        contentColor = contentColor
                    )
                }

                // Main message content with markdown rendering
                if (message.content.isNotBlank()) {
                    MarkdownText(
                        markdown = message.content,
                        textColor = contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Tool calls
                if (message.toolCalls.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.toolCalls.forEach { toolCall ->
                            ToolCallCard(
                                toolCall = toolCall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expandable thinking/reasoning section within a message bubble.
 */
@Composable
private fun ThinkingSection(
    thinkingContent: String,
    contentColor: androidx.compose.ui.graphics.Color
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = "Thinking",
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (expanded) "Thinking" else "Thinking...",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.6f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)
            ) + fadeIn(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)
            ),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)
            ) + fadeOut(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)
            )
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = contentColor.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                MarkdownText(
                    markdown = thinkingContent,
                    modifier = Modifier.padding(12.dp),
                    textColor = contentColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!expanded && thinkingContent.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
