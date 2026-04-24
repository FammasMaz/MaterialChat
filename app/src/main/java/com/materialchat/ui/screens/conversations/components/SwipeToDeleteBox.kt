package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
 * A partial left swipe settles open and reveals Archive/Restore plus Delete actions.
 * A full left swipe intentionally does not delete anymore; destructive actions must
 * be tapped explicitly. Optional right swipe remains available for the edit action.
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
    var isDragging by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(false) }
    var hasTriggeredRevealHaptic by remember { mutableStateOf(false) }

    val actionButtonWidthDp = 84.dp
    val actionRevealDp = if (onArchive != null) actionButtonWidthDp * 2 else actionButtonWidthDp
    val actionRevealPx = with(density) { actionRevealDp.toPx() }
    val revealThresholdPx = with(density) { 48.dp.toPx() }
    val rightActionThresholdPx = with(density) { 100.dp.toPx() }
    val maxRightSwipePx = with(density) { 150.dp.toPx() }

    val animatedOffsetX by animateFloatAsState(
        targetValue = when {
            isDragging -> offsetX
            isOpen -> -actionRevealPx
            else -> 0f
        },
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "offsetX"
    )

    val leftProgress = (animatedOffsetX.absoluteValue / actionRevealPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val rightProgress = (animatedOffsetX / rightActionThresholdPx).coerceIn(0f, 1f)

    LaunchedEffect(leftProgress > 0.55f) {
        if (leftProgress > 0.55f && !hasTriggeredRevealHaptic) {
            haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
            hasTriggeredRevealHaptic = true
        } else if (leftProgress <= 0.55f) {
            hasTriggeredRevealHaptic = false
        }
    }

    val deleteBackgroundColor by animateColorAsState(
        targetValue = if (leftProgress > 0.75f) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "deleteBackgroundColor"
    )
    val deleteContentColor by animateColorAsState(
        targetValue = if (leftProgress > 0.75f) {
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

    val iconScale by animateFloatAsState(
        targetValue = if (leftProgress > 0.5f || rightProgress > 0.5f) 1.05f else 0.9f,
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
        topStart = lerpDp(baseCorners.topStart, activeCorners.topStart, shapeProgress),
        topEnd = lerpDp(baseCorners.topEnd, activeCorners.topEnd, shapeProgress),
        bottomStart = lerpDp(baseCorners.bottomStart, activeCorners.bottomStart, shapeProgress),
        bottomEnd = lerpDp(baseCorners.bottomEnd, activeCorners.bottomEnd, shapeProgress)
    )

    fun closeActions() {
        isOpen = false
        offsetX = 0f
    }

    Box(modifier = modifier) {
        if (animatedOffsetX < -1f || isOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .width(actionRevealDp)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onArchive != null) {
                        SwipeActionButton(
                            label = if (isArchived) "Restore" else "Archive",
                            icon = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            containerColor = archiveBackgroundColor,
                            contentColor = archiveContentColor,
                            width = actionButtonWidthDp,
                            iconScale = iconScale,
                            onClick = {
                                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                closeActions()
                                onArchive()
                            }
                        )
                    }
                    SwipeActionButton(
                        label = "Delete",
                        icon = Icons.Outlined.Delete,
                        containerColor = deleteBackgroundColor,
                        contentColor = deleteContentColor,
                        width = actionButtonWidthDp,
                        iconScale = iconScale,
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
                    .background(
                        if (rightProgress > 0.8f) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit title",
                    tint = if (rightProgress > 0.8f) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .scale(iconScale)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(currentShape)
                .pointerInput(enabled, isOpen, actionRevealPx) {
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
                                isOpen || newOffset <= 0f -> newOffset.coerceIn(-actionRevealPx, 0f)
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(containerColor)
            .clickable(onClick = onClick)
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
    }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    return start + (end - start) * fraction
}
