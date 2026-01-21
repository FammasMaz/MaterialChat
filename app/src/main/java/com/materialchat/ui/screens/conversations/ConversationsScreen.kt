package com.materialchat.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.conversations.components.ConversationItem
import com.materialchat.ui.screens.conversations.components.SwipeToDeleteBox
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
 *
 * @param onNavigateToChat Callback to navigate to a chat screen
 * @param onNavigateToSettings Callback to navigate to settings
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                is ConversationsEvent.ShowNoProviderError -> {
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            ConversationsTopBar(
                scrollBehavior = scrollBehavior,
                onSettingsClick = { viewModel.navigateToSettings() }
            )
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
        ConversationsContent(
            uiState = uiState,
            paddingValues = paddingValues,
            onConversationClick = { viewModel.openConversation(it) },
            onConversationDelete = { viewModel.deleteConversation(it) },
            onRetry = { viewModel.retry() },
            onNavigateToSettings = { viewModel.navigateToSettings() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSettingsClick: () -> Unit
) {
    LargeTopAppBar(
        title = {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineLarge
            )
        },
        actions = {
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

@Composable
private fun NewChatFab(
    onClick: () -> Unit,
    visible: Boolean,
    hapticsEnabled: Boolean = true
) {
    val listState = rememberLazyListState()
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }
    val haptics = rememberHapticFeedback()

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
                .padding(12.dp) // Padding for shadow
        ) {
            ExtendedFloatingActionButton(
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
                text = { Text("New Chat") },
                shape = CustomShapes.ExtendedFab,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp,
                    focusedElevation = 2.dp,
                    hoveredElevation = 3.dp
                ),
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            )
        }
    }
}

@Composable
private fun ConversationsContent(
    uiState: ConversationsUiState,
    paddingValues: PaddingValues,
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
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        tonalElevation = 1.dp
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
    onConversationClick: (String) -> Unit,
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    hapticsEnabled: Boolean = true
) {
    val listState = rememberLazyListState()
    val haptics = rememberHapticFeedback()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 88.dp // Extra padding for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = conversations,
            key = { it.conversation.id }
        ) { conversationItem ->
            SwipeToDeleteBox(
                onDelete = { onConversationDelete(conversationItem.conversation) },
                hapticsEnabled = hapticsEnabled,
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
                    }
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
