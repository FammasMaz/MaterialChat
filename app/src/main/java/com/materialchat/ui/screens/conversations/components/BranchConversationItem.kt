package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.ui.screens.conversations.ConversationUiItem

/**
 * M3 Expressive branch conversation item displayed under its parent conversation.
 *
 * Design specifications per M3 Expressive:
 * - Uses `surfaceContainerHigh` for container (elevated emphasis)
 * - Uses `secondary` color for branch indicator
 * - Uses `medium` (12dp) corner radius with shape morphing on press
 * - 48dp minimum touch target
 * - Spring-based animations:
 *   - Spatial (scale, shape): damping=0.6, can overshoot
 *   - Effects (color): damping=1.0, no overshoot
 *
 * @param conversationItem The branch conversation data to display
 * @param onClick Callback when the item is clicked
 * @param isFirst Whether this is the first branch in the list
 * @param isLast Whether this is the last branch in the list
 * @param isOnly Whether this is the only branch
 * @param showConnector Whether to show the branch connector line
 * @param modifier Modifier for the item
 */
@Composable
fun BranchConversationItem(
    conversationItem: ConversationUiItem,
    onClick: () -> Unit,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isOnly: Boolean = false,
    showConnector: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive spring specs
    // Spatial: Can overshoot (damping < 1)
    val spatialSpring = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 500f
    )
    // Effects: No overshoot (damping = 1)
    val effectsSpring = spring<Float>(
        dampingRatio = 1.0f,
        stiffness = 300f
    )

    // Scale animation - spatial spring
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spatialSpring,
        label = "branchScale"
    )

    // Shape morphing - M3 Expressive: pressed = less rounded
    // Resting: medium (12dp), Pressed: small (8dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 500f
        ),
        label = "branchCornerRadius"
    )

    // Background color animation - effects spring
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = 300f
        ),
        label = "branchBackgroundColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp), // Ensure 48dp+ touch target
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Branch connector - M3 curved line
        if (showConnector) {
            BranchConnector(
                isFirst = isFirst,
                isLast = isLast,
                isOnly = isOnly,
                color = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 2.dp,
                modifier = Modifier.height(56.dp)
            )
        }

        // Branch item content
        Box(
            modifier = Modifier
                .weight(1f)
                .scale(scale)
                .clip(RoundedCornerShape(cornerRadius))
                .background(backgroundColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple()
                ) { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branch icon - uses secondary color per M3 hierarchy
                Icon(
                    imageVector = Icons.Filled.CallSplit,
                    contentDescription = "Branch",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(90f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Title with icon if present
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (conversationItem.conversation.icon != null) {
                            Text(
                                text = conversationItem.conversation.icon,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = conversationItem.conversation.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // Model name - uses onSurfaceVariant per M3
                    Text(
                        text = conversationItem.conversation.modelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Relative time
                Text(
                    text = conversationItem.relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
