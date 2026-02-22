package com.materialchat.ui.screens.bookmarks.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.BookmarkCategory

/**
 * Horizontal filter bar for the Knowledge Base screen.
 *
 * Shows two scrollable rows:
 * 1. Category filter chips (CODE, EXPLANATION, IDEA, etc.)
 * 2. Tag filter chips (from all user-defined tags)
 *
 * Uses Material 3 FilterChip with spring-based color animations
 * for selected/unselected states.
 *
 * @param selectedCategory Currently selected category filter (null = all)
 * @param selectedTag Currently selected tag filter (null = all)
 * @param allTags All available tags from bookmarks
 * @param onCategorySelected Callback when a category chip is selected/deselected
 * @param onTagSelected Callback when a tag chip is selected/deselected
 * @param modifier Modifier for the filter bar
 */
@Composable
fun BookmarkFilterBar(
    selectedCategory: BookmarkCategory?,
    selectedTag: String?,
    allTags: List<String>,
    onCategorySelected: (BookmarkCategory?) -> Unit,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Category chips row
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookmarkCategory.entries.forEach { category ->
                val isSelected = selectedCategory == category

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "category_chip_color"
                )

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onCategorySelected(null) // Deselect
                        } else {
                            onCategorySelected(category)
                        }
                    },
                    label = {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = getCategoryIcon(category),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = containerColor,
                        selectedContainerColor = containerColor
                    )
                )
            }
        }

        // Tag chips row (only shown if tags exist)
        if (allTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allTags.forEach { tag ->
                    val isSelected = selectedTag == tag

                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tag_chip_color"
                    )

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onTagSelected(null) // Deselect
                            } else {
                                onTagSelected(tag)
                            }
                        },
                        label = {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = containerColor,
                            selectedContainerColor = containerColor
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
