package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
 * Visual configuration for the auxiliary swipe action shown next to delete.
 */
data class SwipeActionSpec(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit
)

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
 * Left swipe reveals trailing actions. Right swipe keeps the existing quick rename gesture.
 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    baseCorners: SwipeCornerSpec = SwipeCornerSpec(20.dp, 20.dp, 20.dp, 20.dp),
    activeCorners: SwipeCornerSpec = SwipeCornerSpec(20.dp, 20.dp, 20.dp, 20.dp),
    trailingAction: SwipeActionSpec? = null,
    onSwipeRight: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = rememberHapticFeedback()

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasTriggeredThresholdHaptic by remember { mutableStateOf(false) }

    val actionWidth = 88.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val trailingActionCount = if (trailingAction != null) 2 else 1
    val trailingActionsWidthPx = actionWidthPx * trailingActionCount
    val leftRevealThresholdPx = trailingActionsWidthPx * 0.45f

    val rightActionThresholdPx = with(density) { 100.dp.toPx() }
    val rightMaxSwipePx = with(density) { 150.dp.toPx() }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "offsetX"
    )

    val thresholdReached = when {
        animatedOffsetX < 0f -> animatedOffsetX.absoluteValue >= leftRevealThresholdPx
        animatedOffsetX > 0f -> animatedOffsetX >= rightActionThresholdPx
        else -> false
    }

    LaunchedEffect(thresholdReached) {
        if (thresholdReached && !hasTriggeredThresholdHaptic) {
            haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
            hasTriggeredThresholdHaptic = true
        } else if (!thresholdReached) {
            hasTriggeredThresholdHaptic = false
        }
    }

    val swipeActive = animatedOffsetX.absoluteValue > 1f || isDragging
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

    val actionProgress = when {
        animatedOffsetX < 0f -> (animatedOffsetX.absoluteValue / trailingActionsWidthPx).coerceIn(0f, 1f)
        animatedOffsetX > 0f -> (animatedOffsetX / rightActionThresholdPx).coerceIn(0f, 1f)
        else -> 0f
    }

    val actionScale by animateFloatAsState(
        targetValue = 0.85f + (actionProgress * 0.15f),
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "actionScale"
    )

    val rightSwipeBackgroundColor by animateColorAsState(
        targetValue = if (actionProgress > 0.9f) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "rightSwipeBackground"
    )

    Box(modifier = modifier) {
        if (animatedOffsetX < -1f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(currentShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailingAction?.let { action ->
                    SwipeBackgroundAction(
                        label = action.label,
                        contentDescription = action.contentDescription,
                        icon = action.icon,
                        containerColor = action.containerColor,
                        contentColor = action.contentColor,
                        progress = actionProgress,
                        scale = actionScale,
                        width = actionWidth,
                        onClick = {
                            offsetX = 0f
                            action.onClick()
                        }
                    )
                }
                SwipeBackgroundAction(
                    label = "Delete",
                    contentDescription = "Delete",
                    icon = Icons.Outlined.Delete,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    progress = actionProgress,
                    scale = actionScale,
                    width = actionWidth,
                    onClick = {
                        offsetX = 0f
                        onDelete()
                    }
                )
            }
        }

        if (animatedOffsetX > 1f && onSwipeRight != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(currentShape)
                    .background(rightSwipeBackgroundColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit title",
                    tint = if (actionProgress > 0.9f) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .scale(actionScale)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxSize()
                .clip(currentShape)
                .pointerInput(enabled, trailingAction, onSwipeRight) {
                    if (!enabled) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptics.perform(HapticPattern.GESTURE_START, hapticsEnabled)
                        },
                        onDragEnd = {
                            isDragging = false
                            haptics.perform(HapticPattern.GESTURE_END, hapticsEnabled)
                            offsetX = when {
                                offsetX < 0f -> {
                                    if (offsetX.absoluteValue > leftRevealThresholdPx) {
                                        -trailingActionsWidthPx
                                    } else {
                                        0f
                                    }
                                }
                                offsetX > 0f && onSwipeRight != null && offsetX > rightActionThresholdPx -> {
                                    onSwipeRight()
                                    0f
                                }
                                else -> 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            offsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = offsetX + dragAmount
                            offsetX = when {
                                newOffset < 0f -> newOffset.coerceIn(-trailingActionsWidthPx, 0f)
                                onSwipeRight != null -> newOffset.coerceIn(0f, rightMaxSwipePx)
                                else -> newOffset.coerceIn(-trailingActionsWidthPx, 0f)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeBackgroundAction(
    label: String,
    contentDescription: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    progress: Float,
    scale: Float,
    width: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = progress.coerceIn(0.6f, 1f))
            )
        }
    }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    return start + (end - start) * fraction
}
