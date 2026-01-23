package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * A swipe-to-delete container that wraps content and handles swipe gestures.
 *
 * When the user swipes left beyond the threshold, the onDelete callback is triggered.
 * The background reveals a delete icon as the content is swiped.
 *
 * @param onDelete Callback when the item should be deleted
 * @param modifier Modifier for the container
 * @param enabled Whether swipe-to-delete is enabled
 * @param hapticsEnabled Whether haptic feedback is enabled
 * @param content The content to display
 */
data class SwipeCornerSpec(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
)

@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    baseCorners: SwipeCornerSpec = SwipeCornerSpec(20.dp, 20.dp, 20.dp, 20.dp),
    activeCorners: SwipeCornerSpec = SwipeCornerSpec(20.dp, 20.dp, 20.dp, 20.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = rememberHapticFeedback()

    // Track the horizontal offset of the content
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var shouldDelete by remember { mutableStateOf(false) }
    var hasTriggeredThresholdHaptic by remember { mutableStateOf(false) }

    // Threshold for triggering delete (in dp)
    val deleteThresholdDp = 100.dp
    val deleteThresholdPx = with(density) { deleteThresholdDp.toPx() }

    // Maximum swipe distance
    val maxSwipeDp = 150.dp
    val maxSwipePx = with(density) { maxSwipeDp.toPx() }

    // M3 Expressive: SPATIAL spring for position (can bounce)
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isDragging) offsetX else 0f,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "offsetX"
    )

    // Calculate delete progress (0 to 1)
    val deleteProgress = (animatedOffsetX.absoluteValue / deleteThresholdPx).coerceIn(0f, 1f)

    // Trigger haptic feedback when crossing the threshold
    LaunchedEffect(deleteProgress > 0.9f) {
        if (deleteProgress > 0.9f && !hasTriggeredThresholdHaptic) {
            haptics.perform(HapticPattern.SWIPE_THRESHOLD, hapticsEnabled)
            hasTriggeredThresholdHaptic = true
        } else if (deleteProgress <= 0.9f) {
            hasTriggeredThresholdHaptic = false
        }
    }

    // M3 Expressive: EFFECTS spring for color (no bounce!)
    val backgroundColor by animateColorAsState(
        targetValue = if (deleteProgress > 0.9f) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "backgroundColor"
    )

    // M3 Expressive: SPATIAL spring for scale (bouncy feedback)
    val iconScale by animateFloatAsState(
        targetValue = if (deleteProgress > 0.5f) 1.2f else 0.8f + (deleteProgress * 0.4f),
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "iconScale"
    )

    // M3 Expressive: EFFECTS spring for icon color (no bounce)
    val iconColor by animateColorAsState(
        targetValue = if (deleteProgress > 0.9f) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "iconColor"
    )

    // M3 Expressive: EFFECTS spring for padding (smooth)
    val iconPadding by animateFloatAsState(
        targetValue = if (deleteProgress > 0.5f) 24f else 16f,
        animationSpec = ExpressiveMotion.Effects.elevation(),
        label = "iconPadding"
    )

    // Trigger delete when swipe is complete
    LaunchedEffect(shouldDelete) {
        if (shouldDelete) {
            onDelete()
            shouldDelete = false
            offsetX = 0f
        }
    }

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
        // Background with delete icon - only show when swiping
        if (animatedOffsetX.absoluteValue > 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(currentShape)
                    .background(backgroundColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (deleteProgress > 0.1f) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = iconColor,
                        modifier = Modifier
                            .padding(end = iconPadding.dp)
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
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptics.perform(HapticPattern.GESTURE_START, hapticsEnabled)
                        },
                        onDragEnd = {
                            isDragging = false
                            haptics.perform(HapticPattern.GESTURE_END, hapticsEnabled)
                            if (offsetX.absoluteValue > deleteThresholdPx) {
                                shouldDelete = true
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
                            // Only allow left swipe (negative offset)
                            val newOffset = offsetX + dragAmount
                            if (newOffset <= 0) {
                                offsetX = newOffset.coerceIn(-maxSwipePx, 0f)
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
