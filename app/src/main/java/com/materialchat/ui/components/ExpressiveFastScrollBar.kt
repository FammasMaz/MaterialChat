package com.materialchat.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private data class FastScrollMetrics(
    val progress: Float,
    val totalItems: Int,
    val maxScrollIndex: Int,
    val scrollableHeightPx: Float
)

/**
 * Expressive fast scrollbar for long lazy lists.
 *
 * It stays slim during normal reading, expands into a grabbable handle while
 * pressed/dragged, and can show a compact glyph bubble for fast orientation.
 */
@Composable
fun ExpressiveFastScrollBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    thickness: Dp = 8.dp,
    expandedWidth: Dp = 28.dp,
    paddingEnd: Dp = 4.dp,
    trackGap: Dp = 8.dp,
    dragLabelProvider: ((Int) -> String?)? = null,
    dragLabelMinWidth: Dp = 112.dp,
    dragLabelMaxWidth: Dp = 200.dp,
    dragLabelMinHeight: Dp = 56.dp,
    dragLabelGap: Dp = 12.dp
) {
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var pendingScrollIndex by remember { mutableIntStateOf(-1) }
    var retainedDragLabel by remember { mutableStateOf<String?>(null) }
    val displayedProgress = remember { Animatable(0f) }
    var synced by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val canScroll by remember {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    if (!canScroll) return

    val animatedWidth by animateDpAsState(
        targetValue = if (isPressed || isDragging) expandedWidth else thickness,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "fastScrollWidth"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "fastScrollIconAlpha"
    )
    val squiggleAmount by animateFloatAsState(
        targetValue = if (isDragging || isPressed || listState.isScrollInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "fastScrollSquiggle"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(expandedWidth + paddingEnd)
    ) {
        val constraintsMaxWidth = maxWidth
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val minHeightPx = with(density) { minHeight.toPx() }
        val coarseJumpThresholdPx = with(density) { 16.dp.toPx() }
        val smoothJumpMinDistancePx = with(density) { 10.dp.toPx() }

        fun metrics(): FastScrollMetrics {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 0) {
                return FastScrollMetrics(0f, 0, 1, 1f)
            }

            val visibleItems = layoutInfo.visibleItemsInfo
            val firstVisible = visibleItems.firstOrNull()
            val representativeSize = visibleItems
                .map { it.size }
                .filter { it > 0 }
                .sorted()
                .let { sizes -> sizes.getOrNull(sizes.size / 2) }
                ?.toFloat()
                ?: minHeightPx
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .toFloat()
                .coerceAtLeast(1f)
            val estimatedVisibleItems = (viewportHeight / representativeSize).coerceAtLeast(1f)
            val maxScrollIndex = (totalItems - estimatedVisibleItems)
                .roundToInt()
                .coerceAtLeast(1)
            val currentOffset = listState.firstVisibleItemIndex +
                (listState.firstVisibleItemScrollOffset / representativeSize)
            val progress = when {
                !listState.canScrollBackward -> 0f
                !listState.canScrollForward -> 1f
                else -> (currentOffset / maxScrollIndex).coerceIn(0f, 0.999f)
            }
            val scrollableHeight = (maxHeightPx - minHeightPx).coerceAtLeast(1f)

            return FastScrollMetrics(
                progress = progress,
                totalItems = totalItems,
                maxScrollIndex = maxScrollIndex,
                scrollableHeightPx = scrollableHeight
            )
        }

        fun updateProgressFromTouch(touchY: Float, grabOffset: Float) {
            val stats = metrics()
            val newProgress = ((touchY - grabOffset) / stats.scrollableHeightPx).coerceIn(0f, 1f)
            dragProgress = newProgress
            pendingScrollIndex = resolveFastScrollTargetIndex(
                progress = newProgress,
                maxScrollIndex = stats.maxScrollIndex,
                totalItems = stats.totalItems
            )
        }

        LaunchedEffect(Unit) {
            snapshotFlow { pendingScrollIndex }
                .distinctUntilChanged()
                .collectLatest { index ->
                    if (index >= 0) listState.scrollToItem(index)
                }
        }

        LaunchedEffect(listState, maxHeight, minHeight, isDragging) {
            if (isDragging) return@LaunchedEffect
            snapshotFlow { metrics() }
                .distinctUntilChanged()
                .collectLatest { stats ->
                    if (!synced) {
                        displayedProgress.snapTo(stats.progress)
                        synced = true
                    } else {
                        val handleDeltaPx = abs(stats.progress - displayedProgress.value) * stats.scrollableHeightPx
                        val estimatedStepPx = stats.scrollableHeightPx / stats.maxScrollIndex.coerceAtLeast(1)
                        val shouldSmooth = !listState.isScrollInProgress &&
                            estimatedStepPx >= coarseJumpThresholdPx &&
                            handleDeltaPx >= smoothJumpMinDistancePx
                        if (shouldSmooth) {
                            displayedProgress.animateTo(
                                targetValue = stats.progress,
                                animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing)
                            )
                        } else {
                            displayedProgress.snapTo(stats.progress)
                        }
                    }
                }
        }

        LaunchedEffect(isDragging, dragProgress) {
            if (isDragging && dragProgress >= 0f) {
                displayedProgress.snapTo(dragProgress)
                synced = true
            }
        }

        val dragTargetIndex = when {
            pendingScrollIndex >= 0 -> pendingScrollIndex
            else -> listState.firstVisibleItemIndex
        }
        val activeDragLabel = if (isDragging && dragLabelProvider != null && dragTargetIndex >= 0) {
            dragLabelProvider(dragTargetIndex)
        } else {
            null
        }
        val showDragLabel = isDragging && !activeDragLabel.isNullOrBlank()

        LaunchedEffect(activeDragLabel) {
            if (!activeDragLabel.isNullOrBlank()) retainedDragLabel = activeDragLabel
        }

        val labelAlpha by animateFloatAsState(
            targetValue = if (showDragLabel) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "fastScrollLabelAlpha"
        )
        val labelScale by animateFloatAsState(
            targetValue = if (showDragLabel) 1f else 0.82f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "fastScrollLabelScale"
        )
        val labelSlide by animateDpAsState(
            targetValue = if (showDragLabel) 0.dp else 8.dp,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "fastScrollLabelSlide"
        )
        val indicatorPath = remember { Path() }
        val primaryColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.secondaryContainer

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var grabOffset = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val stats = metrics()
                            val handleY = displayedProgress.value * stats.scrollableHeightPx
                            val onHandle = offset.y in handleY..(handleY + minHeightPx)
                            if (onHandle) {
                                grabOffset = offset.y - handleY
                                dragProgress = displayedProgress.value
                                pendingScrollIndex = listState.firstVisibleItemIndex
                            } else {
                                grabOffset = minHeightPx / 2f
                                updateProgressFromTouch(offset.y, grabOffset)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragProgress = -1f
                            pendingScrollIndex = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            dragProgress = -1f
                            pendingScrollIndex = -1
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateProgressFromTouch(change.position.y, grabOffset)
                        }
                    )
                }
        ) {
            val rightAnchorX = with(density) { (constraintsMaxWidth - paddingEnd).toPx() }
            val trackX = rightAnchorX - with(density) { thickness.toPx() / 2f }
            val rightCornerRadius = with(density) { 6.dp.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val stats = metrics()
                val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else displayedProgress.value
                val handleY = displayProgress * stats.scrollableHeightPx
                val handleHeight = minHeight.toPx()
                val trackStroke = thickness.toPx()
                val indicatorWidth = animatedWidth.toPx()
                val gap = trackGap.toPx()
                val currentIndicatorX = rightAnchorX - indicatorWidth
                val leftRadius = indicatorWidth / 2f
                val resolvedRightRadius = rightCornerRadius.coerceAtMost(indicatorWidth / 2f)

                if (handleY > gap) {
                    drawLine(
                        color = trackColor,
                        start = Offset(trackX, 0f),
                        end = Offset(trackX, handleY - gap),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                }
                if (handleY + handleHeight + gap < size.height) {
                    drawLine(
                        color = trackColor,
                        start = Offset(trackX, handleY + handleHeight + gap),
                        end = Offset(trackX, size.height),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                }

                indicatorPath.reset()
                val rect = Rect(
                    offset = Offset(currentIndicatorX, handleY),
                    size = Size(indicatorWidth, handleHeight)
                )
                if (squiggleAmount > 0.02f) {
                    val waveStrength = when {
                        isDragging -> 0.36f
                        isPressed -> 0.28f
                        else -> 0.2f
                    }
                    indicatorPath.addSquigglyFastScrollThumb(
                        rect = rect,
                        leftRadius = leftRadius,
                        rightRadius = resolvedRightRadius,
                        amplitude = (indicatorWidth * waveStrength).coerceAtLeast(3f) * squiggleAmount
                    )
                } else {
                    indicatorPath.addRoundRect(
                        RoundRect(
                            rect = rect,
                            topLeft = CornerRadius(leftRadius, leftRadius),
                            bottomLeft = CornerRadius(leftRadius, leftRadius),
                            topRight = CornerRadius(resolvedRightRadius, resolvedRightRadius),
                            bottomRight = CornerRadius(resolvedRightRadius, resolvedRightRadius)
                        )
                    )
                }
                drawPath(indicatorPath, primaryColor)
            }

            if (iconAlpha > 0f) {
                Icon(
                    imageVector = Icons.Rounded.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .offset {
                            val stats = metrics()
                            val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else displayedProgress.value
                            val handleY = displayProgress * stats.scrollableHeightPx
                            val iconSizePx = with(density) { 24.dp.toPx() }
                            val paddingEndPx = with(density) { paddingEnd.toPx() }
                            val animatedWidthPx = with(density) { animatedWidth.toPx() }
                            val maxWidthPx = with(density) { constraintsMaxWidth.toPx() }
                            IntOffset(
                                x = (maxWidthPx - paddingEndPx - (animatedWidthPx / 2f) - (iconSizePx / 2f)).toInt(),
                                y = (handleY + (minHeightPx / 2f) - (iconSizePx / 2f)).toInt()
                            )
                        }
                        .size(24.dp)
                        .graphicsLayer {
                            alpha = iconAlpha
                            scaleX = iconAlpha
                            scaleY = iconAlpha
                        }
                )
            }

            val label = activeDragLabel ?: retainedDragLabel
            if (labelAlpha > 0f && !label.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .offset {
                            val stats = metrics()
                            val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else displayedProgress.value
                            val handleY = displayProgress * stats.scrollableHeightPx
                            val labelWidthPx = with(density) { dragLabelMaxWidth.toPx() }
                            val labelHeightPx = with(density) { dragLabelMinHeight.toPx() }
                            val labelGapPx = with(density) { dragLabelGap.toPx() }
                            val labelSlidePx = with(density) { labelSlide.toPx() }
                            val paddingEndPx = with(density) { paddingEnd.toPx() }
                            val animatedWidthPx = with(density) { animatedWidth.toPx() }
                            val maxWidthPx = with(density) { constraintsMaxWidth.toPx() }
                            val indicatorX = maxWidthPx - paddingEndPx - animatedWidthPx
                            IntOffset(
                                x = (indicatorX - labelWidthPx - labelGapPx - labelSlidePx).toInt(),
                                y = (handleY + (minHeightPx / 2f) - (labelHeightPx / 2f)).toInt()
                            )
                        }
                        .widthIn(min = dragLabelMinWidth, max = dragLabelMaxWidth)
                        .heightIn(min = dragLabelMinHeight)
                        .graphicsLayer {
                            alpha = labelAlpha
                            scaleX = labelScale
                            scaleY = labelScale
                        },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun Path.addSquigglyFastScrollThumb(
    rect: Rect,
    leftRadius: Float,
    rightRadius: Float,
    amplitude: Float
) {
    val waveLength = (rect.height / 2.4f).coerceAtLeast(18f)
    moveTo(rect.left + leftRadius, rect.top)
    lineTo(rect.right - rightRadius, rect.top)

    var y = rect.top
    while (y <= rect.bottom) {
        val phase = ((y - rect.top) / waveLength) * (2f * PI.toFloat())
        val x = rect.right + (sin(phase.toDouble()).toFloat() * amplitude)
        lineTo(x, y)
        y += 4f
    }

    lineTo(rect.right - rightRadius, rect.bottom)
    lineTo(rect.left + leftRadius, rect.bottom)
    quadraticTo(rect.left, rect.bottom, rect.left, rect.bottom - leftRadius)
    lineTo(rect.left, rect.top + leftRadius)
    quadraticTo(rect.left, rect.top, rect.left + leftRadius, rect.top)
    close()
}

private fun resolveFastScrollTargetIndex(
    progress: Float,
    maxScrollIndex: Int,
    totalItems: Int
): Int {
    if (totalItems <= 1) return 0
    val lastIndex = totalItems - 1
    if (progress >= 1f) return lastIndex
    return (progress.coerceIn(0f, 1f) * maxScrollIndex.coerceAtLeast(1))
        .roundToInt()
        .coerceIn(0, lastIndex)
}

fun fastScrollGlyph(value: String?): String? {
    val leadingChar = value
        .orEmpty()
        .trim()
        .firstOrNull { it.isLetterOrDigit() }
        ?: return null

    return if (leadingChar.isDigit()) "#" else leadingChar.uppercaseChar().toString()
}
