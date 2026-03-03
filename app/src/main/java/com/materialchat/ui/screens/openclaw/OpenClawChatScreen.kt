package com.materialchat.ui.screens.openclaw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.openclaw.components.OpenClawChatInput
import com.materialchat.ui.screens.openclaw.components.OpenClawMessageBubble
import com.materialchat.ui.theme.CustomShapes

/**
 * OpenClaw Chat screen composable.
 *
 * Provides a full chat interface for communicating with an OpenClaw agent.
 * Supports streaming responses, tool call display, and agent status indicators.
 *
 * @param onNavigateBack Callback to navigate back
 * @param modifier Modifier for the screen
 * @param viewModel The ViewModel managing chat state
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenClawChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenClawChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val haptics = rememberHapticFeedback()

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OpenClawChatEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive or during streaming
    val activeState = uiState as? OpenClawChatUiState.Active
    val messageCount = activeState?.messages?.size ?: 0
    val isStreaming = activeState?.isStreaming == true

    // Animate scroll when a new message is added
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    // Snap (no animation) to bottom during streaming content updates
    LaunchedEffect(isStreaming, activeState?.messages?.lastOrNull()?.content?.length) {
        if (isStreaming && messageCount > 0) {
            listState.scrollToItem(messageCount - 1)
        }
    }

    // Track content length for haptic feedback during streaming
    var lastContentLength by remember { mutableIntStateOf(0) }
    LaunchedEffect(isStreaming, activeState?.messages?.lastOrNull()?.content?.length) {
        val hapticsOn = activeState?.hapticsEnabled ?: true
        if (isStreaming) {
            val currentLength = activeState?.messages?.lastOrNull()?.content?.length ?: 0
            if (currentLength > lastContentLength && lastContentLength > 0) {
                haptics.perform(HapticPattern.CONTENT_TICK, hapticsOn)
            }
            lastContentLength = currentLength
        } else {
            lastContentLength = 0
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "OpenClaw Agent",
                                style = MaterialTheme.typography.titleLarge
                            )
                            // Agent status subtitle
                            if (activeState != null) {
                                val statusText = when {
                                    activeState.isStreaming -> activeState.agentStatus.displayName
                                    activeState.connectionState is GatewayConnectionState.Connected -> "Connected"
                                    activeState.connectionState is GatewayConnectionState.Connecting -> "Connecting..."
                                    else -> "Disconnected"
                                }
                                val statusColor = when {
                                    activeState.isStreaming -> MaterialTheme.colorScheme.tertiary
                                    activeState.connectionState is GatewayConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.perform(HapticPattern.CLICK, activeState?.hapticsEnabled ?: true)
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (activeState != null) {
                        IconButton(onClick = {
                            haptics.perform(HapticPattern.CLICK, activeState?.hapticsEnabled ?: true)
                            viewModel.startNewSession()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.AddComment,
                                contentDescription = "New Chat"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
        },
        modifier = modifier
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            when (val state = uiState) {
                is OpenClawChatUiState.Loading -> {
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

                is OpenClawChatUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is OpenClawChatUiState.Active -> {
                    ChatContent(
                        state = state,
                        onInputChange = viewModel::updateInput,
                        onSend = viewModel::sendMessage,
                        onAbort = viewModel::abortStream,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Chat content with message list and input bar.
 */
@Composable
private fun ChatContent(
    state: OpenClawChatUiState.Active,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.imePadding()
    ) {
        // Message list
        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Hub,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Start a conversation with your agent",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.messages,
                    key = { "${it.role}_${it.timestamp}_${it.runId}" }
                ) { message ->
                    OpenClawMessageBubble(
                        message = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = spring(
                                    dampingRatio = 1.0f,
                                    stiffness = 300f
                                ),
                                fadeOutSpec = spring(
                                    dampingRatio = 1.0f,
                                    stiffness = 300f
                                )
                            )
                    )
                }

                // Agent status indicator during streaming
                if (state.isStreaming && state.agentStatus != AgentStatus.IDLE) {
                    item(key = "agent_status") {
                        AgentStatusIndicator(
                            status = state.agentStatus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        )
                    }
                }
            }
        }

        // Chat input bar
        OpenClawChatInput(
            inputText = state.currentInput,
            isStreaming = state.isStreaming,
            canSend = state.canSend,
            onInputChange = onInputChange,
            onSend = onSend,
            onAbort = onAbort,
            hapticsEnabled = state.hapticsEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Agent status indicator shown during streaming.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AgentStatusIndicator(
    status: AgentStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = status != AgentStatus.IDLE,
        enter = fadeIn(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)
        ) + slideInVertically(
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut()
    ) {
        Row(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LoadingIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
