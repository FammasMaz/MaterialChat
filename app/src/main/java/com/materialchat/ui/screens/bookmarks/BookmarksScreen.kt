package com.materialchat.ui.screens.bookmarks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.screens.bookmarks.components.BookmarkCard
import com.materialchat.ui.screens.bookmarks.components.BookmarkFilterBar
import com.materialchat.ui.screens.bookmarks.components.BookmarkSearchBar

/**
 * Bookmarks / Knowledge Base screen.
 *
 * Displays bookmarked messages in a searchable, filterable list with category
 * and tag organization. Follows Material 3 Expressive design with spring-physics
 * animations, rounded corners, and expressive surfaces.
 *
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToConversation Callback to navigate to a specific conversation
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit = {},
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Knowledge Base",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            when (val state = uiState) {
                is BookmarksUiState.Loading -> {
                    BookmarksLoadingContent()
                }
                is BookmarksUiState.Error -> {
                    BookmarksErrorContent(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        onNavigateBack = onNavigateBack
                    )
                }
                is BookmarksUiState.Success -> {
                    BookmarksSuccessContent(
                        state = state,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onCategorySelected = { viewModel.selectCategory(it) },
                        onTagSelected = { viewModel.selectTag(it) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onNavigateToConversation = onNavigateToConversation
                    )
                }
            }
        }
    }
}

/**
 * Loading state content with centered progress indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookmarksLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Error state content with retry option.
 */
@Composable
private fun BookmarksErrorContent(
    message: String,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
        TextButton(onClick = onNavigateBack) {
            Text("Go Back")
        }
    }
}

/**
 * Main content with search bar, filter bar, and bookmark cards list.
 */
@Composable
private fun BookmarksSuccessContent(
    state: BookmarksUiState.Success,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (com.materialchat.domain.model.BookmarkCategory?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onNavigateToConversation: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        // Search bar
        BookmarkSearchBar(
            query = state.searchQuery,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Filter bar
        BookmarkFilterBar(
            selectedCategory = state.selectedCategory,
            selectedTag = state.selectedTag,
            allTags = state.allTags,
            onCategorySelected = onCategorySelected,
            onTagSelected = onTagSelected,
            modifier = Modifier.fillMaxWidth()
        )

        // Bookmark cards list
        val displayBookmarks = state.filteredBookmarks

        if (displayBookmarks.isEmpty()) {
            EmptyBookmarksContent(
                hasFilters = state.selectedCategory != null ||
                    state.selectedTag != null ||
                    state.searchQuery.isNotBlank()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = displayBookmarks,
                    key = { it.bookmark.id }
                ) { bookmarkItem ->
                    BookmarkCard(
                        bookmarkWithMessage = bookmarkItem,
                        onDelete = { onDeleteBookmark(bookmarkItem.bookmark.id) },
                        onNavigateToConversation = { onNavigateToConversation(bookmarkItem.conversationId) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    )
                }
            }
        }
    }
}

/**
 * Empty state content shown when no bookmarks match the current filters.
 */
@Composable
private fun EmptyBookmarksContent(hasFilters: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = if (hasFilters) "No bookmarks match your filters"
                       else "No bookmarks yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (!hasFilters) {
                Text(
                    text = "Bookmark messages from any conversation\nto build your knowledge base",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
