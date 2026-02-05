package com.materialchat.ui.screens.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.chat.components.ChatTopBar
import com.materialchat.ui.screens.chat.components.ExportBottomSheet
import com.materialchat.ui.screens.chat.components.MessageBubble
import com.materialchat.ui.screens.chat.components.MessageInput
import com.materialchat.ui.theme.CustomShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.compose.ui.layout.onSizeChanged

private const val TAG = "ChatScreen"

/**
 * Maximum dimension for image compression (width or height).
 */
private const val MAX_IMAGE_DIMENSION = 1024

/**
 * JPEG compression quality (0-100).
 */
private const val COMPRESSION_QUALITY = 85

/**
 * Maximum number of image attachments per message.
 */
private const val MAX_ATTACHMENTS = 4

/**
 * Main chat screen showing the conversation with the AI.
 *
 * Features:
 * - Message list with user and assistant bubbles
 * - Message input field with send button
 * - Streaming indicator during AI response
 * - Model picker in top bar
 * - Export functionality
 * - Image attachment support
 * - Branch conversation from any message
 *
 * @param onNavigateBack Callback to navigate back to conversations
 * @param onNavigateToBranch Callback to navigate to a branched conversation
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBranch: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberHapticFeedback()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Check attachment limit
            val currentState = uiState
            if (currentState is ChatUiState.Success &&
                currentState.pendingAttachments.size >= MAX_ATTACHMENTS) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Maximum $MAX_ATTACHMENTS images allowed")
                }
                return@rememberLauncherForActivityResult
            }

            coroutineScope.launch {
                val attachment = uriToAttachment(context, selectedUri)
                if (attachment != null) {
                    viewModel.addAttachment(attachment)
                } else {
                    snackbarHostState.showSnackbar("Failed to load image. Check file size (max 10MB) and format.")
                }
            }
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.NavigateBack -> {
                    onNavigateBack()
                }
                is ChatEvent.ShowSnackbar -> {
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
                is ChatEvent.MessageCopied -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Message copied")
                    }
                }
                is ChatEvent.ModelChanged -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Switched to ${event.modelName}")
                    }
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Failed to share: ${e.message}")
                        }
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
                is ChatEvent.NavigateToBranch -> {
                    // Haptic feedback for successful branch
                    val currentState = uiState
                    val hapticsEnabled = (currentState as? ChatUiState.Success)?.hapticsEnabled ?: true
                    if (hapticsEnabled) {
                        haptics.perform(HapticPattern.CONFIRM, true)
                    }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Conversation branched")
                    }
                    onNavigateToBranch(event.conversationId)
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Draw edge-to-edge
        topBar = {
            when (val state = uiState) {
                is ChatUiState.Success -> {
                    ChatTopBar(
                        title = state.conversationTitle,
                        icon = state.conversationIcon,
                        modelName = state.modelName,
                        providerName = state.providerName,
                        isStreaming = state.isStreaming,
                        availableModels = state.availableModels,
                        isLoadingModels = state.isLoadingModels,
                        beautifulModelNamesEnabled = state.beautifulModelNamesEnabled,
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
                    onRegenerateResponse = { viewModel.regenerateResponse() },
                    onBranchFromMessage = { messageId -> viewModel.branchFromMessage(messageId) },
                    onAttachImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    reasoningEffort = state.reasoningEffort,
                    onReasoningEffortChange = { viewModel.updateReasoningEffort(it) }
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
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
    onRegenerateResponse: () -> Unit,
    onBranchFromMessage: (String) -> Unit,
    onAttachImage: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    reasoningEffort: ReasoningEffort,
    onReasoningEffortChange: (ReasoningEffort) -> Unit
) {
    val haptics = rememberHapticFeedback()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imePadding = with(density) { (imeBottom - navBottom).coerceAtLeast(0).toDp() }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val bottomThresholdPx = with(density) { 24.dp.toPx() }

    // Fix 1: Visual buffer during streaming (8dp)
    val scrollBufferPx = with(density) { 8.dp.toPx() }
    // Fix 2: Larger buffer for action buttons when streaming ends (~56dp)
    val actionButtonHeightPx = with(density) { 56.dp.toPx() }

    // Fix 3: NestedScrollConnection to detect upward scroll gestures immediately
    val autoScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // User scrolling UP (positive y in scroll coordinates means upward gesture)
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    autoScrollEnabled = false
                }
                return Offset.Zero  // Don't consume scroll, just observe
            }
        }
    }
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) {
                true
            } else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                val lastIndex = totalItems - 1
                if (lastVisible.index < lastIndex) {
                    return@derivedStateOf false
                }
                val itemEnd = lastVisible.offset + lastVisible.size
                val viewportEnd = layoutInfo.viewportEndOffset
                itemEnd <= viewportEnd + bottomThresholdPx
            }
        }
    }
    // Only re-enable auto-scroll when user returns to bottom (after scroll stops)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .collect { (isScrolling, atBottom) ->
                if (!isScrolling && atBottom) {
                    autoScrollEnabled = true
                }
            }
    }

    // Track content lengths for haptic feedback during streaming
    var lastContentLength by remember { mutableIntStateOf(0) }
    var lastThinkingLength by remember { mutableIntStateOf(0) }

    // Trigger haptic feedback when streaming content changes
    LaunchedEffect(state.streamingState) {
        val streamingState = state.streamingState
        if (streamingState is StreamingState.Streaming) {
            val currentContentLength = streamingState.content.length
            val currentThinkingLength = streamingState.thinkingContent?.length ?: 0

            // Check if thinking content grew (lighter tap)
            if (currentThinkingLength > lastThinkingLength && lastThinkingLength > 0) {
                haptics.perform(HapticPattern.THINKING_TICK, state.hapticsEnabled)
            }
            // Check if regular content grew (slightly louder tap)
            else if (currentContentLength > lastContentLength && lastContentLength > 0) {
                haptics.perform(HapticPattern.CONTENT_TICK, state.hapticsEnabled)
            }

            lastContentLength = currentContentLength
            lastThinkingLength = currentThinkingLength
        } else if (streamingState is StreamingState.Idle || streamingState is StreamingState.Starting) {
            // Reset tracking when not streaming
            lastContentLength = 0
            lastThinkingLength = 0
        }
    }

    val lastMessage = state.messages.lastOrNull()?.message
    val lastContentHash = lastMessage?.content?.hashCode() ?: 0
    val lastThinkingHash = lastMessage?.thinkingContent?.hashCode() ?: 0
    val lastAttachmentCount = lastMessage?.attachments?.size ?: 0
    // Fix 2: Track streaming state to scroll when it ends (to reveal action buttons)
    val isLastMessageStreaming = lastMessage?.isStreaming ?: false

    LaunchedEffect(
        lastContentHash,
        lastThinkingHash,
        lastAttachmentCount,
        state.messages.size,
        inputHeightPx,
        isAtBottom,
        autoScrollEnabled,
        isLastMessageStreaming  // NEW KEY: triggers scroll when streaming ends
    ) {
        if (autoScrollEnabled && state.messages.isNotEmpty()) {
            val lastIndex = state.messages.lastIndex
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible == null || lastVisible.index < lastIndex) {
                listState.scrollToItem(lastIndex)
            } else {
                // Fix 1 & 2: Use appropriate buffer based on streaming state
                // - During streaming: small buffer (8dp) to prevent bottom cutoff
                // - After streaming ends: larger buffer (~56dp) to reveal action buttons
                val buffer = if (isLastMessageStreaming) scrollBufferPx else actionButtonHeightPx
                val overflow = (lastVisible.offset + lastVisible.size) - layoutInfo.viewportEndOffset + buffer
                if (overflow > 0) {
                    listState.scrollBy(overflow.toFloat())
                }
            }
        }
    }

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
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        // Fix 3: Apply nestedScroll to detect upward scroll gestures immediately
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = imePadding)
                .nestedScroll(autoScrollConnection)
        ) {
            // Message list
            MessageList(
                messages = state.messages,
                listState = listState,
                onCopyMessage = onCopyMessage,
                onRegenerateResponse = onRegenerateResponse,
                onBranchFromMessage = onBranchFromMessage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Input area
            MessageInput(
                inputText = state.inputText,
                isStreaming = state.isStreaming,
                canSend = state.canSend,
                pendingAttachments = state.pendingAttachments,
                onInputChange = onInputChange,
                onSend = onSendMessage,
                onCancel = onCancelStreaming,
                onAttachImage = onAttachImage,
                onRemoveAttachment = onRemoveAttachment,
                reasoningEffort = reasoningEffort,
                onReasoningEffortChange = onReasoningEffortChange,
                hapticsEnabled = state.hapticsEnabled,
                shouldAutoFocus = state.messages.isEmpty(),
                modifier = Modifier.onSizeChanged { inputHeightPx = it.height }
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
    onBranchFromMessage: (String) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = messages,
            key = { _, item -> item.message.id }
        ) { _, messageItem ->
            val topSpacing = when (messageItem.groupPosition) {
                MessageGroupPosition.Middle,
                MessageGroupPosition.Last -> 2.dp
                MessageGroupPosition.First,
                MessageGroupPosition.Single -> 10.dp
            }

            MessageBubble(
                messageItem = messageItem,
                onCopy = { onCopyMessage(messageItem.message.content) },
                onRegenerate = if (messageItem.isLastAssistantMessage && !messageItem.message.isStreaming) {
                    { onRegenerateResponse() }
                } else {
                    null
                },
                onBranch = if (!messageItem.message.isStreaming) {
                    { onBranchFromMessage(messageItem.message.id) }
                } else {
                    null
                },
                modifier = Modifier.padding(top = topSpacing)
            )
        }
    }
}

/**
 * Converts a content URI to an Attachment object with base64-encoded image data.
 * Includes image compression to reduce memory and network usage.
 *
 * @param context The Android context for accessing content resolver
 * @param uri The content URI of the selected image
 * @return An Attachment object, or null if the conversion fails
 */
private suspend fun uriToAttachment(
    context: android.content.Context,
    uri: Uri
): Attachment? = withContext(Dispatchers.IO) {
    try {
        val contentResolver = context.contentResolver

        // Get MIME type
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

        // Check if it's a supported image type
        if (!Attachment.isSupportedImageType(mimeType)) {
            Log.w(TAG, "Unsupported image type: $mimeType")
            return@withContext null
        }

        // Read the image data
        val inputStream = contentResolver.openInputStream(uri) ?: run {
            Log.w(TAG, "Failed to open input stream for URI: $uri")
            return@withContext null
        }

        val originalBytes = inputStream.use { it.readBytes() }

        // Check original file size (max 10MB)
        if (originalBytes.size > Attachment.MAX_FILE_SIZE_BYTES) {
            Log.w(TAG, "Image too large: ${originalBytes.size} bytes (max ${Attachment.MAX_FILE_SIZE_BYTES})")
            return@withContext null
        }

        // Compress the image
        val compressedBytes = compressImage(originalBytes, mimeType)
        Log.d(TAG, "Image compressed: ${originalBytes.size} -> ${compressedBytes.size} bytes")

        // Encode to base64
        val base64Data = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        Attachment(
            uri = uri.toString(),
            mimeType = if (mimeType == "image/png" || mimeType == "image/gif") mimeType else "image/jpeg",
            base64Data = base64Data
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to convert URI to attachment", e)
        null
    }
}

/**
 * Compresses an image to reduce file size while maintaining reasonable quality.
 * Resizes large images and compresses using JPEG for better size reduction.
 */
private fun compressImage(imageBytes: ByteArray, mimeType: String): ByteArray {
    // Decode the bitmap with options to get dimensions first
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

    // Calculate sample size for scaling down large images
    val width = options.outWidth
    val height = options.outHeight
    var sampleSize = 1

    if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
        val halfWidth = width / 2
        val halfHeight = height / 2
        while ((halfWidth / sampleSize) >= MAX_IMAGE_DIMENSION ||
               (halfHeight / sampleSize) >= MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
    }

    // Decode the bitmap with the calculated sample size
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
        ?: return imageBytes // Return original if decode fails

    // Further scale down if still too large
    val scaledBitmap = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
        val scale = minOf(
            MAX_IMAGE_DIMENSION.toFloat() / bitmap.width,
            MAX_IMAGE_DIMENSION.toFloat() / bitmap.height
        )
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    } else {
        bitmap
    }

    // Compress to JPEG (or PNG for transparency)
    val outputStream = ByteArrayOutputStream()
    val format = if (mimeType == "image/png") {
        Bitmap.CompressFormat.PNG
    } else {
        Bitmap.CompressFormat.JPEG
    }
    scaledBitmap.compress(format, COMPRESSION_QUALITY, outputStream)
    scaledBitmap.recycle()

    return outputStream.toByteArray()
}
