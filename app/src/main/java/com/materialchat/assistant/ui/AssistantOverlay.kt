package com.materialchat.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
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
import com.materialchat.assistant.ui.components.AssistantResponseCard
import com.materialchat.assistant.voice.VoiceState
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.isDynamicColorSupported
import com.materialchat.ui.theme.particles.neuralParticleField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.materialchat.assistant.ui.components.VoiceWaveform
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.components.TypingIndicator

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
                    cornerRadius = 38.dp,
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

/**
 * Assistant surface design: a Gemini-style floating pill.
 *
 * - One pill (64dp, fully rounded, surfaceContainerHigh, elevated) sits at the bottom. Left to
 *   right: a state glyph (compact waveform while capturing, animated dots while thinking, a sound
 *   icon while speaking, a sparkle otherwise), the text area (live partial transcript while
 *   listening, "Ask anything" when idle, the text field itself in keyboard mode), then the
 *   keyboard toggle ("Keyboard" / "Hide keyboard") and the primary button (Mic starts listening;
 *   Stop while capturing, thinking or speaking; Send when keyboard mode has text).
 * - When messages exist or a reply is loading, a rounded response surface appears 8dp above the
 *   pill (at most half the screen, scrollable) showing the latest exchange, a light action row
 *   (open in app, new chat, copy) and the "End assistant session" close button in its top-right.
 * - Without a response surface the close button sits at the trailing end of the pill.
 * - Errors and the microphone hint are one short line: inside the response surface, or a slim
 *   surface above the pill when there is none. No header, status title, expand/collapse or
 *   text-button rows.
 * - Gates: resource-id `assistant_sheet` is the column wrapping the response surface and the pill,
 *   so its bottom edge is the pill's bottom edge, 8dp above the IME.
 */
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
    var keyboardMode by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val voiceEnabled = hasMicPermission && uiState.isVoiceInputEnabled
    val capturing = voiceState is VoiceState.Listening || voiceState is VoiceState.Processing
    val speaking = voiceState is VoiceState.Speaking || uiState.isSpeaking
    val busy = uiState.isLoading || uiState.isStreaming || speaking ||
        voiceState is VoiceState.Thinking || voiceState is VoiceState.Responding
    val thinking = busy && !speaking && !capturing
    val hasResponse = messages.isNotEmpty() || uiState.isLoading
    val isError = uiState.error != null || voiceState is VoiceState.Error
    val notice = uiState.error
        ?: (voiceState as? VoiceState.Error)?.message
        ?: when {
            !hasMicPermission -> "Enable microphone permission in system settings to speak."
            !uiState.isVoiceInputEnabled -> "Voice input is disabled in settings."
            else -> null
        }
    val partialTranscript = uiState.textInput.ifBlank {
        (voiceState as? VoiceState.Processing)?.partialText.orEmpty()
    }
    val hasText = uiState.textInput.isNotBlank()

    val hideKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        keyboardMode = false
    }
    val enterKeyboard: () -> Unit = {
        onInterrupt()
        keyboardMode = true
    }
    val submitTyped: () -> Unit = {
        if (hasText) {
            onInterrupt()
            onSendClick()
        }
    }
    val endSession: () -> Unit = {
        onInterrupt()
        onDismiss()
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }
    LaunchedEffect(keyboardMode) {
        if (keyboardMode) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
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

    // Keep the exact card bounds/padding. The parent draws outside the opaque surfaces, without
    // reserving additional space above the IME. ModulateAlpha avoids an offscreen alpha texture
    // clipping the outward halo while the sheet enters; each surface's own shape clips content.
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .testTag("assistant_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Response surface above the pill: latest exchange, action row, close.
            AnimatedVisibility(
                visible = hasResponse,
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
                AssistantResponseSurface(
                    messages = messages,
                    isLoading = uiState.isLoading,
                    notice = notice,
                    isError = isError,
                    showOpenInApp = hasContent,
                    onOpenInApp = onOpenInApp,
                    onNewChat = onNewChat,
                    onCopy = {
                        val text = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.content
                        if (!text.isNullOrBlank()) {
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                ?.setPrimaryClip(android.content.ClipData.newPlainText("Assistant response", text))
                        }
                    },
                    onClose = endSession
                )
            }

            // One short line for errors and the microphone hint when there is no response surface.
            AnimatedVisibility(
                visible = !hasResponse && notice != null,
                enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()),
                exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha())
            ) {
                AssistantNoticeSurface(message = notice.orEmpty(), isError = isError)
            }

            // The pill.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(38.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Leading state glyph.
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        when {
                            capturing -> VoiceWaveform(
                                amplitudeData = amplitudeData,
                                barCount = 5,
                                barWidth = 3.dp,
                                barSpacing = 2.dp,
                                minBarHeight = 4.dp,
                                maxBarHeight = 22.dp
                            )
                            thinking -> TypingIndicator(
                                dotSize = 6.dp,
                                dotSpacing = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            speaking -> Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            else -> Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Main text area: text field in keyboard mode, otherwise the live state line.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = !keyboardMode,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { enterKeyboard() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (keyboardMode) {
                            if (uiState.textInput.isEmpty()) {
                                Text(
                                    text = "Ask anything",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = uiState.textInput,
                                onValueChange = onTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(onSend = { submitTyped() })
                            )
                        } else {
                            val (line, emphasised) = when {
                                capturing && partialTranscript.isNotBlank() -> partialTranscript to true
                                capturing -> "Listening\u2026" to false
                                thinking -> "Thinking\u2026" to false
                                speaking -> "Speaking\u2026" to false
                                voiceState is VoiceState.Error -> "Try again" to false
                                else -> "Ask anything" to false
                            }
                            Text(
                                text = line,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (emphasised) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }

                    // Keyboard toggle.
                    IconButton(onClick = { if (keyboardMode) hideKeyboard() else enterKeyboard() }) {
                        Icon(
                            imageVector = if (keyboardMode) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                            contentDescription = if (keyboardMode) "Hide keyboard" else "Keyboard"
                        )
                    }

                    // Primary action: Send / Stop / Mic.
                    val primaryModifier = Modifier.size(56.dp)
                    when {
                        keyboardMode && hasText -> FilledIconButton(
                            onClick = submitTyped,
                            modifier = primaryModifier
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                        capturing -> FilledIconButton(
                            onClick = onStopVoice,
                            modifier = primaryModifier,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop listening")
                        }
                        busy -> FilledIconButton(
                            onClick = onInterrupt,
                            modifier = primaryModifier,
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Interrupt")
                        }
                        else -> FilledIconButton(
                            onClick = {
                                hideKeyboard()
                                onInterrupt()
                                onVoiceClick()
                            },
                            enabled = voiceEnabled,
                            modifier = primaryModifier
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Microphone")
                        }
                    }

                    // Session close lives here only while there is no response surface.
                    if (!hasResponse) {
                        IconButton(onClick = endSession) {
                            Icon(Icons.Default.Close, contentDescription = "End assistant session")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantResponseSurface(
    messages: List<AssistantMessage>,
    isLoading: Boolean,
    notice: String?,
    isError: Boolean,
    showOpenInApp: Boolean,
    onOpenInApp: () -> Unit,
    onNewChat: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    val hasAssistantText = messages.any { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showOpenInApp) {
                    TextButton(
                        onClick = onOpenInApp,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Open in app", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.AddComment, contentDescription = "New chat")
                }
                if (hasAssistantText) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy response")
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "End assistant session")
                }
            }
            AssistantResponseCard(
                messages = messages,
                isLoading = isLoading,
                onOpenInApp = onOpenInApp,
                showContinueButton = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            )
            if (notice != null) {
                Text(
                    text = notice,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantNoticeSurface(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Text(
            text = message,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
