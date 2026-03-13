package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.MessageUiItem
import com.materialchat.ui.screens.chat.MessageGroupPosition
import com.materialchat.ui.screens.chat.SiblingInfo
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.MessageBubbleShapes
import kotlinx.coroutines.launch

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
 * @param onBookmarkToggle Optional callback when bookmark button is clicked (quick toggle)
 * @param onBookmarkLongPress Optional callback when bookmark button is long-pressed (open detail sheet)
 * @param isBookmarked Whether this message is currently bookmarked
 * @param onNavigatePrevious Optional callback to navigate to previous sibling
 * @param onNavigateNext Optional callback to navigate to next sibling
 * @param modifier Modifier for the bubble container
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageBubble(
    messageItem: MessageUiItem,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    onRedoWithModel: (() -> Unit)? = null,
    onBookmarkToggle: (() -> Unit)? = null,
    onBookmarkLongPress: (() -> Unit)? = null,
    isBookmarked: Boolean = false,
    onNavigatePrevious: (() -> Unit)? = null,
    onNavigateNext: (() -> Unit)? = null,
    alwaysShowThinking: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    isEditing: Boolean = false,
    editingText: String = "",
    onEditingTextChange: ((String) -> Unit)? = null,
    onSubmitEdit: (() -> Unit)? = null,
    onCancelEdit: (() -> Unit)? = null,
    onOpenCanvas: ((com.materialchat.domain.model.CanvasArtifact) -> Unit)? = null,
    onQuoteMessage: (() -> Unit)? = null,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val message = messageItem.message
    val isUser = message.role == MessageRole.USER
    val isAssistant = message.role == MessageRole.ASSISTANT
    val isSystem = message.role == MessageRole.SYSTEM

    // Context menu state for user messages (long-press floating bar)
    var showUserContextMenu by remember { mutableStateOf(false) }

    val haptics = rememberHapticFeedback()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Swipe-to-quote state (spring physics for gesture)
    val swipeOffset = remember { Animatable(0f) }
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val maxSwipe = with(density) { 96.dp.toPx() }
    val swipeActivationEdge = with(density) { 28.dp.toPx() }
    var hasTriggeredSwipeHaptic by remember { mutableStateOf(false) }
    var swipeGestureWidthPx by remember { mutableIntStateOf(0) }
    val swipeProgress = (swipeOffset.value / swipeThreshold).coerceIn(0f, 1f)

    // Double-tap bookmark burst state
    var bookmarkBurstTrigger by remember { mutableIntStateOf(0) }

    val bubbleStyle = getBubbleStyle(
        isUser = isUser,
        isAssistant = isAssistant,
        isSystem = isSystem,
        groupPosition = messageItem.groupPosition,
        isErrored = messageItem.isErrored
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
        // Swipe-to-quote reply icon (appears behind the bubble during swipe)
        if (onQuoteMessage != null && swipeOffset.value > 8f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Quote reply",
                modifier = Modifier
                    .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 8.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = swipeProgress
                        scaleX = 0.6f + (0.4f * swipeProgress)
                        scaleY = 0.6f + (0.4f * swipeProgress)
                    },
                tint = MaterialTheme.colorScheme.primary.copy(alpha = swipeProgress)
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier
                .onSizeChanged { swipeGestureWidthPx = it.width }
                .graphicsLayer {
                    translationX = if (isUser) -swipeOffset.value else swipeOffset.value
                }
                .then(
                    if (onQuoteMessage != null) {
                        Modifier.pointerInput(isUser, swipeGestureWidthPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startedNearQuoteEdge = if (isUser) {
                                    swipeGestureWidthPx > 0 &&
                                        down.position.x >= swipeGestureWidthPx - swipeActivationEdge
                                } else {
                                    down.position.x <= swipeActivationEdge
                                }
                                if (!startedNearQuoteEdge) {
                                    return@awaitEachGesture
                                }
                                var overSlop = 0f
                                var gestureClaimed = false
                                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                    val movingTowardQuote = if (isUser) over < 0f else over > 0f
                                    if (movingTowardQuote && !change.isConsumed) {
                                        change.consume()
                                        overSlop = over
                                        gestureClaimed = true
                                    }
                                }
                                if (drag != null && gestureClaimed) {
                                    val initDir = if (isUser) -overSlop else overSlop
                                    coroutineScope.launch {
                                        swipeOffset.snapTo(initDir.coerceIn(0f, maxSwipe))
                                    }
                                    val completed = horizontalDrag(drag.id) { change ->
                                        if (!change.isConsumed) {
                                            val dragAmount = change.positionChange().x
                                            change.consume()
                                            val direction = if (isUser) -dragAmount else dragAmount
                                            val newOffset = (swipeOffset.value + direction)
                                                .coerceIn(0f, maxSwipe)
                                            coroutineScope.launch { swipeOffset.snapTo(newOffset) }

                                            if (newOffset >= swipeThreshold && !hasTriggeredSwipeHaptic) {
                                                hasTriggeredSwipeHaptic = true
                                                haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
                                            } else if (newOffset < swipeThreshold) {
                                                hasTriggeredSwipeHaptic = false
                                            }
                                        }
                                    }
                                    if (completed && swipeOffset.value >= swipeThreshold) {
                                        onQuoteMessage()
                                    }
                                }
                                hasTriggeredSwipeHaptic = false
                                coroutineScope.launch {
                                    swipeOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.6f,
                                            stiffness = 500f
                                        )
                                    )
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            Box {
                Surface(
                    shape = bubbleStyle.shape,
                    color = bubbleStyle.backgroundColor,
                    modifier = Modifier
                        .widthIn(min = 40.dp, max = bubbleStyle.maxWidth)
                        .animateContentSize(
                            animationSpec = if (message.isStreaming) {
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 1400f
                                )
                            } else {
                                spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 380f
                                )
                            }
                        )
                        .then(
                            if (isUser && !isEditing && !message.isStreaming && message.content.isNotEmpty()) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { haptics.perform(HapticPattern.CLICK, hapticsEnabled); showUserContextMenu = true }
                                )
                            } else if (isAssistant && !message.isStreaming && message.content.isNotEmpty() && onBookmarkToggle != null) {
                                // Double-tap to bookmark (hero moment)
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onDoubleClick = {
                                        onBookmarkToggle()
                                        bookmarkBurstTrigger++
                                    }
                                )
                            } else Modifier
                        )
                ) {
                Column(
                    modifier = Modifier
                        .padding(
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
                            alwaysShowThinking = alwaysShowThinking,
                            hapticsEnabled = hapticsEnabled
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

                    // Message content or inline editing / error state
                    if (isUser && isEditing) {
                        EditingContent(
                            editingText = editingText,
                            onEditingTextChange = onEditingTextChange ?: {},
                            onSubmitEdit = onSubmitEdit ?: {},
                            onCancelEdit = onCancelEdit ?: {}
                        )
                    } else if (isAssistant && !message.isStreaming && message.content.isEmpty() && messageItem.isErrored) {
                        ErrorStateContent()
                    } else if (message.isStreaming && message.content.isEmpty() && message.thinkingContent.isNullOrEmpty()) {
                        // Initial streaming — compact M3 loading indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(3) {
                                LoadingIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = bubbleStyle.textColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else if (message.isStreaming && message.content.isEmpty()) {
                        // Thinking active, no main content yet — ThinkingSection handles indicator
                    } else {
                        MessageContent(
                            content = message.content,
                            isStreaming = message.isStreaming,
                            textColor = bubbleStyle.textColor,
                            isAssistant = isAssistant,
                            onOpenCanvas = onOpenCanvas,
                            hapticsEnabled = hapticsEnabled
                        )

                        // Web search sources carousel (for messages with search metadata)
                        if (isAssistant && !message.isStreaming) {
                            val webSearchMeta = remember(message.webSearchMetadata) {
                                message.webSearchMetadata?.let { json ->
                                    try {
                                        kotlinx.serialization.json.Json {
                                            ignoreUnknownKeys = true
                                        }.decodeFromString<com.materialchat.domain.model.WebSearchMetadata>(json)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                            if (webSearchMeta != null && webSearchMeta.results.isNotEmpty()) {
                                WebSearchSourcesCarousel(metadata = webSearchMeta)
                            }
                        }

                        // Streaming indicator below content
                        if (message.isStreaming) {
                            Spacer(modifier = Modifier.height(4.dp))
                            ShimmerSkeletonLines(
                                color = bubbleStyle.textColor,
                                lines = 1
                            )
                        }
                    }

                }
                }

                // Double-tap bookmark burst animation overlay (hero moment)
                if (isAssistant) {
                    BookmarkBurstAnimation(
                        trigger = bookmarkBurstTrigger,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // User message floating context menu (long-press)
                if (isUser && !isEditing) {
                    DropdownMenu(
                        expanded = showUserContextMenu,
                        onDismissRequest = { showUserContextMenu = false },
                        shape = CustomShapes.Dropdown
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { haptics.perform(HapticPattern.CLICK, hapticsEnabled); onCopy(); showUserContextMenu = false },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy, "Copy",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onEdit != null) {
                                IconButton(
                                    onClick = { haptics.perform(HapticPattern.CLICK, hapticsEnabled); onEdit(); showUserContextMenu = false },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit, "Edit",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (onBranch != null) {
                                IconButton(
                                    onClick = { haptics.perform(HapticPattern.CLICK, hapticsEnabled); onBranch(); showUserContextMenu = false },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.CallSplit, "Branch",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                    // Action buttons (including bookmark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        }

                        // Bookmark button — tap to toggle, long-press for detail sheet
                        if (onBookmarkToggle != null) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .combinedClickable(
                                        onClick = { haptics.perform(HapticPattern.CLICK, hapticsEnabled); onBookmarkToggle() },
                                        onLongClick = { haptics.perform(HapticPattern.CLICK, hapticsEnabled); onBookmarkLongPress?.invoke() }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark
                                                  else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isBookmarked) "Remove bookmark"
                                                         else "Bookmark message",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isBookmarked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }
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
            } else if (isAssistant && !message.isStreaming && message.content.isEmpty() && messageItem.isErrored && onRetry != null) {
                // Error/empty response: show retry button
                Spacer(modifier = Modifier.height(8.dp))
                RetryButton(onRetry = onRetry)
            }
        }
    }
}

/**
 * Message content text component.
 * Uses SmoothStreamingText for streaming assistant messages to normalize
 * patchy token delivery into fluid word-by-word reveal with per-word haptics.
 * Uses MarkdownText for completed assistant messages with full formatting.
 * Uses plain Text for user messages (with inline quote block rendering).
 */
@Composable
private fun MessageContent(
    content: String,
    isStreaming: Boolean,
    textColor: Color,
    isAssistant: Boolean,
    onOpenCanvas: ((com.materialchat.domain.model.CanvasArtifact) -> Unit)? = null,
    hapticsEnabled: Boolean = true
) {
    val displayContent = content.ifEmpty { "" }
    // Use chat-specific font size from user preferences (CompositionLocal)
    val chatFontSizeScale = com.materialchat.ui.theme.LocalChatFontSizeScale.current
    val chatStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * chatFontSizeScale,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * chatFontSizeScale
    )

    if (isAssistant && displayContent.isNotEmpty()) {
        SmoothStreamingText(
            rawText = displayContent,
            isStreaming = isStreaming,
            textColor = textColor,
            style = chatStyle,
            hapticsEnabled = hapticsEnabled,
            onOpenCanvas = onOpenCanvas
        )
    } else if (displayContent.isNotEmpty()) {
        // Parse and render quoted content visually for user messages
        val (quotedLines, bodyLines) = parseQuotedContent(displayContent)

        if (quotedLines.isNotEmpty()) {
            // Render visual quote block with accent bar
            Row(modifier = Modifier.padding(bottom = 8.dp).height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(textColor.copy(alpha = 0.4f))
                )
                Text(
                    text = quotedLines.joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.65f),
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (bodyLines.isNotEmpty()) {
            Text(
                text = bodyLines,
                style = chatStyle,
                color = textColor,
                overflow = TextOverflow.Clip
            )
        }

    }
}

/**
 * Parses message content into quoted lines (prefixed with `> `) and body text.
 * Returns a pair of (quoted lines without prefix, remaining body text).
 */
private fun parseQuotedContent(content: String): Pair<List<String>, String> {
    val lines = content.lines()
    val quotedLines = mutableListOf<String>()
    var bodyStartIndex = 0

    for ((index, line) in lines.withIndex()) {
        if (line.startsWith("> ")) {
            quotedLines.add(line.removePrefix("> "))
            bodyStartIndex = index + 1
        } else if (line.isEmpty() && quotedLines.isNotEmpty() && bodyStartIndex == index) {
            // Skip the blank line between quote and body
            bodyStartIndex = index + 1
        } else {
            break
        }
    }

    val bodyText = if (bodyStartIndex < lines.size) {
        lines.subList(bodyStartIndex, lines.size).joinToString("\n").trim()
    } else ""

    return quotedLines to bodyText
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
    groupPosition: MessageGroupPosition,
    isErrored: Boolean = false
): BubbleStyle {
    val configuration = LocalConfiguration.current
    val maxBubbleWidth = (configuration.screenWidthDp * 0.82f).dp
    val maxAssistantWidth = configuration.screenWidthDp.dp
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
            backgroundColor = if (isErrored) lerp(assistantBubble, MaterialTheme.colorScheme.errorContainer, 0.3f) else assistantBubble,
            textColor = MaterialTheme.colorScheme.onSurface,
            maxWidth = maxAssistantWidth
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
    alwaysShowThinking: Boolean = false,
    hapticsEnabled: Boolean = true
) {
    // Determine the current phase to drive auto-collapse
    // Phase key: true = thinking-only (expanded), false = content arrived or done
    val isThinkingPhase = isStreaming && !hasContent
    val haptics = rememberHapticFeedback()

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
                .clickable { haptics.perform(HapticPattern.CLICK, hapticsEnabled); isExpanded = !isExpanded }
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
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
            ) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            // Effects: no bounce for opacity; spatial spring for size
            exit = fadeOut(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
            ) + shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
            Column {
                SmoothStreamingThinkingText(
                    rawText = thinkingContent,
                    isStreaming = isStreaming,
                    textColor = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    hapticsEnabled = hapticsEnabled,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                )

                if (isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerSkeletonLines(
                        color = textColor,
                        lines = 1,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
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
    val haptics = rememberHapticFeedback()

    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Previous arrow
        IconButton(
            onClick = { haptics.perform(HapticPattern.CLICK); onNavigatePrevious?.invoke() },
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
            onClick = { haptics.perform(HapticPattern.CLICK); onNavigateNext?.invoke() },
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

/**
 * Error state content shown inside assistant bubble when response fails.
 */
@Composable
private fun ErrorStateContent() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Response failed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Retry button shown below errored assistant messages.
 * Uses errorContainer M3 color tokens.
 */
@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExpressiveButton(
        onClick = { onRetry() },
        text = "Retry",
        leadingIcon = Icons.Default.Refresh,
        style = ExpressiveButtonStyle.FilledTonal
    )
}

/**
 * Inline editing content for user messages.
 * Shows a text field with Cancel and Save & Submit buttons.
 */
@Composable
private fun EditingContent(
    editingText: String,
    onEditingTextChange: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val haptics = rememberHapticFeedback()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val chatFontSizeScale = com.materialchat.ui.theme.LocalChatFontSizeScale.current

    Column {
        BasicTextField(
            value = editingText,
            onValueChange = onEditingTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * chatFontSizeScale,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * chatFontSizeScale
            ),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveButton(onClick = { onCancelEdit() }, text = "Cancel", style = ExpressiveButtonStyle.Text)
            Spacer(modifier = Modifier.size(8.dp))
            ExpressiveButton(onClick = { onSubmitEdit() }, text = "Save & Submit", style = ExpressiveButtonStyle.FilledTonal)
        }
    }
}
