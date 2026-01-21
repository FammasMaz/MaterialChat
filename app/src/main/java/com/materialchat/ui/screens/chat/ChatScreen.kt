package com.materialchat.ui.screens.chat

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.screens.chat.components.ChatTopBar
import com.materialchat.ui.screens.chat.components.ExportBottomSheet
import com.materialchat.ui.screens.chat.components.MessageBubble
import com.materialchat.ui.screens.chat.components.MessageInput
import com.materialchat.ui.theme.CustomShapes
import kotlinx.coroutines.launch
import java.io.File

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
    val context = LocalContext.current

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
                    // Now handled via UI state
                    viewModel.showExportOptions()
                }
                is ChatEvent.HideExportOptions -> {
                    viewModel.hideExportOptions()
                }
                is ChatEvent.ShareContent -> {
                    // Create a temporary file and share it
                    try {
                        val cacheDir = File(context.cacheDir, "exports")
                        cacheDir.mkdirs()
                        val file = File(cacheDir, event.filename)
                        file.writeText(event.content)

                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        context.startActivity(
                            Intent.createChooser(shareIntent, "Export Conversation")
                        )
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to share: ${e.message}")
                    }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            when (val state = uiState) {
                is ChatUiState.Success -> {
                    ChatTopBar(
                        title = state.conversationTitle,
                        modelName = state.modelName,
                        providerName = state.providerName,
                        isStreaming = state.isStreaming,
                        availableModels = state.availableModels,
                        isLoadingModels = state.isLoadingModels,
                        onNavigateBack = { viewModel.navigateBack() },
                        onExportClick = { viewModel.showExportOptions() },
                        onModelSelected = { model -> viewModel.changeModel(model) },
                        onLoadModels = { viewModel.loadModels() },
                        scrollBehavior = scrollBehavior
                    )
                }
                else -> {
                    ChatTopBar(
                        title = "Chat",
                        modelName = "",
                        providerName = "",
                        isStreaming = false,
                        availableModels = emptyList(),
                        isLoadingModels = false,
                        onNavigateBack = { viewModel.navigateBack() },
                        onExportClick = { },
                        onModelSelected = { },
                        onLoadModels = { },
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
                    },
                    onRegenerateResponse = { viewModel.regenerateResponse() }
                )

                // Export bottom sheet
                ExportBottomSheet(
                    isVisible = state.showExportSheet,
                    isExporting = state.isExporting,
                    onDismiss = { viewModel.hideExportOptions() },
                    onExportFormat = { format -> viewModel.exportConversation(format) }
                )
            }
        }
    }
}

/**
 * Loading state content with centered progress indicator.
 */
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

/**
 * Error state content with retry and back options.
 */
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

/**
 * Main chat content with message list and input bar.
 * Wrapped in M3 Expressive rounded container.
 */
@Composable
private fun ChatContent(
    state: ChatUiState.Success,
    paddingValues: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelStreaming: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRegenerateResponse: () -> Unit
) {
    // M3 Expressive: Rounded container wrapping main content
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Message list
            MessageList(
                messages = state.messages,
                listState = listState,
                onCopyMessage = onCopyMessage,
                onRegenerateResponse = onRegenerateResponse,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Input area
            MessageInput(
                inputText = state.inputText,
                isStreaming = state.isStreaming,
                canSend = state.canSend,
                onInputChange = onInputChange,
                onSend = onSendMessage,
                onCancel = onCancelStreaming
            )
        }
    }
}

/**
 * Scrollable message list with proper styling.
 */
@Composable
private fun MessageList(
    messages: List<MessageUiItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopyMessage: (String) -> Unit,
    onRegenerateResponse: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
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
            items = messages,
            key = { it.message.id }
        ) { messageItem ->
            MessageBubble(
                messageItem = messageItem,
                onCopy = { onCopyMessage(messageItem.message.content) },
                onRegenerate = if (messageItem.isLastAssistantMessage && !messageItem.message.isStreaming) {
                    { onRegenerateResponse() }
                } else {
                    null
                }
            )
        }
    }
}
