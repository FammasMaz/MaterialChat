package com.materialchat.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import com.materialchat.ui.components.seaglass.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.isDynamicColorSupported
import com.materialchat.ui.theme.neuralExpressiveGlow

/**
 * M3 Expressive Assistant Overlay.
 *
 * This is the main overlay UI displayed when the user activates MaterialChat
 * as the system assistant (power button long-press, corner swipe).
 *
 * Features:
 * - Compact floating surface with an expandable transcript
 * - Spring-based entrance animation
 * - Dynamic color from system theme
 * - Voice waveform visualization
 * - AI response streaming with markdown
 * - Explicit keyboard, microphone, interrupt, and session controls
 *
 * @param viewModel The ViewModel for this overlay (created by the session)
 * @param onDismiss Callback to dismiss the overlay
 * @param onOpenInApp Callback to open conversation in main app
 */
@Composable
fun AssistantOverlay(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit,
    onOpenInApp: (String?) -> Unit,
    onOpenClock: (AssistantEvent.OpenClock) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val amplitudeData by viewModel.amplitudeData.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val showTrigger by viewModel.showTrigger.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val host = LocalView.current
    val density = LocalDensity.current
    var bottomObstructionPx by remember(host) { mutableIntStateOf(0) }
    // Both values are screen coordinates. A resized host already above the IME
    // needs no additional padding; a full-height host reserves only its overlap.
    DisposableEffect(host) {
        val frame = Rect()
        val location = IntArray(2)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            host.getWindowVisibleDisplayFrame(frame)
            host.getLocationOnScreen(location)
            bottomObstructionPx = (location[1] + host.height - frame.bottom)
                .coerceIn(0, host.height)
        }
        host.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            if (host.viewTreeObserver.isAlive) {
                host.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    // Follow the user's appearance preferences (mode, palette, dynamic color)
    val themeMode by viewModel.appPreferences.themeMode.collectAsStateWithLifecycle(
        initialValue = AppPreferences.ThemeMode.SYSTEM
    )
    val dynamicColorEnabled by viewModel.appPreferences.dynamicColorEnabled.collectAsStateWithLifecycle(
        initialValue = true
    )
    val themePalette by viewModel.appPreferences.themePalette.collectAsStateWithLifecycle(
        initialValue = AppPreferences.DEFAULT_THEME_PALETTE
    )

    // Check permission state (we can't request it here, only check)
    val hasMicPermission = remember(showTrigger) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // One attempt per visible invocation, after preference loading. Recomposition and
    // recognition completion must not reopen the microphone automatically.
    LaunchedEffect(showTrigger) {
        viewModel.requestAutoListen(showTrigger, hasMicPermission)
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AssistantEvent.OpenClock -> onOpenClock(event)
                is AssistantEvent.OpenInApp -> onOpenInApp(event.conversationId)
                is AssistantEvent.Dismiss -> onDismiss()
            }
        }
    }

    MaterialChatTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColorEnabled && isDynamicColorSupported(),
        themePalette = themePalette
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.dismiss()
                }
                .padding(bottom = with(density) { bottomObstructionPx.toDp() })
        ) {
            // Use showTrigger as key to force recomposition and animation restart
            key(showTrigger) {
                // Main overlay content
                AssistantOverlayContent(
                    uiState = uiState,
                    voiceState = voiceState,
                    amplitudeData = amplitudeData,
                    messages = messages,
                    hasMicPermission = hasMicPermission,
                    hasContent = messages.isNotEmpty(),
                    onTextChange = viewModel::updateTextInput,
                    onVoiceClick = {
                        if (hasMicPermission) {
                            viewModel.startVoiceInput()
                        } else {
                            // Show error - permission not granted
                            // User needs to grant permission in system settings
                        }
                    },
                    onInterrupt = {
                        viewModel.stopVoiceInput()
                        viewModel.cancelStreaming()
                        viewModel.stopSpeaking()
                    },
                    onStopVoice = viewModel::finishVoiceInput,
                    onSendClick = { viewModel.sendQuery() },
                    onOpenInApp = viewModel::openInApp,
                    onDismiss = viewModel::dismiss,
                    onNewChat = viewModel::startNewChat,
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
}

@Composable
private fun AssistantOverlayContent(
    uiState: AssistantUiState,
    voiceState: VoiceState,
    amplitudeData: com.materialchat.assistant.voice.AudioAmplitudeData,
    messages: List<AssistantMessage>,
    hasMicPermission: Boolean,
    hasContent: Boolean,
    onTextChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onInterrupt: () -> Unit,
    onStopVoice: () -> Unit,
    onSendClick: () -> Unit,
    onOpenInApp: () -> Unit,
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val busy = uiState.isLoading || uiState.isStreaming || uiState.isSpeaking ||
        voiceState is VoiceState.Speaking
    val capturing = voiceState is VoiceState.Listening || voiceState is VoiceState.Processing
    val status = when {
        voiceState is VoiceState.Error -> "Try again"
        voiceState is VoiceState.Speaking || uiState.isSpeaking -> "Speaking"
        capturing -> "Listening"
        busy || voiceState is VoiceState.Thinking || voiceState is VoiceState.Responding -> "Thinking"
        else -> "How can I help?"
    }

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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(max = 600.dp)
            .animateContentSize(animationSpec = ExpressiveMotion.Spatial.container())
            .offset { IntOffset(0, offsetY.roundToPx()) }  // Animate entrance only
            .graphicsLayer {
                scaleX = sheetScale
                scaleY = sheetScale
            }
            .alpha(alpha)
            .neuralExpressiveGlow(cornerRadius = 32.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 8.dp,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .testTag("assistant_sheet")
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Material Chat", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                ExpressiveFilledIconButton(
                    onClick = { expanded = !expanded },
                    icon = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (expanded) "Collapse assistant" else "Expand assistant",
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                ExpressiveFilledIconButton(
                    onClick = { onInterrupt(); onDismiss() },
                    icon = Icons.Default.Close,
                    contentDescription = "End assistant session",
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            if (!expanded) {
                val preview = if (capturing) uiState.textInput else messages.lastOrNull()?.content.orEmpty()
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            if (expanded) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    if (capturing) {
                        com.materialchat.assistant.ui.components.VoiceWaveform(
                            amplitudeData = amplitudeData, maxBarHeight = 48.dp)
                    } else {
                        Text(if (busy) "One moment…" else "Tap the microphone to speak",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (capturing && uiState.textInput.isNotBlank()) {
                    Text(uiState.textInput, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = onNewChat,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("New chat") }
                    if (hasContent) {
                        androidx.compose.material3.TextButton(onClick = onOpenInApp,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Open in app") }
                    }
                }
            }

            // Response card - flexible height that shrinks when keyboard appears
            AnimatedVisibility(
                visible = expanded && (messages.isNotEmpty() || uiState.isLoading),
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()) +
                        slideInVertically(
                            animationSpec = ExpressiveMotion.Spatial.default(),
                            initialOffsetY = { it / 2 }
                        ),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                        slideOutVertically(
                            animationSpec = ExpressiveMotion.Spatial.default(),
                            targetOffsetY = { it / 2 }
                        ),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                AssistantResponseCard(
                    messages = messages,
                    isLoading = uiState.isLoading,
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
                visible = voiceState is VoiceState.Error && uiState.error != (voiceState as? VoiceState.Error)?.message,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
            ) {
                (voiceState as? VoiceState.Error)?.let { errorState ->
                    ErrorMessage(message = errorState.message)
                }
            }

            if (!hasMicPermission || !uiState.isVoiceInputEnabled) {
                Text(
                    if (!hasMicPermission) "Enable microphone permission in system settings to speak."
                    else "Voice input is disabled in settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (keyboardMode) {
                AssistantInputBar(
                    textInput = uiState.textInput,
                    onTextChange = onTextChange,
                    voiceState = voiceState,
                    amplitudeData = amplitudeData,
                    onVoiceClick = {
                        keyboardMode = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                        if (hasMicPermission && uiState.isVoiceInputEnabled) {
                            onInterrupt()
                            expanded = true
                            onVoiceClick()
                        }
                    },
                    onSendClick = { onInterrupt(); expanded = true; onSendClick() },
                    onStopVoice = onStopVoice,
                    voiceEnabled = hasMicPermission && uiState.isVoiceInputEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onInterrupt()
                        if (keyboardMode) {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                        keyboardMode = !keyboardMode
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(if (keyboardMode) "Hide keyboard" else "Keyboard") }
                Spacer(Modifier.weight(1f))
                if (busy) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onInterrupt, modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Interrupt") }
                } else {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            keyboardMode = false
                            focusManager.clearFocus()
                            keyboard?.hide()
                            expanded = true
                            if (capturing) onStopVoice() else { onInterrupt(); onVoiceClick() }
                        },
                        enabled = capturing || (hasMicPermission && uiState.isVoiceInputEnabled),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(if (capturing) "Stop listening" else "Microphone") }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
