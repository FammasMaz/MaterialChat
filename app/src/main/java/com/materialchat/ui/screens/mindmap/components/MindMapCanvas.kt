package com.materialchat.ui.screens.mindmap.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.ConversationTreeNode
import kotlin.math.max

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------

private val NODE_WIDTH_DP = 140.dp
private val NODE_HEIGHT_DP = 60.dp
private val HORIZONTAL_SPACING_DP = 24.dp
private val VERTICAL_SPACING_DP = 80.dp
private val CORNER_RADIUS_DP = 16.dp
private val PADDING_TOP_DP = 40.dp

// ---------------------------------------------------------------------------
// Layout data model
// ---------------------------------------------------------------------------

/**
 * A tree node with computed layout position and its laid-out children.
 *
 * @property node The original conversation tree node
 * @property x Center-x position in canvas coordinate space
 * @property y Top-y position in canvas coordinate space
 * @property subtreeWidth Total horizontal width consumed by this subtree
 * @property children Positioned child nodes
 */
private data class LayoutNode(
    val node: ConversationTreeNode,
    val x: Float,
    val y: Float,
    val subtreeWidth: Float,
    val children: List<LayoutNode>
)

/**
 * Represents an edge between two points (parent bottom-center to child top-center).
 */
private data class Edge(val from: Offset, val to: Offset)

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * Interactive mind map canvas that renders the conversation tree.
 *
 * Implements a simplified top-down Reingold-Tilford layout where each
 * parent node is centered above its children. Supports pinch-to-zoom,
 * pan gestures, tap to select, and long-press to show a tooltip.
 *
 * The current conversation node displays a pulsing glow effect using
 * an [InfiniteTransition].
 *
 * @param tree The root node of the conversation tree
 * @param selectedNodeId The currently selected node ID
 * @param onNodeTap Called when a node is tapped
 * @param onNodeDoubleTap Called when a node is double-tapped
 * @param modifier Modifier for the canvas
 */
@Composable
fun MindMapCanvas(
    tree: ConversationTreeNode,
    selectedNodeId: String?,
    onNodeTap: (String) -> Unit,
    onNodeDoubleTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Convert dp dimensions to pixels
    val nodeWidthPx = with(density) { NODE_WIDTH_DP.toPx() }
    val nodeHeightPx = with(density) { NODE_HEIGHT_DP.toPx() }
    val hSpacingPx = with(density) { HORIZONTAL_SPACING_DP.toPx() }
    val vSpacingPx = with(density) { VERTICAL_SPACING_DP.toPx() }
    val cornerRadiusPx = with(density) { CORNER_RADIUS_DP.toPx() }
    val paddingTopPx = with(density) { PADDING_TOP_DP.toPx() }

    // Zoom and pan state (survives configuration changes)
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    // Tooltip state
    var tooltipNode by remember { mutableStateOf<ConversationTreeNode?>(null) }
    var tooltipOffset by remember { mutableStateOf(IntOffset.Zero) }

    // Pulsing glow for current conversation node
    val infiniteTransition = rememberInfiniteTransition(label = "currentNodePulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // M3 color tokens
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainerColor = MaterialTheme.colorScheme.onSecondaryContainer
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    // Compute layout tree and derived flat/edge lists
    val layoutRoot = remember(tree, nodeWidthPx, nodeHeightPx, hSpacingPx, vSpacingPx, paddingTopPx) {
        computeLayout(
            node = tree,
            x = 0f,
            y = paddingTopPx,
            nodeWidth = nodeWidthPx,
            nodeHeight = nodeHeightPx,
            hSpacing = hSpacingPx,
            vSpacing = vSpacingPx
        )
    }

    val flatNodes = remember(layoutRoot) { flattenLayout(layoutRoot) }
    val edges = remember(layoutRoot) { collectEdges(layoutRoot, nodeHeightPx) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(flatNodes, scale, offsetX, offsetY) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            findNodeAtPosition(
                                tapOffset, flatNodes, scale, offsetX, offsetY,
                                nodeWidthPx, nodeHeightPx
                            )?.let { node -> onNodeTap(node.id) }
                        },
                        onDoubleTap = { tapOffset ->
                            findNodeAtPosition(
                                tapOffset, flatNodes, scale, offsetX, offsetY,
                                nodeWidthPx, nodeHeightPx
                            )?.let { node -> onNodeDoubleTap(node.id) }
                        },
                        onLongPress = { tapOffset ->
                            findNodeAtPosition(
                                tapOffset, flatNodes, scale, offsetX, offsetY,
                                nodeWidthPx, nodeHeightPx
                            )?.let { node ->
                                tooltipNode = node
                                tooltipOffset = IntOffset(
                                    tapOffset.x.toInt(),
                                    tapOffset.y.toInt()
                                )
                            }
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        ) {
            // Draw edges (Bezier curves)
            edges.forEach { (from, to) ->
                drawEdge(from, to, outlineVariantColor)
            }

            // Draw nodes
            flatNodes.forEach { layoutNode ->
                drawNode(
                    layoutNode = layoutNode,
                    nodeWidth = nodeWidthPx,
                    nodeHeight = nodeHeightPx,
                    cornerRadius = cornerRadiusPx,
                    selectedNodeId = selectedNodeId,
                    glowAlpha = glowAlpha,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onPrimaryContainerColor = onPrimaryContainerColor,
                    onPrimaryColor = onPrimaryColor,
                    secondaryContainerColor = secondaryContainerColor,
                    onSecondaryContainerColor = onSecondaryContainerColor
                )
            }
        }

        // Tooltip popup on long-press
        tooltipNode?.let { node ->
            NodePreviewTooltip(
                node = node,
                offset = tooltipOffset,
                onDismiss = { tooltipNode = null }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tree layout algorithm (simplified Reingold-Tilford, top-down)
// ---------------------------------------------------------------------------

/**
 * Recursively lays out the tree, computing x/y positions for each node.
 *
 * Each leaf node occupies [nodeWidth] of horizontal space. Each parent is
 * centered above the horizontal extent of its children.
 */
private fun computeLayout(
    node: ConversationTreeNode,
    x: Float,
    y: Float,
    nodeWidth: Float,
    nodeHeight: Float,
    hSpacing: Float,
    vSpacing: Float
): LayoutNode {
    if (node.children.isEmpty()) {
        return LayoutNode(
            node = node,
            x = x + nodeWidth / 2f,
            y = y,
            subtreeWidth = nodeWidth,
            children = emptyList()
        )
    }

    val childY = y + nodeHeight + vSpacing
    var childX = x
    val layoutChildren = mutableListOf<LayoutNode>()

    node.children.forEachIndexed { index, child ->
        val layoutChild = computeLayout(
            child, childX, childY, nodeWidth, nodeHeight, hSpacing, vSpacing
        )
        layoutChildren.add(layoutChild)
        childX += layoutChild.subtreeWidth
        if (index < node.children.size - 1) {
            childX += hSpacing
        }
    }

    val totalChildrenWidth = layoutChildren.sumOf { it.subtreeWidth.toDouble() }.toFloat() +
            hSpacing * max(0, layoutChildren.size - 1)
    val subtreeWidth = max(nodeWidth, totalChildrenWidth)

    val firstChildCenter = layoutChildren.first().x
    val lastChildCenter = layoutChildren.last().x
    val centerX = (firstChildCenter + lastChildCenter) / 2f

    return LayoutNode(
        node = node,
        x = centerX,
        y = y,
        subtreeWidth = subtreeWidth,
        children = layoutChildren
    )
}

/**
 * Flattens a [LayoutNode] tree into a list for rendering.
 */
private fun flattenLayout(root: LayoutNode): List<LayoutNode> {
    val result = mutableListOf<LayoutNode>()
    fun collect(n: LayoutNode) {
        result.add(n)
        n.children.forEach { collect(it) }
    }
    collect(root)
    return result
}

/**
 * Collects all parent-to-child edges from the layout tree.
 */
private fun collectEdges(root: LayoutNode, nodeHeight: Float): List<Edge> {
    val edges = mutableListOf<Edge>()
    fun collect(n: LayoutNode) {
        val parentBottom = Offset(n.x, n.y + nodeHeight)
        n.children.forEach { child ->
            edges.add(Edge(parentBottom, Offset(child.x, child.y)))
            collect(child)
        }
    }
    collect(root)
    return edges
}

// ---------------------------------------------------------------------------
// Hit testing
// ---------------------------------------------------------------------------

/**
 * Finds which node (if any) is at the given screen-space tap position,
 * accounting for the current zoom scale and pan offset.
 */
private fun findNodeAtPosition(
    tapOffset: Offset,
    flatNodes: List<LayoutNode>,
    scale: Float,
    panX: Float,
    panY: Float,
    nodeWidth: Float,
    nodeHeight: Float
): ConversationTreeNode? {
    val canvasX = (tapOffset.x - panX) / scale
    val canvasY = (tapOffset.y - panY) / scale

    return flatNodes.firstOrNull { layoutNode ->
        val left = layoutNode.x - nodeWidth / 2f
        val top = layoutNode.y
        canvasX in left..(left + nodeWidth) && canvasY in top..(top + nodeHeight)
    }?.node
}

// ---------------------------------------------------------------------------
// Drawing helpers
// ---------------------------------------------------------------------------

/**
 * Draws a Bezier curve edge from parent bottom-center to child top-center.
 */
private fun DrawScope.drawEdge(from: Offset, to: Offset, color: Color) {
    val controlPointOffset = (to.y - from.y) / 2f
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(
            x1 = from.x,
            y1 = from.y + controlPointOffset,
            x2 = to.x,
            y2 = to.y - controlPointOffset,
            x3 = to.x,
            y3 = to.y
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * Draws a single node rectangle with title text, message count badge,
 * and optional icon emoji.
 */
private fun DrawScope.drawNode(
    layoutNode: LayoutNode,
    nodeWidth: Float,
    nodeHeight: Float,
    cornerRadius: Float,
    selectedNodeId: String?,
    glowAlpha: Float,
    primaryColor: Color,
    primaryContainerColor: Color,
    onPrimaryContainerColor: Color,
    onPrimaryColor: Color,
    secondaryContainerColor: Color,
    onSecondaryContainerColor: Color
) {
    val node = layoutNode.node
    val left = layoutNode.x - nodeWidth / 2f
    val top = layoutNode.y

    // Determine node colors based on state
    val (bgColor, textColor) = when {
        node.isCurrentConversation -> primaryColor to onPrimaryColor
        node.icon == null -> primaryContainerColor to onPrimaryContainerColor
        else -> secondaryContainerColor to onSecondaryContainerColor
    }

    // Pulsing glow for current conversation
    if (node.isCurrentConversation) {
        val glowExpand = 6.dp.toPx()
        drawRoundRect(
            color = primaryColor.copy(alpha = glowAlpha),
            topLeft = Offset(left - glowExpand, top - glowExpand),
            size = Size(nodeWidth + glowExpand * 2, nodeHeight + glowExpand * 2),
            cornerRadius = CornerRadius(cornerRadius + glowExpand)
        )
    }

    // Selection outline for non-current selected node
    if (node.id == selectedNodeId && !node.isCurrentConversation) {
        val outlineWidth = 3.dp.toPx()
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(left - outlineWidth, top - outlineWidth),
            size = Size(nodeWidth + outlineWidth * 2, nodeHeight + outlineWidth * 2),
            cornerRadius = CornerRadius(cornerRadius + outlineWidth),
            style = Stroke(width = outlineWidth)
        )
    }

    // Node background
    drawRoundRect(
        color = bgColor,
        topLeft = Offset(left, top),
        size = Size(nodeWidth, nodeHeight),
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Draw text using native canvas for emoji and text rendering
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 13.dp.toPx()
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.NORMAL
            )
        }

        // Build display title: optional icon prefix + truncated title
        val iconPrefix = if (!node.icon.isNullOrEmpty()) "${node.icon} " else ""
        val displayTitle = if (node.title.length > 12) {
            node.title.take(12) + "..."
        } else {
            node.title
        }
        val fullTitle = "$iconPrefix$displayTitle"

        val textX = left + 12.dp.toPx()
        val textY = top + nodeHeight / 2f - 2.dp.toPx()
        drawText(fullTitle, textX, textY, textPaint)

        // Message count badge (bottom-right area)
        val badgePaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.7f).toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }
        val badgeText = "${node.messageCount} msgs"
        val badgeWidth = badgePaint.measureText(badgeText)
        drawText(
            badgeText,
            left + nodeWidth - badgeWidth - 10.dp.toPx(),
            top + nodeHeight - 8.dp.toPx(),
            badgePaint
        )
    }
}

/**
 * Converts a Compose [Color] to an ARGB integer for use with Android's
 * native [android.graphics.Paint].
 */
private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
