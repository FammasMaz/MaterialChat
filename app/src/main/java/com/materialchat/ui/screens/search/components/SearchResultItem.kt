package com.materialchat.ui.screens.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.MatchType
import com.materialchat.ui.screens.search.MessageSnippet
import com.materialchat.ui.screens.search.SearchResultUiItem
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.MaterialChatMotion

/**
 * A single search result item displaying a conversation with matching messages.
 *
 * Follows a title-first approach where the conversation is the primary result,
 * with matching messages shown branched underneath.
 *
 * @param item The search result to display
 * @param onClick Callback when the item is clicked
 * @param modifier Modifier for the item
 */
@Composable
fun SearchResultItem(
    item: SearchResultUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isExpanded by remember { mutableStateOf(true) }

    // Press animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) MaterialChatMotion.Scales.Pressed else MaterialChatMotion.Scales.Normal,
        animationSpec = spring(
            dampingRatio = MaterialChatMotion.Springs.ScalePress.dampingRatio,
            stiffness = MaterialChatMotion.Springs.ScalePress.stiffness
        ),
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(),
        label = "backgroundColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(CustomShapes.ConversationItem)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Main conversation row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or emoji
            if (item.icon != null) {
                Text(
                    text = item.icon,
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

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Match type badge and model
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Match type badge
                    Text(
                        text = when (item.matchType) {
                            MatchType.TITLE -> "Title"
                            MatchType.CONTENT -> "Content"
                            MatchType.BOTH -> "Title & Content"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.matchType) {
                            MatchType.TITLE -> MaterialTheme.colorScheme.primary
                            MatchType.CONTENT -> MaterialTheme.colorScheme.secondary
                            MatchType.BOTH -> MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier
                            .clip(CustomShapes.Pill)
                            .background(
                                when (item.matchType) {
                                    MatchType.TITLE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    MatchType.CONTENT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    MatchType.BOTH -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    // Model name
                    Text(
                        text = item.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Timestamp
            Text(
                text = item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Matching messages (branched from title)
        AnimatedVisibility(
            visible = isExpanded && item.matchingMessages.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 56.dp, top = 8.dp)
            ) {
                item.matchingMessages.take(3).forEach { snippet ->
                    MessageSnippetItem(snippet = snippet)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * A single message snippet within a search result.
 */
@Composable
private fun MessageSnippetItem(
    snippet: MessageSnippet,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CustomShapes.InlineCode)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Role icon
        Icon(
            imageVector = if (snippet.role.lowercase() == "user") {
                Icons.Outlined.Person
            } else {
                Icons.Outlined.SmartToy
            },
            contentDescription = snippet.role,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Snippet text with highlighting
        Text(
            text = snippet.highlightedSnippet,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
