package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.components.seaglass.Surface
import com.materialchat.ui.theme.ExpressiveMotion

/** The actions an assistant message can offer, in their display order. */
enum class MessageActionKind { Copy, Branch, RedoWithModel, Regenerate }

/** Tonal hierarchy inside the row: one container language, two tones. */
enum class MessageActionEmphasis { Standard, Primary }

data class MessageActionSpec(
    val kind: MessageActionKind,
    val emphasis: MessageActionEmphasis
)

/**
 * Pure visibility/ordering rule for the action row: copy, branch, redo-with-model, then the
 * primary regenerate action last so the emphasised control closes the group.
 */
fun messageActionSpecs(
    showCopy: Boolean,
    showBranch: Boolean,
    showRedoWithModel: Boolean,
    showRegenerate: Boolean
): List<MessageActionSpec> = buildList {
    if (showCopy) add(MessageActionSpec(MessageActionKind.Copy, MessageActionEmphasis.Standard))
    if (showBranch) add(MessageActionSpec(MessageActionKind.Branch, MessageActionEmphasis.Standard))
    if (showRedoWithModel) add(MessageActionSpec(MessageActionKind.RedoWithModel, MessageActionEmphasis.Standard))
    if (showRegenerate) add(MessageActionSpec(MessageActionKind.Regenerate, MessageActionEmphasis.Primary))
}

/** Pre-formatted speed figures shown in the expanded stats pill. */
data class MessageSpeedStats(
    val tps: String? = null,
    val ttft: String? = null,
    val total: String? = null,
    val avg: String? = null
) {
    /** The non-null figures in display order (tok/s, first token, total, average). */
    fun segments(): List<String> = listOfNotNull(tps, ttft, total, avg)
}

data class BookmarkActionState(
    val isBookmarked: Boolean,
    val onToggle: () -> Unit,
    val onLongPress: (() -> Unit)? = null
)

/** Height of every control in the row; icon-only controls are square at this size. */
internal val MessageActionRowItemSize: Dp = 40.dp
internal val MessageActionRowSpacing: Dp = 6.dp

/**
 * The action row under an assistant message as one M3 Expressive group.
 *
 * Every control (copy, branch, redo with model, regenerate, speed stats, bookmark) shares one
 * container: a 40dp tonal pill that morphs toward a rounded square and dips in scale while
 * pressed, with hierarchy expressed by tone only. Standard actions sit on
 * surfaceContainerHigh, the primary action (regenerate) on primaryContainer, and toggled
 * states (bookmarked, stats expanded) on secondaryContainer.
 *
 * Controls flow into further lines on narrow widths instead of squeezing; the elapsed-time
 * label keeps the trailing edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageActionRow(
    specs: List<MessageActionSpec>,
    onAction: (MessageActionKind) -> Unit,
    stats: MessageSpeedStats?,
    statsExpanded: Boolean,
    onToggleStats: () -> Unit,
    bookmark: BookmarkActionState?,
    elapsedLabel: String?,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    if (specs.isEmpty() && stats == null && bookmark == null && elapsedLabel == null) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MessageActionRowSpacing),
            verticalArrangement = Arrangement.spacedBy(MessageActionRowSpacing),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            specs.forEach { spec ->
                val primary = spec.emphasis == MessageActionEmphasis.Primary
                ActionContainer(
                    tone = if (primary) ActionTone.Primary else ActionTone.Standard,
                    hapticPattern = if (primary) HapticPattern.MORPH_TRANSITION else HapticPattern.CLICK,
                    hapticsEnabled = hapticsEnabled,
                    onClick = { onAction(spec.kind) }
                ) {
                    ActionGlyph(icon = spec.kind.icon, contentDescription = spec.kind.label)
                }
            }

            if (stats != null) {
                ActionContainer(
                    tone = if (statsExpanded) ActionTone.Selected else ActionTone.Standard,
                    hapticPattern = HapticPattern.CLICK,
                    hapticsEnabled = hapticsEnabled,
                    onClick = onToggleStats
                ) {
                    ActionGlyph(
                        icon = Icons.Outlined.Bolt,
                        contentDescription = if (statsExpanded) "Hide response speed" else "Response speed"
                    )
                    AnimatedVisibility(
                        visible = statsExpanded,
                        enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
                        exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
                    ) {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            stats.segments().forEachIndexed { index, segment ->
                                if (index > 0) {
                                    Text("·", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    text = segment,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (bookmark != null) {
                ActionContainer(
                    tone = if (bookmark.isBookmarked) ActionTone.Selected else ActionTone.Standard,
                    hapticPattern = HapticPattern.MORPH_TRANSITION,
                    hapticsEnabled = hapticsEnabled,
                    onClick = bookmark.onToggle,
                    onLongClick = bookmark.onLongPress
                ) {
                    ActionGlyph(
                        icon = if (bookmark.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmark.isBookmarked) "Remove bookmark" else "Bookmark message"
                    )
                }
            }
        }

        if (elapsedLabel != null) {
            Text(
                text = elapsedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

private enum class ActionTone { Standard, Primary, Selected }

private val MessageActionKind.icon: ImageVector
    get() = when (this) {
        MessageActionKind.Copy -> Icons.Default.ContentCopy
        MessageActionKind.Branch -> Icons.AutoMirrored.Outlined.CallSplit
        MessageActionKind.RedoWithModel -> Icons.Outlined.SwapHoriz
        MessageActionKind.Regenerate -> Icons.Default.Refresh
    }

private val MessageActionKind.label: String
    get() = when (this) {
        MessageActionKind.Copy -> "Copy message"
        MessageActionKind.Branch -> "Branch conversation"
        MessageActionKind.RedoWithModel -> "Redo with different model"
        MessageActionKind.Regenerate -> "Regenerate response"
    }

@Composable
private fun ActionGlyph(icon: ImageVector, contentDescription: String) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * The one container every action in the row uses: a tonal pill that morphs its corners toward
 * a rounded square and dips in scale while pressed (the M3 Expressive icon-button press
 * morph), with content colour following the tone. Width grows with its content via a spring
 * so the stats pill expands in place.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionContainer(
    tone: ActionTone,
    hapticPattern: HapticPattern,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val haptics = rememberHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = if (pressed) 12.dp else MessageActionRowItemSize / 2,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "messageActionCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "messageActionScale"
    )
    val containerColor by animateColorAsState(
        targetValue = when (tone) {
            ActionTone.Standard -> scheme.surfaceContainerHigh
            ActionTone.Primary -> scheme.primaryContainer
            ActionTone.Selected -> scheme.secondaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "messageActionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when (tone) {
            ActionTone.Standard -> scheme.onSurfaceVariant
            ActionTone.Primary -> scheme.onPrimaryContainer
            ActionTone.Selected -> scheme.onSecondaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "messageActionContent"
    )

    Surface(
        modifier = modifier
            .height(MessageActionRowItemSize)
            .defaultMinSize(minWidth = MessageActionRowItemSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptics.perform(hapticPattern, hapticsEnabled)
                    onClick()
                },
                onLongClick = onLongClick?.let { longPress ->
                    {
                        haptics.perform(HapticPattern.LONG_PRESS, hapticsEnabled)
                        longPress()
                    }
                }
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(MessageActionRowItemSize)
                .animateContentSize(animationSpec = ExpressiveMotion.Spatial.contentSize())
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            content = content
        )
    }
}
