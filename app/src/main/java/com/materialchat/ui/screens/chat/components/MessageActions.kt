package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import androidx.compose.material3.Surface
import com.materialchat.ui.components.tapThroughScroll
import com.materialchat.ui.theme.ExpressiveMotion

/** The actions an assistant message can offer, in their display order. */
enum class MessageActionKind { Copy, Branch, RedoWithModel, Regenerate }

/** Tonal hierarchy inside the group: one container language, two tones. */
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

/** Visual height of every segment in the connected group. */
internal val MessageActionRowItemSize: Dp = 40.dp

/** Height of the row and of every segment's touch target (M3 minimum). */
internal val MessageActionRowTouchHeight: Dp = 48.dp

/** Width of an icon-only segment while the stats pill is expanded next to it. */
internal val MessageActionCompactWidth: Dp = 32.dp

/** Gap between connected segments (M3 Expressive connected button group spacing). */
internal val MessageActionGroupSpacing: Dp = 2.dp

/** Corner radii of the connected group: outer edges, inner joins, and the pressed morph. */
internal val MessageActionOuterCorner: Dp = 20.dp
internal val MessageActionInnerCorner: Dp = 6.dp
internal val MessageActionPressedCorner: Dp = 14.dp

/** Kept for call-site compatibility; the group now uses [MessageActionGroupSpacing]. */
internal val MessageActionRowSpacing: Dp = MessageActionGroupSpacing

/** Where a segment sits in the connected group; decides which corners are outer and inner. */
enum class ConnectedSegmentPosition { Only, First, Middle, Last }

/** Position of the [index]-th of [count] segments. */
fun connectedSegmentPosition(index: Int, count: Int): ConnectedSegmentPosition = when {
    count <= 1 -> ConnectedSegmentPosition.Only
    index == 0 -> ConnectedSegmentPosition.First
    index == count - 1 -> ConnectedSegmentPosition.Last
    else -> ConnectedSegmentPosition.Middle
}

/** Start/end corner radii of one segment (start = leading edge in layout direction). */
data class ConnectedSegmentCorners(val start: Dp, val end: Dp)

/**
 * Corner rule of the connected group: the group's outer edges are fully expressive, the joins
 * between segments are tight, and a pressed segment morphs both edges to the pressed radius
 * so it reads as the one control that lifted out of the group.
 */
fun connectedSegmentCorners(
    position: ConnectedSegmentPosition,
    pressed: Boolean,
    outer: Dp = MessageActionOuterCorner,
    inner: Dp = MessageActionInnerCorner,
    pressedCorner: Dp = MessageActionPressedCorner
): ConnectedSegmentCorners {
    if (pressed) return ConnectedSegmentCorners(pressedCorner, pressedCorner)
    return when (position) {
        ConnectedSegmentPosition.Only -> ConnectedSegmentCorners(outer, outer)
        ConnectedSegmentPosition.First -> ConnectedSegmentCorners(outer, inner)
        ConnectedSegmentPosition.Middle -> ConnectedSegmentCorners(inner, inner)
        ConnectedSegmentPosition.Last -> ConnectedSegmentCorners(inner, outer)
    }
}

/**
 * Width rule for icon-only segments: they compress while the stats pill is expanded so the
 * pill can grow in place; the row never wraps to a second line.
 */
fun messageActionSegmentWidth(
    statsExpanded: Boolean,
    full: Dp = MessageActionRowItemSize,
    compact: Dp = MessageActionCompactWidth
): Dp = if (statsExpanded) compact else full

/**
 * The elapsed-time label duplicates the pill's total figure, so it yields its space to the
 * pill while the pill is expanded.
 */
fun showElapsedLabel(elapsedLabel: String?, statsExpanded: Boolean): Boolean =
    elapsedLabel != null && !statsExpanded

/**
 * The action row under an assistant message as one M3 Expressive connected button group.
 *
 * Every control (copy, branch, redo with model, regenerate, speed stats, bookmark) is a
 * segment of one group: 2dp joins, 20dp outer corners, 6dp inner corners, a shared tone
 * family. Hierarchy is tone only: standard segments on surfaceContainerHigh, the one primary
 * action (regenerate) filled tonal on secondaryContainer inside the same group, toggled states
 * (bookmarked, stats expanded) on primaryContainer. A pressed segment morphs its corners and
 * dips in scale on the spatial shape-morph spring.
 *
 * The row is a single line: while the stats pill is expanded the icon segments compress to
 * [MessageActionCompactWidth] and the pill takes the remaining width (its text ellipsises),
 * never a second line. Every segment keeps a 48dp-tall touch target around its 40dp visual.
 */
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

    val segmentCount = specs.size + (if (stats != null) 1 else 0) + (if (bookmark != null) 1 else 0)
    val iconWidth by animateDpAsState(
        targetValue = messageActionSegmentWidth(statsExpanded),
        animationSpec = ExpressiveMotion.Spatial.contentSize(),
        label = "messageActionSegmentWidth"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MessageActionRowTouchHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MessageActionGroupSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var index = 0
            specs.forEach { spec ->
                val primary = spec.emphasis == MessageActionEmphasis.Primary
                ActionSegment(
                    position = connectedSegmentPosition(index++, segmentCount),
                    tone = if (primary) ActionTone.Primary else ActionTone.Standard,
                    hapticPattern = if (primary) HapticPattern.MORPH_TRANSITION else HapticPattern.CLICK,
                    hapticsEnabled = hapticsEnabled,
                    onClick = { onAction(spec.kind) },
                    modifier = Modifier.width(iconWidth)
                ) {
                    ActionGlyph(icon = spec.kind.icon, contentDescription = spec.kind.label)
                }
            }

            if (stats != null) {
                ActionSegment(
                    position = connectedSegmentPosition(index++, segmentCount),
                    tone = if (statsExpanded) ActionTone.Selected else ActionTone.Standard,
                    hapticPattern = HapticPattern.CLICK,
                    hapticsEnabled = hapticsEnabled,
                    onClick = onToggleStats,
                    modifier = if (statsExpanded) {
                        Modifier.weight(1f, fill = false).widthIn(min = MessageActionRowItemSize)
                    } else {
                        Modifier.width(MessageActionRowItemSize)
                    },
                    contentPadding = if (statsExpanded) 10.dp else 0.dp
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
                        Text(
                            text = stats.segments().joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                }
            }

            if (bookmark != null) {
                ActionSegment(
                    position = connectedSegmentPosition(index++, segmentCount),
                    tone = if (bookmark.isBookmarked) ActionTone.Selected else ActionTone.Standard,
                    hapticPattern = HapticPattern.MORPH_TRANSITION,
                    hapticsEnabled = hapticsEnabled,
                    onClick = bookmark.onToggle,
                    onLongClick = bookmark.onLongPress,
                    modifier = Modifier.width(iconWidth)
                ) {
                    ActionGlyph(
                        icon = if (bookmark.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmark.isBookmarked) "Remove bookmark" else "Bookmark message"
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showElapsedLabel(elapsedLabel, statsExpanded),
            enter = expandHorizontally(animationSpec = ExpressiveMotion.Spatial.expand<IntSize>()) +
                fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
            exit = shrinkHorizontally(animationSpec = ExpressiveMotion.Spatial.collapse<IntSize>()) +
                fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
        ) {
            Text(
                text = elapsedLabel.orEmpty(),
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
 * One segment of the connected group. The 48dp-tall box is the touch target (its click,
 * long-press and tap-through-scroll handling live here); the 40dp surface inside is the
 * visual, with per-position corners that morph to the pressed radius and a scale dip while
 * pressed, both on the spatial springs. Content colour follows the tone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionSegment(
    position: ConnectedSegmentPosition,
    tone: ActionTone,
    hapticPattern: HapticPattern,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contentPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    val haptics = rememberHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val corners = connectedSegmentCorners(position, pressed)
    val startCorner by animateDpAsState(
        targetValue = corners.start,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "messageActionStartCorner"
    )
    val endCorner by animateDpAsState(
        targetValue = corners.end,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "messageActionEndCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "messageActionScale"
    )
    val containerColor by animateColorAsState(
        targetValue = when (tone) {
            ActionTone.Standard -> scheme.surfaceContainerHigh
            ActionTone.Primary -> scheme.secondaryContainer
            ActionTone.Selected -> scheme.primaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "messageActionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when (tone) {
            ActionTone.Standard -> scheme.onSurfaceVariant
            ActionTone.Primary -> scheme.onSecondaryContainer
            ActionTone.Selected -> scheme.onPrimaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "messageActionContent"
    )
    val press = {
        haptics.perform(hapticPattern, hapticsEnabled)
        onClick()
    }

    Box(
        modifier = modifier
            .height(MessageActionRowTouchHeight)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = press,
                onLongClick = onLongClick?.let { longPress ->
                    {
                        haptics.perform(HapticPattern.LONG_PRESS, hapticsEnabled)
                        longPress()
                    }
                }
            )
            // Long chats: the list consumes the down while its scroll is in progress, so the
            // clickable above never sees the tap. This path takes over only then.
            .tapThroughScroll(onClick = press),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(MessageActionRowItemSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .indication(interactionSource, ripple()),
            shape = RoundedCornerShape(
                topStart = startCorner,
                bottomStart = startCorner,
                topEnd = endCorner,
                bottomEnd = endCorner
            ),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .height(MessageActionRowItemSize)
                    .animateContentSize(animationSpec = ExpressiveMotion.Spatial.contentSize())
                    .padding(horizontal = contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                content = content
            )
        }
    }
}
