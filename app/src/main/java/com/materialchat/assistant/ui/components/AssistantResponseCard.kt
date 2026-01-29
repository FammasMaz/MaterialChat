package com.materialchat.assistant.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.materialchat.assistant.ui.AssistantMessage
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.screens.chat.components.TypingIndicator
import com.materialchat.ui.theme.MessageBubbleShapes

/**
 * M3 Expressive response card for the assistant overlay.
 *
 * 100% compliant with main chat interface patterns:
 * - Uses MessageBubbleShapes from theme (asymmetric corners)
 * - Uses TypingIndicator (M3TripleShapeIndicator) for loading
 * - lerp color blending for depth
 * - Bouncy spring animations for content size changes
 * - Auto-scroll to newest message
 * - bodyLarge typography for readability
 *
 * @param messages The list of messages in the conversation
 * @param isLoading Whether a response is still loading
 * @param onOpenInApp Callback to open the conversation in the main app
 * @param modifier Modifier for the card
 */
@Composable
fun AssistantResponseCard(
    messages: List<AssistantMessage>,
    isLoading: Boolean,
    onOpenInApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Calculate total items (messages + optional loading indicator)
    val hasLoadingItem = isLoading && (messages.isEmpty() || messages.lastOrNull()?.role != MessageRole.ASSISTANT)
    val totalItems = messages.size + if (hasLoadingItem) 1 else 0

    // Track content changes via hash (more efficient than full string comparison)
    val lastMessage = messages.lastOrNull()
    val lastContentHash = lastMessage?.content?.hashCode() ?: 0
    val isStreaming = lastMessage?.isStreaming ?: false

    // Scroll buffer for visual comfort
    val scrollBufferPx = with(density) { 8.dp.toPx() }

    // Robust auto-scroll matching main ChatScreen approach
    LaunchedEffect(
        messages.size,
        lastContentHash,
        isLoading,
        isStreaming
    ) {
        if (totalItems == 0) return@LaunchedEffect

        val lastIndex = totalItems - 1
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()

        if (lastVisible == null || lastVisible.index < lastIndex) {
            // Not showing last item - jump to it instantly
            listState.scrollToItem(lastIndex)
        } else {
            // Last item is visible - use incremental scroll if needed
            val overflow = (lastVisible.offset + lastVisible.size) - layoutInfo.viewportEndOffset + scrollBufferPx
            if (overflow > 0) {
                listState.scrollBy(overflow)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Message list with auto-scroll
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = messages,
                key = { "${it.role}_${it.content.hashCode()}_${messages.indexOf(it)}" }
            ) { message ->
                when (message.role) {
                    MessageRole.USER -> UserQueryBubble(query = message.content)
                    MessageRole.ASSISTANT -> AssistantBubble(
                        response = message.content,
                        isStreaming = message.isStreaming
                    )
                    else -> {} // System messages not shown in assistant overlay
                }
            }

            // Show loading indicator at the end if loading and no streaming message
            if (isLoading && (messages.isEmpty() || messages.lastOrNull()?.role != MessageRole.ASSISTANT)) {
                item {
                    AssistantLoadingBubble()
                }
            }
        }

        // Continue in App button (always visible when there are messages)
        if (messages.isNotEmpty()) {
            ContinueInAppButton(onClick = onOpenInApp)
        }
    }
}

/**
 * User query bubble - right-aligned with user colors.
 * Uses MessageBubbleShapes.UserBubble from theme for 100% compliance.
 */
@Composable
private fun UserQueryBubble(query: String) {
    val configuration = LocalConfiguration.current
    val maxBubbleWidth = (configuration.screenWidthDp * 0.82f).dp

    // Color blending matching MessageBubble.kt exactly
    val surfaceBase = MaterialTheme.colorScheme.surfaceContainer
    val userBubbleColor = lerp(surfaceBase, MaterialTheme.colorScheme.primaryContainer, 0.75f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End  // Right-aligned
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 40.dp, max = maxBubbleWidth),
            shape = MessageBubbleShapes.UserBubble,  // Theme shape: 28dp/8dp/28dp/28dp
            color = userBubbleColor,
            tonalElevation = 0.dp
        ) {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,  // Matches main chat
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Assistant response bubble - left-aligned with assistant colors.
 * Uses MessageBubbleShapes.AssistantBubble from theme for 100% compliance.
 */
@Composable
private fun AssistantBubble(
    response: String,
    isStreaming: Boolean
) {
    val configuration = LocalConfiguration.current
    val maxBubbleWidth = (configuration.screenWidthDp * 0.82f).dp

    // Color blending matching MessageBubble.kt exactly
    val surfaceBase = MaterialTheme.colorScheme.surfaceContainer
    val assistantBubbleColor = lerp(surfaceBase, MaterialTheme.colorScheme.surfaceContainerHigh, 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start  // Left-aligned
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 40.dp, max = maxBubbleWidth)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow  // Matches main chat
                    )
                ),
            shape = MessageBubbleShapes.AssistantBubble,  // Theme shape: 8dp/28dp/28dp/28dp
            color = assistantBubbleColor,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (response.isNotEmpty()) {
                    MarkdownText(
                        markdown = response,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge  // Matches main chat
                    )
                }

                // Streaming indicator using TypingIndicator (M3 shape morphing - matches main chat)
                if (isStreaming) {
                    TypingIndicator(
                        dotSize = 7.dp,
                        dotSpacing = 4.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Loading bubble shown while waiting for assistant response.
 * Uses TypingIndicator (M3TripleShapeIndicator) for 100% compliance.
 */
@Composable
private fun AssistantLoadingBubble() {
    val surfaceBase = MaterialTheme.colorScheme.surfaceContainer
    val assistantBubbleColor = lerp(surfaceBase, MaterialTheme.colorScheme.surfaceContainerHigh, 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 80.dp),
            shape = MessageBubbleShapes.AssistantBubble,  // Theme shape
            color = assistantBubbleColor,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // M3 Expressive TypingIndicator with shape morphing (matches main chat)
                TypingIndicator(
                    dotSize = 8.dp,
                    dotSpacing = 3.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact continue button styled as chip.
 */
@Composable
private fun ContinueInAppButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,  // 12dp from theme
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Continue in App",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
