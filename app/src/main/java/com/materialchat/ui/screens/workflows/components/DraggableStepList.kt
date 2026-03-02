package com.materialchat.ui.screens.workflows.components

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.materialchat.ui.theme.ExpressiveMotion
import kotlin.math.roundToInt

/**
 * A composable that wraps a Column with drag-to-reorder support.
 *
 * Items can be reordered by long-pressing and dragging. During drag,
 * the dragged item visually follows the pointer while other items
 * animate their positions with spring animation.
 *
 * @param items The list of items to display
 * @param onMove Callback when items are reordered with (fromIndex, toIndex)
 * @param modifier Modifier for the outer column
 * @param itemContent Composable content for each item, given the item and its index
 */
@Composable
fun <T> DraggableStepList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int) -> Unit
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeights by remember { mutableStateOf(mapOf<Int, Int>()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isDragged = draggedIndex == index

            // Calculate the target offset for non-dragged items based on
            // whether the dragged item has passed over them
            val targetOffset = remember(draggedIndex, dragOffset, index) {
                calculateItemOffset(
                    itemIndex = index,
                    draggedIndex = draggedIndex,
                    dragOffset = dragOffset,
                    itemHeights = itemHeights
                )
            }

            val animatedOffset by animateIntOffsetAsState(
                targetValue = if (isDragged) {
                    IntOffset(0, dragOffset.roundToInt())
                } else {
                    IntOffset(0, targetOffset)
                },
                animationSpec = if (isDragged) {
                    ExpressiveMotion.Spatial.default()
                } else {
                    ExpressiveMotion.Spatial.container()
                },
                label = "itemOffset_$index"
            )

            Box(
                modifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .offset { animatedOffset }
                    .onGloballyPositioned { coordinates ->
                        itemHeights = itemHeights + (index to coordinates.size.height)
                    }
                    .pointerInput(items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y

                                // Determine if we've crossed into another item's territory
                                val targetIndex = findTargetIndex(
                                    draggedIndex = index,
                                    dragOffset = dragOffset,
                                    itemHeights = itemHeights,
                                    itemCount = items.size
                                )

                                if (targetIndex != null && targetIndex != draggedIndex) {
                                    onMove(draggedIndex!!, targetIndex)
                                    draggedIndex = targetIndex
                                    dragOffset = 0f
                                }
                            },
                            onDragEnd = {
                                draggedIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggedIndex = null
                                dragOffset = 0f
                            }
                        )
                    }
            ) {
                itemContent(item, index)
            }
        }
    }
}

/**
 * Calculates the offset for a non-dragged item based on the dragged item's position.
 * If the dragged item has passed over this item, it shifts up or down.
 */
private fun calculateItemOffset(
    itemIndex: Int,
    draggedIndex: Int?,
    dragOffset: Float,
    itemHeights: Map<Int, Int>
): Int {
    if (draggedIndex == null || itemIndex == draggedIndex) return 0

    val draggedHeight = itemHeights[draggedIndex] ?: return 0
    val spacing = 12 // approximate spacing in pixels

    // Determine displacement direction based on drag direction
    return when {
        // Dragging down: items between draggedIndex and target shift up
        dragOffset > 0 && itemIndex > draggedIndex -> {
            val threshold = (0 until itemIndex - draggedIndex).sumOf {
                (itemHeights[draggedIndex + it + 1] ?: draggedHeight) + spacing
            }
            if (dragOffset > threshold / 2f) -(draggedHeight + spacing) else 0
        }
        // Dragging up: items between target and draggedIndex shift down
        dragOffset < 0 && itemIndex < draggedIndex -> {
            val threshold = (0 until draggedIndex - itemIndex).sumOf {
                (itemHeights[itemIndex + it] ?: draggedHeight) + spacing
            }
            if (-dragOffset > threshold / 2f) (draggedHeight + spacing) else 0
        }
        else -> 0
    }
}

/**
 * Finds the target index for a reorder based on how far the dragged item
 * has been displaced.
 */
private fun findTargetIndex(
    draggedIndex: Int,
    dragOffset: Float,
    itemHeights: Map<Int, Int>,
    itemCount: Int
): Int? {
    if (dragOffset == 0f) return null

    val spacing = 12 // approximate spacing
    var accumulated = 0f

    if (dragOffset > 0) {
        // Dragging down
        for (i in draggedIndex + 1 until itemCount) {
            val h = (itemHeights[i] ?: 100) + spacing
            accumulated += h
            if (dragOffset > accumulated - h / 2f) {
                return i
            }
        }
    } else {
        // Dragging up
        for (i in draggedIndex - 1 downTo 0) {
            val h = (itemHeights[i] ?: 100) + spacing
            accumulated += h
            if (-dragOffset > accumulated - h / 2f) {
                return i
            }
        }
    }

    return null
}
