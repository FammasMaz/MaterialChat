package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.Conversation
import com.materialchat.ui.screens.conversations.ConversationGroupUiItem
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback

/**
 * M3 Expressive expandable conversation group component.
 *
 * Design specifications per M3 Expressive:
 * - Uses `surfaceContainer` for parent item (medium emphasis)
 * - Uses `surfaceContainerHigh` for branch items (elevated emphasis)
 * - Uses `secondaryContainer` for branch count badge
 * - Uses `outlineVariant` for branch connectors
 * - Shape tokens: `largeIncreased` (20dp) for parent, `medium` (12dp) for branches
 * - 48dp minimum touch targets
 * - Spring-based motion physics:
 *   - Spatial (position, rotation, size): damping=0.6, stiffness=380
 *   - Effects (opacity, color): damping=1.0, stiffness=300
 *
 * @param group The conversation group data
 * @param onParentClick Callback when the parent conversation is clicked
 * @param onBranchClick Callback when a branch conversation is clicked
 * @param onExpandToggle Callback to toggle the expanded state
 * @param onDelete Callback when delete is triggered on parent or branch
 * @param cornerRadius Corner radius for the container (M3: largeIncreased = 20dp)
 * @param isFirst Whether this is the first item in the list
 * @param isLast Whether this is the last item in the list
 * @param hapticsEnabled Whether haptic feedback is enabled
 * @param modifier Modifier for the group
 */
@Composable
fun ExpandableConversationGroup(
    group: ConversationGroupUiItem,
    onParentClick: (String) -> Unit,
    onBranchClick: (String) -> Unit,
    onExpandToggle: (String) -> Unit,
    onDelete: (Conversation) -> Unit,
    cornerRadius: Dp = 20.dp, // M3: largeIncreased
    isFirst: Boolean = false,
    isLast: Boolean = false,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()

    // M3 Expressive spring specs
    // Spatial: Can overshoot (damping < 1) - for position, rotation, size
    val spatialSpring = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 380f
    )
    // Effects: No overshoot (damping = 1) - for opacity, color
    val effectsSpring = spring<Float>(
        dampingRatio = 1.0f,
        stiffness = 300f
    )

    // Chevron rotation animation - spatial spring (can overshoot)
    val chevronRotation by animateFloatAsState(
        targetValue = if (group.isExpanded) 180f else 0f,
        animationSpec = spatialSpring,
        label = "chevronRotation"
    )

    // Calculate corner radii based on position and expansion state
    // M3 Expressive: Connected items share edges
    val topCornerRadius = if (isFirst) cornerRadius else 0.dp
    val bottomCornerRadius = when {
        isLast && !group.isExpanded -> cornerRadius
        isLast && group.isExpanded && !group.hasBranches -> cornerRadius
        else -> 0.dp
    }

    val parentShape = RoundedCornerShape(
        topStart = topCornerRadius,
        topEnd = topCornerRadius,
        bottomStart = bottomCornerRadius,
        bottomEnd = bottomCornerRadius
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Parent conversation with expand controls
        SwipeToDeleteBox(
            onDelete = { onDelete(group.parent.conversation) },
            hapticsEnabled = hapticsEnabled,
            baseCorners = SwipeCornerSpec(
                topStart = topCornerRadius,
                topEnd = topCornerRadius,
                bottomStart = bottomCornerRadius,
                bottomEnd = bottomCornerRadius
            ),
            activeCorners = SwipeCornerSpec(cornerRadius, cornerRadius, cornerRadius, cornerRadius),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = parentShape,
                color = MaterialTheme.colorScheme.surfaceContainer // M3: Medium emphasis
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                onParentClick(group.parent.conversation.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chat icon or emoji - 40dp for visual prominence
                        if (group.parent.conversation.icon != null) {
                            Text(
                                text = group.parent.conversation.icon,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
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
                                text = group.parent.conversation.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Provider, model, and branch count badges
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Provider name badge - M3: primaryContainer
                                Text(
                                    text = group.parent.providerName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50)) // M3: full (pill)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )

                                // Model name
                                Text(
                                    text = group.parent.conversation.modelName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Branch count badge - M3: secondaryContainer
                                if (group.hasBranches) {
                                    Text(
                                        text = "${group.branchCount} ${if (group.branchCount == 1) "branch" else "branches"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50)) // M3: full (pill)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Relative time and expand chevron
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = group.parent.relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Expand chevron - M3: 48dp touch target
                            if (group.hasBranches) {
                                Surface(
                                    onClick = {
                                        haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                        onExpandToggle(group.parent.conversation.id)
                                    },
                                    shape = RoundedCornerShape(8.dp), // M3: small
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(32.dp) // Visual size, touch target handled by parent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.ExpandMore,
                                            contentDescription = if (group.isExpanded) "Collapse branches" else "Expand branches",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    rotationZ = chevronRotation
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Divider - M3: outlineVariant for subtle separation
                    if (!group.isExpanded && !isLast) {
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
        }

        // Expanded branch list with M3 Expressive spring animations
        AnimatedVisibility(
            visible = group.isExpanded && group.hasBranches,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = 0.6f, // Spatial: can overshoot
                    stiffness = 380f
                ),
                expandFrom = Alignment.Top
            ) + fadeIn(
                animationSpec = spring(
                    dampingRatio = 1.0f, // Effects: no overshoot
                    stiffness = 300f
                )
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                ),
                shrinkTowards = Alignment.Top
            ) + fadeOut(
                animationSpec = spring(
                    dampingRatio = 1.0f,
                    stiffness = 300f
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (isLast) 8.dp else 4.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                group.branches.forEachIndexed { index, branch ->
                    val branchIsFirst = index == 0
                    val branchIsLast = index == group.branches.lastIndex
                    val branchIsOnly = group.branches.size == 1

                    SwipeToDeleteBox(
                        onDelete = { onDelete(branch.conversation) },
                        hapticsEnabled = hapticsEnabled,
                        baseCorners = SwipeCornerSpec(12.dp, 12.dp, 12.dp, 12.dp),
                        activeCorners = SwipeCornerSpec(16.dp, 16.dp, 16.dp, 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BranchConversationItem(
                            conversationItem = branch,
                            onClick = {
                                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                onBranchClick(branch.conversation.id)
                            },
                            isFirst = branchIsFirst,
                            isLast = branchIsLast,
                            isOnly = branchIsOnly,
                            showConnector = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
