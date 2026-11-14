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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import com.materialchat.ui.theme.particles.neuralParticleField

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
    val density = LocalDensity.current
    // The session window never resizes for the IME, so the keyboard arrives as an inset of the
    // full-height host. Reserving the IME's own height keeps the card directly above the keyboard,
    // and the navigation bar is the only obstruction left once it closes. The IME covers the
    // navigation bar, so the two are alternatives rather than additive.
    // Compose's inset values only trigger recomposition; the geometry below uses the window's own
    // insets and screen positions so a host the framework already shortened is not padded twice.
    val composeImePx = WindowInsets.ime.getBottom(density)
    val composeNavPx = WindowInsets.navigationBars.getBottom(density)
    var layoutPass by remember { mutableIntStateOf(0) }
    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener { layoutPass++ }
        hostView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            if (hostView.viewTreeObserver.isAlive) hostView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    val insetObstructionPx = remember(composeImePx, composeNavPx, layoutPass, hostView) {
        val rootInsets = if (Build.VERSION.SDK_INT >= 30) hostView.rootWindowInsets else null
        if (rootInsets == null || Build.VERSION.SDK_INT < 30) {
            AssistantKeyboardSpacing.bottomPaddingPx(composeImePx, composeNavPx)
        } else {
            val root = hostView.rootView
            val hostLoc = IntArray(2).also(hostView::getLocationOnScreen)
            val rootLoc = IntArray(2).also(root::getLocationOnScreen)
            val ime = rootInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            val nav = rootInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            AssistantKeyboardSpacing.remainingObstructionPx(
                hostBottomOnScreen = hostLoc[1] + hostView.height,
                rootBottomOnScreen = rootLoc[1] + root.height,
                rootImeBottomPx = ime,
                rootNavigationBarBottomPx = nav
            ).also { padding ->
                if (ime > 0) android.util.Log.i("MaterialChatAssistant",
                    "keyboard ime=$ime nav=$nav hostBottom=${hostLoc[1] + hostView.height} " +
                        "rootBottom=${rootLoc[1] + root.height} composeIme=$composeImePx padding=$padding")
            }
        }
    }
    // API 26-29 deliver no IME inset to this ADJUST_RESIZE session window, so keep the legacy
    // measurement there (proven by the hosted API 26 gate): the host's overlap with the visible
    // display frame. API 30+ uses the IME inset above, because on modern devices the legacy frame
    // over-reported the keyboard and pushed the card to the top of the screen.
    val host = LocalView.current
    var legacyObstructionPx by remember(host) { mutableIntStateOf(0) }
    if (!AssistantKeyboardSpacing.usesImeInsets(Build.VERSION.SDK_INT)) {
        DisposableEffect(host) {
            val frame = AndroidRect()
            val location = IntArray(2)
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                host.getWindowVisibleDisplayFrame(frame)
                host.getLocationOnScreen(location)
                legacyObstructionPx = (location[1] + host.height - frame.bottom)
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
    }
    val bottomObstructionPx =
        if (AssistantKeyboardSpacing.usesImeInsets(Build.VERSION.SDK_INT)) insetObstructionPx
        else legacyObstructionPx

    // The session has its own composition; propagate the same saved appearance as the activity.
    val themeMode by viewModel.appPreferences.themeMode.collectAsStateWithLifecycle(
        initialValue = AppPreferences.ThemeMode.SYSTEM
    )
    val dynamicColorEnabled by viewModel.appPreferences.dynamicColorEnabled.collectAsStateWithLifecycle(
        initialValue = true
    )
    val themePalette by viewModel.appPreferences.themePalette.collectAsStateWithLifecycle(
        initialValue = AppPreferences.DEFAULT_THEME_PALETTE
    )
    val pitchBlackDark by viewModel.appPreferences.pitchBlackDark.collectAsStateWithLifecycle(
        initialValue = AppPreferences.DEFAULT_PITCH_BLACK_DARK
    )
    val neuralExpressive by viewModel.appPreferences.neuralExpressive.collectAsStateWithLifecycle(
        initialValue = AppPreferences.DEFAULT_NEURAL_EXPRESSIVE
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

    // The card reports its bounds in root coordinates; the scrim draws the particle field around
    // it in its own coordinate space, so the card rect is translated by the scrim's root origin.
    var cardBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var scrimOriginInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val decorationRect = cardBoundsInRoot?.translate(-scrimOriginInRoot.x, -scrimOriginInRoot.y)

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
        themePalette = themePalette,
        pitchBlack = pitchBlackDark,
        neuralExpressive = neuralExpressive
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
                .onGloballyPositioned { scrimOriginInRoot = it.boundsInRoot().topLeft }
                // Below API 28 a node's drawing is clipped to its own bounds, so a card-sized
                // node can never draw an outward particle band there. The full-window scrim hosts
                // the field and targets the card rect (reported in this node's coordinate space),
                // which also keeps the whole outer band inside the drawing node.
                .neuralParticleField(
                    capsuleRect = decorationRect,
                    cornerRadius = 32.dp,
                    color = Color.White,
                    enabled = neuralExpressive,
                    // The card reports its rect; until it does, there is nothing to decorate.
                    capsuleInset = null,
                    // Particles show from invocation until the answer is done, then fade out
                    // (800 ms) and stop requesting frames. Any new turn brings them back.
                    active = (voiceState !is VoiceState.Complete && voiceState !is VoiceState.Error) ||
                        uiState.isLoading || uiState.isStreaming || uiState.isSpeaking
                )
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
                    onCardBoundsChange = { cardBoundsInRoot = it },
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
internal fun AssistantOverlayContent(
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
    onCardBoundsChange: (Rect) -> Unit = {},
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

    // Keep the exact card bounds/padding. The parent draws outside the opaque Surface, without
    // reserving additional space above the IME. ModulateAlpha avoids an offscreen alpha texture
    // clipping the outward halo while the sheet enters; the card's own shape still clips content.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = AssistantKeyboardSpacing.GAP_DP.dp
            )
            .heightIn(max = 600.dp)
            .offset { IntOffset(0, offsetY.roundToPx()) }
            .graphicsLayer {
                scaleX = sheetScale
                scaleY = sheetScale
                this.alpha = alpha
                clip = false
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .onGloballyPositioned { onCardBoundsChange(it.boundsInRoot()) }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = ExpressiveMotion.Spatial.container()),
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
