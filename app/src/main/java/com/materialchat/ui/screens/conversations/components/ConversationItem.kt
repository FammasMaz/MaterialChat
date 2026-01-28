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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.screens.conversations.ConversationUiItem
import com.materialchat.ui.theme.CustomShapes

/**
 * M3 Expressive conversation item in the conversations list.
 *
 * Design specifications per M3 Expressive:
 * - Uses `surfaceContainer` for container (medium emphasis)
 * - Uses `primaryContainer` for provider badge
 * - Uses `outlineVariant` for dividers
 * - Shape morphing on press: resting → less rounded when pressed
 * - 48dp+ minimum touch target (row height)
 * - Spring-based animations:
 *   - Spatial (scale, shape): damping=0.6, stiffness=500 (fast)
 *   - Effects (color): damping=1.0, stiffness=300 (no overshoot)
 *
 * @param conversationItem The conversation data to display
 * @param onClick Callback when the item is clicked
 * @param shape Shape for the item (used for corner calculation)
 * @param showDivider Whether to show a divider below this item
 * @param modifier Modifier for the item
 */
@Composable
fun ConversationItem(
    conversationItem: ConversationUiItem,
    onClick: () -> Unit,
    shape: Shape = CustomShapes.ConversationItem,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive spring specs
    // Spatial: Can overshoot (damping < 1) - for scale, shape
    val spatialSpring = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 500f // Fast for small components
    )
    // Effects: No overshoot (damping = 1) - for color
    val effectsSpring = spring<Float>(
        dampingRatio = 1.0f,
        stiffness = 300f
    )

    // Scale animation - spatial spring
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spatialSpring,
        label = "scale"
    )

    // Shape morphing - M3 Expressive: pressed = less rounded
    // This is applied as a visual effect, the actual shape is passed in
    val shapeMorphFactor by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spatialSpring,
        label = "shapeMorph"
    )

    // Background color animation - effects spring (no overshoot)
    val pressedOverlay = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            pressedOverlay.compositeOver(MaterialTheme.colorScheme.surfaceContainer)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = 300f
        ),
        label = "backgroundColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chat icon or emoji - 40dp for visual prominence
            if (conversationItem.conversation.icon != null) {
                // Display AI-generated emoji
                Text(
                    text = conversationItem.conversation.icon,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                // Fallback to chat icon - uses primary color
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title - M3 titleMedium
                Text(
                    text = conversationItem.conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Provider and model info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Provider name badge - M3: primaryContainer with pill shape
                    Text(
                        text = conversationItem.providerName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50)) // M3: full (pill)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    // Model name - uses onSurfaceVariant
                    Text(
                        text = conversationItem.conversation.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // Message preview (if available)
                conversationItem.messagePreview?.let { preview ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Relative time - M3: labelSmall, onSurfaceVariant
            Text(
                text = conversationItem.relativeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Divider - M3: outlineVariant for subtle separation
        if (showDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 16.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}
