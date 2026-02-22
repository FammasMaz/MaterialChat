package com.materialchat.ui.screens.bookmarks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.ui.screens.bookmarks.BookmarkWithMessage

/**
 * A card displaying a bookmarked message in the knowledge base.
 *
 * Shows the category icon, message content preview, model badge,
 * conversation title, tag chips, and action buttons.
 * Follows Material 3 Expressive design with RoundedCornerShape(20.dp).
 *
 * @param bookmarkWithMessage The bookmark data with associated message info
 * @param onDelete Callback when the delete button is clicked
 * @param onNavigateToConversation Callback to navigate to the source conversation
 * @param modifier Modifier for the card
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkCard(
    bookmarkWithMessage: BookmarkWithMessage,
    onDelete: () -> Unit,
    onNavigateToConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bookmark = bookmarkWithMessage.bookmark
    val categoryIcon = getCategoryIcon(bookmark.category)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top row: Category icon + conversation title + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = bookmark.category.displayName,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Conversation title
                Text(
                    text = bookmarkWithMessage.conversationTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Model badge
                if (bookmarkWithMessage.modelName != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = bookmarkWithMessage.modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message content preview
            Text(
                text = bookmarkWithMessage.messageContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Note (if present)
            if (!bookmark.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = bookmark.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tags
            if (bookmark.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    bookmark.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            border = null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom row: Category label + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category label
                Text(
                    text = bookmark.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )

                // Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToConversation,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Go to conversation",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete bookmark",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Maps a [BookmarkCategory] to its corresponding Material icon.
 */
@Composable
fun getCategoryIcon(category: BookmarkCategory): ImageVector {
    return when (category) {
        BookmarkCategory.CODE -> Icons.Default.Code
        BookmarkCategory.EXPLANATION -> Icons.Default.School
        BookmarkCategory.IDEA -> Icons.Default.Lightbulb
        BookmarkCategory.REFERENCE -> Icons.AutoMirrored.Filled.MenuBook
        BookmarkCategory.HOWTO -> Icons.Outlined.Build
        BookmarkCategory.GENERAL -> Icons.Outlined.BookmarkBorder
    }
}
