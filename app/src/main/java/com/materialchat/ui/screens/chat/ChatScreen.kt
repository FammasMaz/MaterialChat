package com.materialchat.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.components.ChatTopBar
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.MessageBubbleShapes
import kotlinx.coroutines.launch

/**
 * Main chat screen showing the conversation with the AI.
 *
 * Features:
 * - Message list with user and assistant bubbles
 * - Message input field with send button
 * - Streaming indicator during AI response
 * - Model picker in top bar
 * - Export functionality
 *
 * @param onNavigateBack Callback to navigate back to conversations
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.NavigateBack -> {
                    onNavigateBack()
                }
                is ChatEvent.ShowSnackbar -> {
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
                is ChatEvent.MessageCopied -> {
                    snackbarHostState.showSnackbar("Message copied")
                }
                is ChatEvent.ModelChanged -> {
                    snackbarHostState.showSnackbar("Switched to ${event.modelName}")
                }
                is ChatEvent.ShowExportOptions -> {
                    // Export functionality will be implemented in ui-chat-06
                    snackbarHostState.showSnackbar("Export coming soon")
                }
                is ChatEvent.ScrollToBottom -> {
                    val currentState = uiState
                    if (currentState is ChatUiState.Success && currentState.messages.isNotEmpty()) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentState.messages.size - 1)
                        }
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        topBar = {
            when (val state = uiState) {
                is ChatUiState.Success -> {
                    ChatTopBar(
                        title = state.conversationTitle,
                        modelName = state.modelName,
                        providerName = state.providerName,
                        isStreaming = state.isStreaming,
                        onNavigateBack = { viewModel.navigateBack() },
                        onExportClick = { viewModel.showExportOptions() },
                        scrollBehavior = scrollBehavior
                    )
                }
                else -> {
                    ChatTopBar(
                        title = "Chat",
                        modelName = "",
                        providerName = "",
                        isStreaming = false,
                        onNavigateBack = { viewModel.navigateBack() },
                        onExportClick = { },
                        scrollBehavior = scrollBehavior
                    )
                }
            }
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
        when (val state = uiState) {
            is ChatUiState.Loading -> {
                LoadingContent(paddingValues = paddingValues)
            }
            is ChatUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    paddingValues = paddingValues,
                    onRetry = { viewModel.retry() },
                    onNavigateBack = onNavigateBack
                )
            }
            is ChatUiState.Success -> {
                ChatContent(
                    state = state,
                    paddingValues = paddingValues,
                    listState = listState,
                    onInputChange = { viewModel.updateInputText(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    onCancelStreaming = { viewModel.cancelStreaming() },
                    onCopyMessage = { content ->
                        clipboardManager.setText(AnnotatedString(content))
                        viewModel.copyMessage(content)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    paddingValues: PaddingValues,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onNavigateBack) {
                Text("Go Back")
            }
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ChatContent(
    state: ChatUiState.Success,
    paddingValues: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelStreaming: () -> Unit,
    onCopyMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Message list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
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
                key = { it.message.id }
            ) { messageItem ->
                MessageBubble(
                    messageItem = messageItem,
                    onCopy = { onCopyMessage(messageItem.message.content) }
                )
            }
        }

        // Input area
        MessageInputBar(
            inputText = state.inputText,
            isStreaming = state.isStreaming,
            canSend = state.canSend,
            onInputChange = onInputChange,
            onSend = onSendMessage,
            onCancel = onCancelStreaming
        )
    }
}

@Composable
private fun MessageBubble(
    messageItem: MessageUiItem,
    onCopy: () -> Unit
) {
    val message = messageItem.message
    val isUser = message.role == MessageRole.USER
    val isAssistant = message.role == MessageRole.ASSISTANT
    val isSystem = message.role == MessageRole.SYSTEM

    val bubbleShape = when {
        isUser -> MessageBubbleShapes.UserBubble
        isAssistant -> MessageBubbleShapes.AssistantBubble
        else -> MessageBubbleShapes.SystemBubble
    }

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isAssistant -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isAssistant -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    val alignment = when {
        isUser -> Alignment.CenterEnd
        isAssistant -> Alignment.CenterStart
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content.ifEmpty {
                        if (message.isStreaming) "..." else ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )

                // Streaming indicator
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StreamingIndicator()
                }
            }
        }
    }
}

@Composable
private fun StreamingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    inputText: String,
    isStreaming: Boolean,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Type a message...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                enabled = !isStreaming,
                maxLines = 4,
                shape = CustomShapes.MessageInputContainer,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) {
                            onSend()
                        }
                    }
                )
            )

            AnimatedVisibility(
                visible = isStreaming,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onCancel,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generating"
                    )
                }
            }

            AnimatedVisibility(
                visible = !isStreaming,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (canSend) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }
}
