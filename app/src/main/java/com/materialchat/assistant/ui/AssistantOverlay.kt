package com.materialchat.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.assistant.ui.components.AssistantInputBar
import com.materialchat.assistant.ui.components.AssistantResponseCard
import com.materialchat.assistant.voice.VoiceState
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatTheme
import kotlin.math.roundToInt

/**
 * M3 Expressive Assistant Overlay.
 *
 * This is the main overlay UI displayed when the user activates MaterialChat
 * as the system assistant (power button long-press, corner swipe).
 *
 * Features:
 * - Bottom sheet style with 32dp top corners
 * - Spring-based entrance animation
 * - Dynamic color from system theme
 * - Voice waveform visualization
 * - AI response streaming with markdown
 * - Swipe-up gesture on drag handle to open in app
 *
 * @param viewModel The ViewModel for this overlay (created by the session)
 * @param onDismiss Callback to dismiss the overlay
 * @param onOpenInApp Callback to open conversation in main app
 */
@Composable
fun AssistantOverlay(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit,
    onOpenInApp: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val amplitudeData by viewModel.amplitudeData.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Check permission state (we can't request it here, only check)
    val hasMicPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AssistantEvent.OpenInApp -> onOpenInApp(event.conversationId)
                is AssistantEvent.Dismiss -> onDismiss()
            }
        }
    }

    MaterialChatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.dismiss()
                }
        ) {
            // Main overlay content
            AssistantOverlayContent(
                uiState = uiState,
                voiceState = voiceState,
                amplitudeData = amplitudeData,
                hasMicPermission = hasMicPermission,
                hasContent = uiState.userQuery.isNotEmpty() || uiState.response.isNotEmpty(),
                onTextChange = viewModel::updateTextInput,
                onVoiceClick = {
                    if (hasMicPermission) {
                        viewModel.startVoiceInput()
                    } else {
                        // Show error - permission not granted
                        // User needs to grant permission in system settings
                    }
                },
                onStopVoice = viewModel::stopVoiceInput,
                onSendClick = { viewModel.sendQuery() },
                onOpenInApp = viewModel::openInApp,
                onDismiss = viewModel::dismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks on the sheet */ }
            )
        }
    }
}

@Composable
private fun AssistantOverlayContent(
    uiState: AssistantUiState,
    voiceState: VoiceState,
    amplitudeData: com.materialchat.assistant.voice.AudioAmplitudeData,
    hasMicPermission: Boolean,
    hasContent: Boolean,
    onTextChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onStopVoice: () -> Unit,
    onSendClick: () -> Unit,
    onOpenInApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Animate sheet entrance with bounce (M3 Expressive spatial spring)
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 400.dp,
        animationSpec = ExpressiveMotion.Spatial.container(),
        label = "sheetOffset"
    )

    // Sheet scale for depth effect (M3 Expressive playful spring)
    val sheetScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.95f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "sheetScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "sheetAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToPx()) }
            .graphicsLayer {
                scaleX = sheetScale
                scaleY = sheetScale
            }
            .alpha(alpha),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp, // M3 Expressive: Higher elevation for depth
        shadowElevation = 24.dp // Strong shadow for depth
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle bar and close button with swipe gesture
            HeaderSection(
                onDismiss = onDismiss,
                onOpenInApp = onOpenInApp,
                hasContent = hasContent
            )

            // Response card (only shown when there's a query/response)
            AnimatedVisibility(
                visible = uiState.userQuery.isNotEmpty() || uiState.response.isNotEmpty() || uiState.isLoading,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()) +
                        slideInVertically(
                            animationSpec = ExpressiveMotion.Spatial.default(),
                            initialOffsetY = { it / 2 }
                        ),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                        slideOutVertically(
                            animationSpec = ExpressiveMotion.Spatial.default(),
                            targetOffsetY = { it / 2 }
                        )
            ) {
                AssistantResponseCard(
                    userQuery = uiState.userQuery,
                    response = uiState.response,
                    isLoading = uiState.isLoading,
                    isStreaming = uiState.isStreaming,
                    onOpenInApp = onOpenInApp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Error message
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
            ) {
                uiState.error?.let { error ->
                    ErrorMessage(message = error)
                }
            }

            // Voice state indicator
            AnimatedVisibility(
                visible = voiceState is VoiceState.Error,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
            ) {
                (voiceState as? VoiceState.Error)?.let { errorState ->
                    ErrorMessage(message = errorState.message)
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Input bar
            AssistantInputBar(
                textInput = uiState.textInput,
                onTextChange = onTextChange,
                voiceState = voiceState,
                amplitudeData = amplitudeData,
                onVoiceClick = onVoiceClick,
                onSendClick = onSendClick,
                onStopVoice = onStopVoice,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Header section with drag handle (swipe-up gesture) and close button.
 * M3 Expressive: Drag handle expands on drag to provide visual feedback.
 */
@Composable
private fun HeaderSection(
    onDismiss: () -> Unit,
    onOpenInApp: () -> Unit,
    hasContent: Boolean
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        dragOffset += delta
    }

    // Animate handle width based on drag progress (M3 Expressive visual feedback)
    val handleWidth by animateFloatAsState(
        targetValue = when {
            dragOffset < -80f -> 0.2f
            dragOffset < -40f -> 0.15f
            else -> 0.1f
        },
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "handleWidth"
    )

    // Animate handle color on drag
    val handleAlpha by animateFloatAsState(
        targetValue = if (dragOffset < -40f) 0.8f else 0.4f,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "handleAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    isDragging = true
                    dragOffset = 0f
                },
                onDragStopped = {
                    // If dragged up significantly and there's content, open in app
                    if (dragOffset < -100f && hasContent) {
                        onOpenInApp()
                    }
                    isDragging = false
                    dragOffset = 0f
                }
            )
    ) {
        // Handle bar with visual feedback
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth(handleWidth)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = handleAlpha),
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            // Visual hint text when dragging up
            AnimatedVisibility(
                visible = dragOffset < -40f && hasContent,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()) +
                        slideInVertically(
                            animationSpec = ExpressiveMotion.Spatial.playful(),
                            initialOffsetY = { it }
                        ),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
            ) {
                Text(
                    text = "Open in App",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Title
        Text(
            text = "MaterialChat",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 12.dp)
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
