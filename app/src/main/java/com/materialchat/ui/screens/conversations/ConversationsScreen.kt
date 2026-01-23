package com.materialchat.ui.screens.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import com.materialchat.ui.navigation.LocalAnimatedVisibilityScope
import com.materialchat.ui.navigation.LocalSharedTransitionScope
import com.materialchat.ui.navigation.SHARED_ELEMENT_FAB_TO_INPUT
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.components.AnimatedExtendedFab
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.conversations.components.ConversationItem
import com.materialchat.ui.screens.conversations.components.SwipeToDeleteBox
import com.materialchat.ui.screens.conversations.components.SwipeCornerSpec
import com.materialchat.ui.screens.search.SearchUiState
import com.materialchat.ui.screens.search.SearchViewModel
import com.materialchat.ui.screens.search.components.ChatSearchBar
import com.materialchat.ui.screens.search.components.SearchResultItem
import com.materialchat.ui.theme.CustomShapes

/**
 * Main conversations screen showing the list of all conversations.
 *
 * Features:
 * - Large collapsing top app bar with Material 3 Expressive styling
 * - Conversation list with swipe-to-delete
 * - Extended FAB for creating new conversations
 * - Empty state with illustration
 * - Settings navigation
 * - Search functionality for finding chats by title and content
 *
 * @param onNavigateToChat Callback to navigate to a chat screen
 * @param onNavigateToSettings Callback to navigate to settings
 * @param viewModel The ViewModel for this screen
 * @param searchViewModel The ViewModel for search functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Shared list state for scroll detection (M3 Expressive FAB behavior)
    val conversationListState = rememberLazyListState()
    
    // M3 Expressive: FAB expand/collapse with stable state
    // Start expanded, only update when USER actively scrolls (not data changes)
    var fabExpanded by rememberSaveable { mutableStateOf(true) }
    
    // Only update FAB state during active user scrolling
    val isScrolling = conversationListState.isScrollInProgress
    LaunchedEffect(isScrolling, conversationListState.firstVisibleItemIndex) {
        if (isScrolling) {
            // Collapse when scrolled past first item
            fabExpanded = conversationListState.firstVisibleItemIndex == 0
        }
    }
    
    // Always expand when at the very top (even after scroll stops)
    LaunchedEffect(conversationListState.firstVisibleItemIndex, conversationListState.firstVisibleItemScrollOffset) {
        if (conversationListState.firstVisibleItemIndex == 0 && 
            conversationListState.firstVisibleItemScrollOffset < 20) {
            fabExpanded = true
        }
    }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by searchViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()

    // Handle back gesture when search is active
    BackHandler(enabled = isSearchActive) {
        searchViewModel.clearSearch()
        isSearchActive = false
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversationsEvent.NavigateToChat -> {
                    onNavigateToChat(event.conversationId)
                }
                is ConversationsEvent.NavigateToSettings -> {
                    onNavigateToSettings()
                }
                is ConversationsEvent.ShowSnackbar -> {
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = if (event.actionLabel != null) {
                                SnackbarDuration.Long
                            } else {
                                SnackbarDuration.Short
                            }
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            event.onAction?.invoke()
                        }
                    }
                }
                is ConversationsEvent.ShowNoProviderError -> {
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "No provider configured",
                            actionLabel = "Settings",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onNavigateToSettings()
                        }
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
                },
                label = "topBarTransition"
            ) { searchActive ->
                if (searchActive) {
                    // Search mode - show search bar
                    Surface(
                        modifier = Modifier.statusBarsPadding(),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        ChatSearchBar(
                            query = searchQuery,
                            onQueryChange = searchViewModel::onQueryChange,
                            onClear = searchViewModel::clearSearch,
                            onClose = {
                                searchViewModel.clearSearch()
                                isSearchActive = false
                            }
                        )
                    }
                } else {
                    // Normal mode - show regular top bar
                    ConversationsTopBar(
                        scrollBehavior = scrollBehavior,
                        onSearchClick = { isSearchActive = true },
                        onSettingsClick = { viewModel.navigateToSettings() }
                    )
                }
            }
        },
        floatingActionButton = {
            val hapticsEnabled = when (val state = uiState) {
                is ConversationsUiState.Success -> state.hapticsEnabled
                is ConversationsUiState.Empty -> state.hapticsEnabled
                else -> true
            }
            NewChatFab(
                onClick = { viewModel.createNewConversation() },
                visible = uiState !is ConversationsUiState.Loading,
                expanded = fabExpanded,
                hapticsEnabled = hapticsEnabled
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    shape = CustomShapes.Snackbar,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { paddingValues ->
        // Show search results when search is active, otherwise show conversation list
        if (isSearchActive) {
            SearchContent(
                searchState = searchUiState,
                paddingValues = paddingValues,
                onResultClick = { conversationId ->
                    searchViewModel.clearSearch()
                    isSearchActive = false
                    onNavigateToChat(conversationId)
                }
            )
        } else {
            ConversationsContent(
                uiState = uiState,
                paddingValues = paddingValues,
                listState = conversationListState,
                onConversationClick = { viewModel.openConversation(it) },
                onConversationDelete = { viewModel.deleteConversation(it) },
                onRetry = { viewModel.retry() },
                onNavigateToSettings = { viewModel.navigateToSettings() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    LargeTopAppBar(
        title = {
            val isCollapsed = scrollBehavior.state.collapsedFraction > 0.55f

            AnimatedContent(
                targetState = isCollapsed,
                transitionSpec = {
                    val springSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                    val sizeTransform = SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> springSpec })
                    if (targetState) {
                        (fadeIn() + scaleIn(initialScale = 0.9f, animationSpec = springSpec)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 1.05f, animationSpec = springSpec)) using sizeTransform
                    } else {
                        (fadeIn() + scaleIn(initialScale = 0.95f, animationSpec = springSpec)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.92f, animationSpec = springSpec)) using sizeTransform
                    }
                },
                contentAlignment = Alignment.BottomStart,
                label = "titleMorph"
            ) { collapsed ->
                if (collapsed) {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            ) {
                                append("material")
                            }
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append("Chat")
                            }
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NewChatFab(
    onClick: () -> Unit,
    visible: Boolean,
    expanded: Boolean = true,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            // Apply sharedElement modifier for FAB-to-Input morph
            val fabModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        state = rememberSharedContentState(key = SHARED_ELEMENT_FAB_TO_INPUT),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(
                                dampingRatio = 0.8f,
                                stiffness = 380f
                            )
                        }
                    )
                }
            } else {
                Modifier
            }
            
            AnimatedExtendedFab(
                expanded = expanded,
                onClick = {
                    haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                    onClick()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { 
                    Text(
                        text = "New Chat",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                },
                modifier = fabModifier,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ConversationsContent(
    uiState: ConversationsUiState,
    paddingValues: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConversationClick: (String) -> Unit,
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // M3 Expressive: Rounded container wrapping main content
    // Fill entire screen and use content padding inside
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (uiState) {
                is ConversationsUiState.Loading -> {
                    LoadingContent()
                }
                is ConversationsUiState.Empty -> {
                    EmptyContent(
                        hasActiveProvider = uiState.hasActiveProvider,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                is ConversationsUiState.Success -> {
                    ConversationList(
                        conversations = uiState.conversations,
                        listState = listState,
                        onConversationClick = onConversationClick,
                        onConversationDelete = onConversationDelete,
                        hapticsEnabled = uiState.hapticsEnabled
                    )
                }
                is ConversationsUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyContent(
    hasActiveProvider: Boolean,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration
        Image(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            colorFilter = ColorFilter.tint(
                MaterialTheme.colorScheme.primaryContainer
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No conversations yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasActiveProvider) {
                "Tap the button below to start a new chat"
            } else {
                "Add an AI provider in settings to get started"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!hasActiveProvider) {
            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToSettings) {
                Text("Go to Settings")
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<ConversationUiItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConversationClick: (String) -> Unit,
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()
    val cornerRadius = 20.dp

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 88.dp // Extra padding for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = conversations,
            key = { _, item -> item.conversation.id }
        ) { index, conversationItem ->
            val isFirst = index == 0
            val isLast = index == conversations.lastIndex
            val baseCorners = when {
                isFirst && isLast -> SwipeCornerSpec(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                isFirst -> SwipeCornerSpec(cornerRadius, cornerRadius, 0.dp, 0.dp)
                isLast -> SwipeCornerSpec(0.dp, 0.dp, cornerRadius, cornerRadius)
                else -> SwipeCornerSpec(0.dp, 0.dp, 0.dp, 0.dp)
            }
            val itemShape = RoundedCornerShape(
                topStart = baseCorners.topStart,
                topEnd = baseCorners.topEnd,
                bottomStart = baseCorners.bottomStart,
                bottomEnd = baseCorners.bottomEnd
            )

            SwipeToDeleteBox(
                onDelete = { onConversationDelete(conversationItem.conversation) },
                hapticsEnabled = hapticsEnabled,
                baseCorners = baseCorners,
                activeCorners = SwipeCornerSpec(cornerRadius, cornerRadius, cornerRadius, cornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                ConversationItem(
                    conversationItem = conversationItem,
                    onClick = {
                        haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                        onConversationClick(conversationItem.conversation.id)
                    },
                    shape = itemShape,
                    showDivider = !isLast
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
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
    }
}

/**
 * Content displayed when search is active.
 * Shows search results, loading state, or empty state.
 */
@Composable
private fun SearchContent(
    searchState: SearchUiState,
    paddingValues: PaddingValues,
    onResultClick: (String) -> Unit
) {
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
        when (searchState) {
            is SearchUiState.Idle -> {
                SearchIdleContent()
            }
            is SearchUiState.Loading -> {
                SearchLoadingContent()
            }
            is SearchUiState.Results -> {
                SearchResultsList(
                    results = searchState.results,
                    onResultClick = onResultClick
                )
            }
            is SearchUiState.Empty -> {
                SearchEmptyContent(query = searchState.query)
            }
            is SearchUiState.Error -> {
                SearchErrorContent(message = searchState.message)
            }
        }
    }
}

@Composable
private fun SearchIdleContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primaryContainer
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Search your chats",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Find conversations by title or message content",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<com.materialchat.ui.screens.search.SearchResultUiItem>,
    onResultClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = results,
            key = { it.id }
        ) { item ->
            SearchResultItem(
                item = item,
                onClick = { onResultClick(item.conversationId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
            )
        }
    }
}

@Composable
private fun SearchEmptyContent(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primaryContainer
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No results found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No chats match \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchErrorContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Search failed",
            style = MaterialTheme.typography.titleMedium,
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
    }
}
