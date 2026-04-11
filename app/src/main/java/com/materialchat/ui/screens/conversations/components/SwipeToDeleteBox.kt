package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ripple
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
 * Visual configuration for the secondary left-swipe action (e.g. Archive/Restore).
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
 * M3 Expressive swipe-to-action container for conversation rows.
 *
 * Left swipe reveals two action buttons (Delete + optional trailing action like Archive).
 * Content snaps open past threshold, each button is tappable with M3 ripple.
 * Right swipe triggers [onSwipeRight] with the original spring-back gesture.
 *
 * Animation system:
 * - SPATIAL springs (can bounce): position, scale, shape morph
 * - EFFECTS springs (no bounce): color, opacity
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

    // Left swipe: snap-open reveal for action buttons
    val actionWidth = 80.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val actionCount = if (trailingAction != null) 2 else 1
    val totalActionsWidthPx = actionWidthPx * actionCount
    val snapThresholdPx = totalActionsWidthPx * 0.4f

    // Right swipe: spring-back gesture (edit/rename)
    val rightThresholdPx = with(density) { 100.dp.toPx() }
    val rightMaxPx = with(density) { 150.dp.toPx() }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "offsetX"
    )

    // Threshold feedback
    val thresholdReached = when {
        animatedOffsetX < 0f -> animatedOffsetX.absoluteValue >= snapThresholdPx
        animatedOffsetX > 0f -> animatedOffsetX >= rightThresholdPx
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

    // Shape morphing
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

    // Right swipe progress for icon animation
    val rightProgress = if (animatedOffsetX > 0f) {
        (animatedOffsetX / rightThresholdPx).coerceIn(0f, 1f)
    } else 0f

    val rightIconScale by animateFloatAsState(
        targetValue = if (rightProgress > 0.5f) 1.2f else 0.8f + (rightProgress * 0.4f),
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "rightIconScale"
    )

    val rightBgColor by animateColorAsState(
        targetValue = if (rightProgress > 0.9f) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "rightBg"
    )

    val rightIconColor by animateColorAsState(
        targetValue = if (rightProgress > 0.9f) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "rightIcon"
    )

    val rightIconPadding by animateFloatAsState(
        targetValue = if (rightProgress > 0.5f) 24f else 16f,
        animationSpec = ExpressiveMotion.Effects.elevation(),
        label = "rightIconPad"
    )

    // Reveal progress for left swipe action icons
    val revealProgress = if (animatedOffsetX < 0f) {
        (animatedOffsetX.absoluteValue / totalActionsWidthPx).coerceIn(0f, 1f)
    } else 0f

    val actionScale by animateFloatAsState(
        targetValue = 0.8f + (revealProgress * 0.2f),
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "actionScale"
    )

    Box(modifier = modifier) {
        // Left swipe: reveal action buttons (Delete + Archive)
        if (animatedOffsetX < -1f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(currentShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button (closer to content, primary action)
                SwipeRevealAction(
                    icon = Icons.Outlined.Delete,
                    label = "Delete",
                    contentDescription = "Delete",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    scale = actionScale,
                    width = actionWidth,
                    onClick = {
                        offsetX = 0f
                        onDelete()
                    }
                )
                // Archive/Restore button (trailing edge)
                trailingAction?.let { action ->
                    SwipeRevealAction(
                        icon = action.icon,
                        label = action.label,
                        contentDescription = action.contentDescription,
                        containerColor = action.containerColor,
                        contentColor = action.contentColor,
                        scale = actionScale,
                        width = actionWidth,
                        onClick = {
                            offsetX = 0f
                            action.onClick()
                        }
                    )
                }
            }
        }

        // Right swipe: edit icon (original M3 color wash design)
        if (animatedOffsetX > 1f && onSwipeRight != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape)
                    .background(rightBgColor),
                contentAlignment = Alignment.CenterStart
            ) {
                if (rightProgress > 0.1f) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit title",
                        tint = rightIconColor,
                        modifier = Modifier
                            .padding(start = rightIconPadding.dp)
                            .scale(rightIconScale)
                    )
                }
            }
        }

        // Foreground content
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
                                // Left swipe: snap open if past threshold, else close
                                offsetX < 0f -> {
                                    if (offsetX.absoluteValue > snapThresholdPx) {
                                        -totalActionsWidthPx
                                    } else {
                                        0f
                                    }
                                }
                                // Right swipe: trigger action + spring back
                                offsetX > 0f && onSwipeRight != null && offsetX > rightThresholdPx -> {
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
                                newOffset < 0f -> newOffset.coerceIn(-totalActionsWidthPx * 1.1f, 0f)
                                onSwipeRight != null -> newOffset.coerceIn(0f, rightMaxPx)
                                else -> newOffset.coerceIn(-totalActionsWidthPx * 1.1f, 0f)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

/**
 * Single action button revealed behind a swiped conversation row.
 *
 * M3 Expressive styling: 24dp icon, labelSmall text, M3 ripple indication,
 * 4dp vertical spacing between icon and label.
 */
@Composable
private fun SwipeRevealAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    scale: Float,
    width: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = contentColor)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(scale)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    return start + (end - start) * fraction
}
