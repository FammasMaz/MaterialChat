package com.materialchat.assistant.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.materialchat.assistant.ui.AssistantCopy
import com.materialchat.assistant.ui.AssistantMessage
import com.materialchat.assistant.ui.AssistantMotion
import com.materialchat.assistant.ui.AssistantShapes
import com.materialchat.assistant.ui.AssistantSizes
import com.materialchat.assistant.ui.AssistantSpacing
import com.materialchat.assistant.ui.AssistantTypography
import com.materialchat.assistant.ui.rememberAssistantSurfacePalette
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.screens.chat.components.TypingIndicator
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.MessageBubbleShapes

/**
 * M3 Expressive response card for the assistant overlay.
 *
 * 100% compliant with main chat interface patterns:
 * - Uses MessageBubbleShapes from theme (asymmetric corners)
 * - A designed thinking surface (M3 Expressive loading indicator, bold upright title) while loading
 * - lerp color blending for depth
 * - Bouncy spring animations for content size changes
 * - Auto-scroll to newest message
 * - bodyLarge typography for readability
 *
 * @param messages The list of messages in the conversation
 * @param isLoading Whether a response is still loading
 * @param onOpenInApp Callback to open the conversation in the main app
 * @param modifier Modifier for the card
 * @param showContinueButton Whether to render the trailing "Continue in App" chip; the overlay's
 * response surface supplies its own action row and passes false.
 */
@Composable
fun AssistantResponseCard(
    messages: List<AssistantMessage>,
    isLoading: Boolean,
    onOpenInApp: () -> Unit,
    modifier: Modifier = Modifier,
    showContinueButton: Boolean = true
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
    val scrollBufferPx = with(density) { AssistantSpacing.Between.toPx() }

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
        verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Between)
    ) {
        // Message list with auto-scroll
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Between)
        ) {
            itemsIndexed(
                items = messages,
                key = { index, message -> "${message.role}_$index" }
            ) { _, message ->
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
                item(key = "thinking") {
                    AssistantThinkingSurface()
                }
            }
        }

        // Continue in App button (visible when there are messages, unless the host owns actions)
        if (showContinueButton && messages.isNotEmpty()) {
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

    // Bold tonal fill: the question sits in the full primary container on the tinted answer surface.
    val palette = rememberAssistantSurfacePalette()
    val userBubbleColor = palette.query.container

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End  // Right-aligned
    ) {
        Surface(
            modifier = Modifier.widthIn(min = AssistantSizes.QueryBubbleMin, max = maxBubbleWidth),
            shape = MessageBubbleShapes.UserBubble,  // Theme shape: 28dp/8dp/28dp/28dp
            color = userBubbleColor,
            tonalElevation = 2.dp
        ) {
            Text(
                text = query,
                style = AssistantTypography.query(MaterialTheme.typography),
                color = palette.query.content,
                modifier = Modifier.padding(horizontal = AssistantSpacing.Block, vertical = AssistantSpacing.Inset)
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
    // The answer is plain text on the response surface, like Gemini; only the question is a bubble.
    val palette = rememberAssistantSurfacePalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AssistantSpacing.Tight, vertical = AssistantSpacing.Tight)
            .animateContentSize(animationSpec = AssistantMotion.contentSize()),
        verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Between)
    ) {
        if (response.isNotEmpty()) {
            MarkdownText(
                markdown = response,
                textColor = palette.answer.body,
                style = AssistantTypography.answer(MaterialTheme.typography)
            )
        }
        if (isStreaming) {
            TypingIndicator(
                dotSize = AssistantSizes.TypingDot,
                dotSpacing = AssistantSpacing.Tight,
                color = palette.answer.indicator
            )
        }
    }
}

/**
 * The thinking state, shown while the reply is awaited: a real surface rather than three dots.
 *
 * - Container: an asymmetric M3 shape (one hero use of variable radii) whose tight bottom-start
 *   corner points down at the pill the answer will stream into, filled with the thinking tone
 *   (highest surface container leaning toward the secondary container) so it reads as a step
 *   below the answer that replaces it.
 * - Type: a bold, upright "Thinking" title over a regular-weight status line, both from
 *   [AssistantCopy]. Never italic.
 * - Motion: one spring entrance from the pill corner, and nothing continuous. The working
 *   indicator (the M3 Expressive loading indicator) is owned by the pill's glyph, which is
 *   visible in every phase; drawing a second one here put two morphing polygons on screen for
 *   the same state, so this surface is the quiet placeholder the answer streams into.
 */
@Composable
private fun AssistantThinkingSurface() {
    // One spring on appearance, anchored at the corner that points at the pill. A spring, not a
    // delay: the composition owns no timers (see AssistantOverlay's keep-awake contract).
    val entrance = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, AssistantMotion.surfaceReveal()) }
    val palette = rememberAssistantSurfacePalette()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier
                .padding(start = AssistantSpacing.Tight, top = AssistantSpacing.Tight, bottom = AssistantSpacing.Tight)
                .graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                    transformOrigin = TransformOrigin(0f, 1f)
                },
            shape = ExpressiveShapes.ThinkingSurface,
            color = palette.thinking.fill,
            contentColor = palette.thinking.body,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(start = AssistantSpacing.Block, end = AssistantSpacing.SurfaceInset, top = AssistantSpacing.Inset, bottom = AssistantSpacing.Inset),
                verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Hairline)
            ) {
                Text(
                    text = AssistantCopy.THINKING_TITLE,
                    style = AssistantTypography.thinkingTitle(MaterialTheme.typography),
                    color = palette.thinking.body
                )
                Text(
                    text = AssistantCopy.THINKING_STATUS,
                    style = AssistantTypography.thinkingStatus(MaterialTheme.typography),
                    color = palette.thinking.muted
                )
            }
        }
    }
}

/**
 * The card's own continue chip, drawn only when a host does not supply an action row (the
 * overlay passes `showContinueButton = false`). Kept in the pill's chip language: the 40dp
 * capsule, 16dp icon, bold action label, the palette's continue-chip pair.
 */
@Composable
private fun ContinueInAppButton(onClick: () -> Unit) {
    val palette = rememberAssistantSurfacePalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            onClick = onClick,
            shape = AssistantShapes.Chip,
            color = palette.continueChip.container,
            contentColor = palette.continueChip.content,
            modifier = Modifier.height(AssistantSizes.ChipHeight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AssistantSpacing.ChipInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AssistantSpacing.Gap)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(AssistantSizes.ChipIcon),
                    tint = palette.continueChip.content
                )
                Text(
                    text = AssistantCopy.CONTINUE_IN_APP,
                    style = AssistantTypography.action(MaterialTheme.typography),
                    color = palette.continueChip.content
                )
            }
        }
    }
}
