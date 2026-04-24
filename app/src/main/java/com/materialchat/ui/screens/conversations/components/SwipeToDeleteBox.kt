package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Corner specification used to morph row corners during swipe.
 */
data class SwipeCornerSpec(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
)

/**
 * Swipe container for conversation rows.
 *
 * Partial left swipe reveals layered M3 action pills. A deeper left swipe expands
 * the Delete action into the dominant destructive state and deletes on release.
 * Optional right swipe remains available for the edit action.
 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    baseCorners: SwipeCornerSpec = SwipeCornerSpec(20.dp, 20.dp, 20.dp, 20.dp),
    activeCorners: SwipeCornerSpec = SwipeCornerSpec(20.dp, 20.dp, 20.dp, 20.dp),
    onArchive: (() -> Unit)? = null,
    isArchived: Boolean = false,
    onSwipeRight: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = rememberHapticFeedback()

    var offsetX by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(false) }
    var hasTriggeredRevealHaptic by remember { mutableStateOf(false) }
    var hasTriggeredDeleteHaptic by remember { mutableStateOf(false) }

    val actionButtonWidthDp = 84.dp
    val actionGapDp = 8.dp
    val actionGutterDp = 6.dp
    val leftActionContentWidthDp = if (onArchive != null) {
        (actionButtonWidthDp * 2) + actionGapDp
    } else {
        actionButtonWidthDp
    }
    val actionRevealDp = leftActionContentWidthDp + (actionGutterDp * 2)
    val rightActionRevealDp = actionButtonWidthDp + (actionGutterDp * 2)
    val actionRevealPx = with(density) { actionRevealDp.toPx() }
    val leftActionContentWidthPx = with(density) { leftActionContentWidthDp.toPx() }
    val revealThresholdPx = with(density) { 48.dp.toPx() }
    val rightActionThresholdPx = with(density) { 100.dp.toPx() }
    val maxRightSwipePx = with(density) { 150.dp.toPx() }
    val deleteThresholdPx = when {
        containerWidthPx > 0f -> (containerWidthPx * 0.62f).coerceAtLeast(actionRevealPx + with(density) { 64.dp.toPx() })
        else -> actionRevealPx + with(density) { 112.dp.toPx() }
    }
    val maxLeftSwipePx = when {
        containerWidthPx > 0f -> containerWidthPx * 0.92f
        else -> actionRevealPx + with(density) { 144.dp.toPx() }
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = when {
            isDragging -> offsetX
            isOpen -> -actionRevealPx
            else -> 0f
        },
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "offsetX"
    )

    val leftDistancePx = animatedOffsetX.absoluteValue
    val leftProgress = (leftDistancePx / actionRevealPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val deleteProgress = ((leftDistancePx - leftActionContentWidthPx) /
        (deleteThresholdPx - leftActionContentWidthPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val rightProgress = (animatedOffsetX / rightActionThresholdPx).coerceIn(0f, 1f)

    LaunchedEffect(leftProgress > 0.55f, deleteProgress >= 1f) {
        if (deleteProgress >= 1f && !hasTriggeredDeleteHaptic) {
            haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
            hasTriggeredDeleteHaptic = true
        } else if (leftProgress > 0.55f && !hasTriggeredRevealHaptic) {
            haptics.perform(HapticPattern.SEGMENT_TICK, hapticsEnabled)
            hasTriggeredRevealHaptic = true
        }

        if (leftProgress <= 0.55f) {
            hasTriggeredRevealHaptic = false
        }
        if (deleteProgress < 1f) {
            hasTriggeredDeleteHaptic = false
        }
    }

    val deleteBackgroundColor by animateColorAsState(
        targetValue = if (deleteProgress > 0.75f) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "deleteBackgroundColor"
    )
    val deleteContentColor by animateColorAsState(
        targetValue = if (deleteProgress > 0.75f) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "deleteContentColor"
    )
    val archiveBackgroundColor by animateColorAsState(
        targetValue = if (isArchived) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "archiveBackgroundColor"
    )
    val archiveContentColor by animateColorAsState(
        targetValue = if (isArchived) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "archiveContentColor"
    )

    val archiveWidth by animateDpAsState(
        targetValue = if (deleteProgress > 0.35f) 56.dp else actionButtonWidthDp,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "archiveActionWidth"
    )
    val deleteWidth = with(density) {
        (actionButtonWidthDp.toPx() + (leftDistancePx - leftActionContentWidthPx).coerceAtLeast(0f)).toDp()
    }
    val deleteButtonWidth by animateDpAsState(
        targetValue = deleteWidth.coerceAtLeast(actionButtonWidthDp),
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "deleteActionWidth"
    )

    val iconScale by animateFloatAsState(
        targetValue = when {
            deleteProgress >= 1f -> 1.18f
            leftProgress > 0.5f || rightProgress > 0.5f -> 1.06f
            else -> 0.92f
        },
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "iconScale"
    )

    val swipeActive = animatedOffsetX.absoluteValue > 1f || isDragging || isOpen
    val shapeProgressRaw by animateFloatAsState(
        targetValue = if (swipeActive) 1f else 0f,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "shapeProgress"
    )
    val shapeProgress = shapeProgressRaw.coerceIn(0f, 1f)
    val currentShape = RoundedCornerShape(
        topStart = lerp(baseCorners.topStart, activeCorners.topStart, shapeProgress),
        topEnd = lerp(baseCorners.topEnd, activeCorners.topEnd, shapeProgress),
        bottomStart = lerp(baseCorners.bottomStart, activeCorners.bottomStart, shapeProgress),
        bottomEnd = lerp(baseCorners.bottomEnd, activeCorners.bottomEnd, shapeProgress)
    )

    fun closeActions() {
        isOpen = false
        offsetX = 0f
    }

    Box(
        modifier = modifier.onSizeChanged { containerWidthPx = it.width.toFloat() }
    ) {
        if (animatedOffsetX < -1f || isOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.66f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .width(actionRevealDp)
                        .fillMaxHeight()
                        .padding(horizontal = actionGutterDp),
                    horizontalArrangement = Arrangement.spacedBy(actionGapDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onArchive != null && deleteProgress < 0.92f) {
                        SwipeActionButton(
                            label = if (isArchived) "Restore" else "Archive",
                            icon = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            containerColor = archiveBackgroundColor,
                            contentColor = archiveContentColor,
                            width = archiveWidth,
                            iconScale = iconScale,
                            emphasized = false,
                            onClick = {
                                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                closeActions()
                                onArchive()
                            }
                        )
                    }
                    SwipeActionButton(
                        label = if (deleteProgress >= 0.82f) "Release" else "Delete",
                        icon = Icons.Outlined.Delete,
                        containerColor = deleteBackgroundColor,
                        contentColor = deleteContentColor,
                        width = deleteButtonWidth,
                        iconScale = iconScale,
                        emphasized = deleteProgress > 0.35f,
                        onClick = {
                            haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
                            closeActions()
                            onDelete()
                        }
                    )
                }
            }
        }

        if (animatedOffsetX > 1f && onSwipeRight != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.66f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .width(rightActionRevealDp)
                        .fillMaxHeight()
                        .padding(horizontal = actionGutterDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwipeActionButton(
                        label = "Edit",
                        icon = Icons.Outlined.Edit,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        width = actionButtonWidthDp,
                        iconScale = iconScale,
                        emphasized = rightProgress > 0.72f,
                        onClick = {
                            haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                            closeActions()
                            onSwipeRight()
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(currentShape)
                .pointerInput(enabled, isOpen, actionRevealPx, deleteThresholdPx, maxLeftSwipePx) {
                    if (!enabled) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            offsetX = if (isOpen) -actionRevealPx else animatedOffsetX
                            haptics.perform(HapticPattern.GESTURE_START, hapticsEnabled)
                        },
                        onDragEnd = {
                            isDragging = false
                            haptics.perform(HapticPattern.GESTURE_END, hapticsEnabled)
                            when {
                                offsetX <= -deleteThresholdPx -> {
                                    haptics.perform(HapticPattern.CONFIRM, hapticsEnabled)
                                    closeActions()
                                    onDelete()
                                }
                                offsetX < -revealThresholdPx -> {
                                    isOpen = true
                                    offsetX = -actionRevealPx
                                }
                                offsetX > rightActionThresholdPx && onSwipeRight != null && !isOpen -> {
                                    closeActions()
                                    onSwipeRight()
                                }
                                else -> closeActions()
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            closeActions()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = offsetX + dragAmount
                            offsetX = when {
                                isOpen || newOffset <= 0f -> newOffset.coerceIn(-maxLeftSwipePx, 0f)
                                onSwipeRight != null -> newOffset.coerceIn(0f, maxRightSwipePx)
                                else -> 0f
                            }
                        }
                    )
                }
        ) {
            content()
            if (isOpen && !isDragging) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                            closeActions()
                        }
                )
            }
        }
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    width: Dp,
    iconScale: Float,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val actionShape by animateDpAsState(
        targetValue = if (emphasized) 28.dp else 22.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "actionShape"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 7.dp),
        shape = RoundedCornerShape(actionShape),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (emphasized) 6.dp else 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
            )
            if (width >= 72.dp) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}
