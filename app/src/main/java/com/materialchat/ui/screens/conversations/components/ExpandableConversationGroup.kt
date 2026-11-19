package com.materialchat.ui.screens.conversations.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.Conversation
import com.materialchat.ui.screens.conversations.ConversationUiItem
import com.materialchat.ui.screens.conversations.ConversationGroupUiItem
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.HomeSettingsSlot
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.components.rememberHomeSettingsPalette
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.SquircleShape

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
 * @param onArchiveToggle Callback when archive/restore is tapped on parent or branch
 * @param cornerRadius Corner radius for the container (M3: largeIncreased = 20dp)
 * @param seamRadius Corner radius on edges shared with neighbouring groups; non-zero
 *   values produce the segmented-list look where rows sit in a stack with a small gap
 * @param showDivider Whether to draw a divider under a collapsed parent row
 * @param isFirst Whether this is the first item in the list
 * @param isLast Whether this is the last item in the list
 * @param hapticsEnabled Whether haptic feedback is enabled
 * @param modifier Modifier for the group
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableConversationGroup(
    group: ConversationGroupUiItem,
    onParentClick: (String) -> Unit,
    onBranchClick: (String) -> Unit,
    onParentLongClick: (ConversationUiItem) -> Unit = {},
    onBranchLongClick: (ConversationUiItem) -> Unit = {},
    onExpandToggle: (String) -> Unit,
    onDelete: (Conversation) -> Unit,
    onArchiveToggle: (Conversation) -> Unit,
    onSwipeRight: (Conversation) -> Unit = {},
    cornerRadius: Dp = 20.dp, // M3: largeIncreased
    seamRadius: Dp = 0.dp,
    showDivider: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val parentInteractionSource = remember { MutableInteractionSource() }
    val parentPressed by parentInteractionSource.collectIsPressedAsState()

    // Chevron rotation: spatial spring from the theme (can overshoot)
    val chevronRotation by animateFloatAsState(
        targetValue = if (group.isExpanded) 180f else 0f,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "chevronRotation"
    )

    // Chevron tile rounds off while the group is open (spatial shape-morph spring)
    val chevronCorner by animateDpAsState(
        targetValue = if (group.isExpanded) 16.dp else 8.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "chevronCorner"
    )

    // Chevron tile pulses up on open (disclosure spring) so the toggle reads as a
    // container morph, not just a rotating glyph.
    val chevronScale by animateFloatAsState(
        targetValue = if (group.isExpanded) 1.12f else 1f,
        animationSpec = ExpressiveMotion.Spatial.disclosurePulse(),
        label = "chevronScale"
    )

    // Leading tile rounds off while the parent row is pressed, like ConversationItem.
    val tileCorner by animateDpAsState(
        targetValue = if (parentPressed) ExpressiveShapes.RowTile.Pressed else ExpressiveShapes.RowTile.Resting,
        animationSpec = ExpressiveMotion.Press.Shape,
        label = "parentTileCorner"
    )
    val tileShape = remember(tileCorner) { SquircleShape(tileCorner.coerceAtLeast(0.dp)) }

    // Calculate corner radii based on position and expansion state
    // M3 Expressive: Connected items share edges
    val topCornerRadius = if (isFirst) cornerRadius else seamRadius
    val bottomCornerRadius = when {
        isLast && !group.isExpanded -> cornerRadius
        isLast && group.isExpanded && !group.hasBranches -> cornerRadius
        else -> seamRadius
    }

    // Pressed parent rows morph to the full outer radius so they lift out of the stack.
    val parentShape = pressMorphShape(
        resting = SwipeCornerSpec(
            topStart = topCornerRadius,
            topEnd = topCornerRadius,
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius
        ),
        pressed = SwipeCornerSpec(cornerRadius, cornerRadius, cornerRadius, cornerRadius),
        isPressed = parentPressed
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Parent conversation with expand controls
        SwipeToDeleteBox(
            onDelete = { onDelete(group.parent.conversation) },
            hapticsEnabled = hapticsEnabled,
            onArchive = { onArchiveToggle(group.parent.conversation) },
            isArchived = group.parent.conversation.isArchived,
            onSwipeRight = { onSwipeRight(group.parent.conversation) },
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
                            .combinedClickable(
                                interactionSource = parentInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                    onParentClick(group.parent.conversation.id)
                                },
                                onLongClick = { onParentLongClick(group.parent) }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tonal squircle tile for the icon or emoji: the same leading
                        // treatment as ConversationItem so both row kinds read as one list.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(tileShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (group.parent.conversation.icon != null) {
                                Text(
                                    text = group.parent.conversation.icon,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

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

                                // Model name absorbs the squeeze (weighted + ellipsis) so the
                                // date is never crushed into one glyph per line.
                                Text(
                                    text = group.parent.conversation.modelName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Only weighted child: the unweighted date is measured
                                    // first and keeps its intrinsic width.
                                    modifier = Modifier.weight(1f)
                                )

                                if (group.parent.conversation.isArchived) {
                                    Text(
                                        text = "Archived",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

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

                                // Time rides the metadata line: as a sibling of the title it
                                // clipped names that had room to spare. No weighted spacer: it
                                // would halve the model name's share of the slack.
                                if (group.parent.isStreaming) {
                                    GroupActiveIndicator()
                                } else {
                                    RowDateLabel(text = group.parent.relativeTime)
                                }
                            }
                        }

                        // Expand chevron - M3: 48dp touch target
                        if (group.hasBranches) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                    onClick = {
                                        haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                                        onExpandToggle(group.parent.conversation.id)
                                    },
                                    // Morphs small → rounder when open, on the shape-morph spring.
                                    shape = RoundedCornerShape(chevronCorner),
                                    color = if (group.isExpanded) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier
                                        .size(32.dp) // Visual size, touch target handled by parent
                                        .scale(chevronScale)
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

                    // Divider - M3: outlineVariant for subtle separation
                    if (showDivider && !group.isExpanded && !isLast) {
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

        // Expanded branch list: shared disclosure physics (see ExpressiveMotion.Disclosure)
        AnimatedVisibility(
            visible = group.isExpanded && group.hasBranches,
            enter = expandVertically(
                animationSpec = ExpressiveMotion.Disclosure.Expand,
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = ExpressiveMotion.Disclosure.FadeIn),
            exit = shrinkVertically(
                animationSpec = ExpressiveMotion.Disclosure.Shrink,
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = ExpressiveMotion.Disclosure.FadeOut)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Branch count changes (add/delete) resize on the content-size spring
                    // instead of snapping; enter/exit stays on the disclosure physics above.
                    .animateContentSize(animationSpec = ExpressiveMotion.Spatial.contentSize())
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
                        onArchive = { onArchiveToggle(branch.conversation) },
                        isArchived = branch.conversation.isArchived,
                        onSwipeRight = { onSwipeRight(branch.conversation) },
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
                            onLongClick = { onBranchLongClick(branch) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupActiveIndicator() {
    // Label colour resolved against the streaming row fill (primary fails 4.5:1 on some light
    // palettes once primaryContainer is blended under it); the indicator keeps primary at 3:1.
    val palette = rememberHomeSettingsPalette()
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelSmall,
            color = palette[HomeSettingsSlot.RowActiveLabel]
        )
    }
}
