package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Visual configuration for the primary left-swipe action.
 *
 * When provided, the left swipe will trigger this action instead of delete.
 * The full-width color wash and icon will use these values.
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
 * Restores the original M3 design: full-width color wash background with a single
 * animated icon. Swiping past the threshold triggers the action and springs back.
 *
 * - Left swipe: executes [trailingAction] if provided, otherwise [onDelete]
 * - Right swipe: executes [onSwipeRight] (e.g. rename)
 *
 * Animation system:
 * - SPATIAL springs (can bounce): position, scale, icon padding
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

    // Track the horizontal offset of the content
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var shouldTriggerLeftAction by remember { mutableStateOf(false) }
    var shouldTriggerRightAction by remember { mutableStateOf(false) }
    var hasTriggeredThresholdHaptic by remember { mutableStateOf(false) }

    // Threshold for triggering action (in dp)
    val thresholdDp = 100.dp
    val thresholdPx = with(density) { thresholdDp.toPx() }

    // Maximum swipe distance
    val maxSwipeDp = 150.dp
    val maxSwipePx = with(density) { maxSwipeDp.toPx() }

    // M3 Expressive: SPATIAL spring for position (can bounce)
    // Springs back to 0 when not dragging — triggers action then resets
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isDragging) offsetX else 0f,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "offsetX"
    )

    // Calculate swipe progress (0 to 1)
    val swipeProgress = (animatedOffsetX.absoluteValue / thresholdPx).coerceIn(0f, 1f)

    // Trigger haptic feedback when crossing the threshold
    LaunchedEffect(swipeProgress > 0.9f) {
        if (swipeProgress > 0.9f && !hasTriggeredThresholdHaptic) {
            haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
            hasTriggeredThresholdHaptic = true
        } else if (swipeProgress <= 0.9f) {
            hasTriggeredThresholdHaptic = false
        }
    }

    // === LEFT SWIPE — trailingAction (archive/restore) or delete ===

    // M3 Expressive: EFFECTS spring for color (no bounce!)
    val leftBackgroundColor by animateColorAsState(
        targetValue = if (trailingAction != null) {
            // Archive/Restore: consistent container color
            trailingAction.containerColor
        } else {
            // Delete: errorContainer → error at threshold
            if (swipeProgress > 0.9f) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "leftBgColor"
    )

    val leftIconColor by animateColorAsState(
        targetValue = if (trailingAction != null) {
            trailingAction.contentColor
        } else {
            if (swipeProgress > 0.9f) MaterialTheme.colorScheme.onError
            else MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "leftIconColor"
    )

    val leftIcon = trailingAction?.icon ?: Icons.Outlined.Delete
    val leftContentDescription = trailingAction?.contentDescription ?: "Delete"

    // === RIGHT SWIPE — edit ===

    val rightBackgroundColor by animateColorAsState(
        targetValue = if (swipeProgress > 0.9f) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "rightBgColor"
    )

    val rightIconColor by animateColorAsState(
        targetValue = if (swipeProgress > 0.9f) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "rightIconColor"
    )

    // M3 Expressive: SPATIAL spring for scale (bouncy feedback)
    val iconScale by animateFloatAsState(
        targetValue = if (swipeProgress > 0.5f) 1.2f else 0.8f + (swipeProgress * 0.4f),
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "iconScale"
    )

    // M3 Expressive: EFFECTS spring for padding (smooth)
    val iconPadding by animateFloatAsState(
        targetValue = if (swipeProgress > 0.5f) 24f else 16f,
        animationSpec = ExpressiveMotion.Effects.elevation(),
        label = "iconPadding"
    )

    // Trigger left action when swipe is complete
    LaunchedEffect(shouldTriggerLeftAction) {
        if (shouldTriggerLeftAction) {
            if (trailingAction != null) {
                trailingAction.onClick()
            } else {
                onDelete()
            }
            shouldTriggerLeftAction = false
            offsetX = 0f
        }
    }

    // Trigger right-swipe action
    LaunchedEffect(shouldTriggerRightAction) {
        if (shouldTriggerRightAction) {
            onSwipeRight?.invoke()
            shouldTriggerRightAction = false
            offsetX = 0f
        }
    }

    // Shape morphing
    val swipeActive = animatedOffsetX.absoluteValue > 1f || isDragging
    val shapeProgressRaw by animateFloatAsState(
        targetValue = if (swipeActive) 1f else 0f,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "shapeProgress"
    )
    val shapeProgress = shapeProgressRaw.coerceIn(0f, 1f) // Avoid negative corner sizes on overshoot.
    val currentShape = RoundedCornerShape(
        topStart = lerpDp(baseCorners.topStart, activeCorners.topStart, shapeProgress),
        topEnd = lerpDp(baseCorners.topEnd, activeCorners.topEnd, shapeProgress),
        bottomStart = lerpDp(baseCorners.bottomStart, activeCorners.bottomStart, shapeProgress),
        bottomEnd = lerpDp(baseCorners.bottomEnd, activeCorners.bottomEnd, shapeProgress)
    )

    Box(modifier = modifier) {
        // Background with icon — only show when swiping left
        if (animatedOffsetX < -1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape)
                    .background(leftBackgroundColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (swipeProgress > 0.1f) {
                    Icon(
                        imageVector = leftIcon,
                        contentDescription = leftContentDescription,
                        tint = leftIconColor,
                        modifier = Modifier
                            .padding(end = iconPadding.dp)
                            .scale(iconScale)
                    )
                }
            }
        }

        // Right-swipe background with edit icon
        if (animatedOffsetX > 1f && onSwipeRight != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape)
                    .background(rightBackgroundColor),
                contentAlignment = Alignment.CenterStart
            ) {
                if (swipeProgress > 0.1f) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit title",
                        tint = rightIconColor,
                        modifier = Modifier
                            .padding(start = iconPadding.dp)
                            .scale(iconScale)
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
                            if (offsetX < 0 && offsetX.absoluteValue > thresholdPx) {
                                shouldTriggerLeftAction = true
                            } else if (offsetX > 0 && offsetX > thresholdPx) {
                                shouldTriggerRightAction = true
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            offsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = offsetX + dragAmount
                            if (newOffset <= 0) {
                                // Left swipe (archive/delete)
                                offsetX = newOffset.coerceIn(-maxSwipePx, 0f)
                            } else if (onSwipeRight != null) {
                                // Right swipe (edit) - only if actions are provided
                                offsetX = newOffset.coerceIn(0f, maxSwipePx)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    return start + (end - start) * fraction
}
