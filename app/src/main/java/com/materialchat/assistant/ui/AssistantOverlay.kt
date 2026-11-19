package com.materialchat.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.material3.Surface
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
import com.materialchat.ui.theme.particles.CapsuleBackdropBlur
import com.materialchat.ui.theme.particles.CapsuleBackdropBlurSupport
import com.materialchat.ui.theme.particles.EdgeGlowPhase
import com.materialchat.ui.theme.particles.ParticleFieldStyle
import com.materialchat.ui.theme.particles.neuralParticleField
import com.materialchat.ui.theme.particles.readAnimationScale
import com.materialchat.ui.theme.particles.screenEdgeGlow
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Settings
import com.materialchat.assistant.screen.ScreenContextAccess
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.materialchat.assistant.ui.components.VoiceWaveform
import com.materialchat.assistant.attach.AssistantAttachMode
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.components.AttachmentPreviewRow
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector

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
    onOpenClock: (AssistantEvent.OpenClock) -> Unit,
    /** Reports whether the assistant is working; the session applies the keep-awake grace. */
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
    /** The user wants to add a photo; the session opens the camera or picker activity. */
    onRequestAttach: (AssistantEvent.Attach) -> Unit = {},
    /** The screen could not be read; the session opens the system's digital-assistant settings. */
    onOpenScreenContextSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val screenAttachment by viewModel.screenAttachment.collectAsStateWithLifecycle()
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
            // The keyboard and navigation bar are docked to the bottom of the SCREEN. Measuring
            // from the window's bottom double-counts on phones that both shrink the session
            // window above the keyboard and still report the full IME inset (the remaining gap).
            val screenBottom = hostView.context.getSystemService(android.view.WindowManager::class.java)
                ?.maximumWindowMetrics?.bounds?.bottom ?: (rootLoc[1] + root.height)
            AssistantKeyboardSpacing.remainingObstructionPx(
                hostBottomOnScreen = hostLoc[1] + hostView.height,
                rootBottomOnScreen = maxOf(screenBottom, rootLoc[1] + root.height),
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

    // Leaving: a dismiss first lets the window's decorations (edge light, screenshot backdrop)
    // animate out, then hides. Every new invocation starts with the decorations present.
    val leavingState = remember { mutableStateOf(false) }
    val leaving = leavingState.value
    LaunchedEffect(showTrigger) { leavingState.value = false }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AssistantEvent.OpenClock -> onOpenClock(event)
                is AssistantEvent.OpenInApp -> onOpenInApp(event.conversationId)
                is AssistantEvent.Attach -> onRequestAttach(event)
                is AssistantEvent.Dismiss -> {
                    leavingState.value = true
                    delay(AssistantExitMotion.durationMillis(readAnimationScale(context)))
                    onDismiss()
                }
            }
        }
    }

    // The display timeout used to cut off a spoken reply on the lock screen. Hold the screen on
    // while the assistant works and for a grace period after the answer, then let it sleep.
    val assistantBusy = uiState.isLoading || uiState.isStreaming || uiState.isSpeaking ||
        voiceState is VoiceState.Listening || voiceState is VoiceState.Processing ||
        voiceState is VoiceState.Thinking || voiceState is VoiceState.Responding ||
        voiceState is VoiceState.Speaking
    // Report the working state only. The grace period after the answer is timed by the session,
    // not here: a long delay inside the composition keeps the frame clock busy for its whole
    // duration, which pins the display of any test (or tooling) that advances that clock.
    LaunchedEffect(assistantBusy) {
        onKeepScreenOnChanged(assistantBusy)
    }

    // The screen-edge light follows the session's phase: full while capturing speech, calmer
    // while thinking, answering or speaking, a gentle frame while waiting, out when leaving.
    val edgeGlowPhase = when {
        leaving -> EdgeGlowPhase.Hidden
        voiceState is VoiceState.Listening || voiceState is VoiceState.Processing -> EdgeGlowPhase.Listening
        uiState.isLoading || uiState.isStreaming || uiState.isSpeaking ||
            voiceState is VoiceState.Thinking || voiceState is VoiceState.Responding ||
            voiceState is VoiceState.Speaking -> EdgeGlowPhase.Busy
        else -> EdgeGlowPhase.Idle
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
                // The theme's scrim role (black in every palette) at the alpha the contrast model assumes.
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = AssistantContrast.SCRIM_ALPHA))
                // The edge light frames the whole window (before the keyboard padding) and sits
                // under the pill and its particle field (before them in the chain). The hosted
                // gate's control band stays outside its reach; see scripts/check-assistant-window.sh.
                .screenEdgeGlow(
                    phase = edgeGlowPhase,
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.inversePrimary,
                        MaterialTheme.colorScheme.primaryContainer
                    ),
                    enabled = neuralExpressive
                )
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
                    cornerRadius = AssistantShapes.PillCorner,
                    // As in essentials' Gemini pill: the pill's own colour is painted as a
                    // soft-edged capsule with the accent glowing inside along the edge, and the
                    // scattered dots drift in the band around that blurred edge.
                    featherColor = AssistantColors.pillFill(MaterialTheme.colorScheme),
                    style = ParticleFieldStyle(innerBand = 20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    enabled = neuralExpressive,
                    // The card reports its rect; until it does, there is nothing to decorate.
                    capsuleInset = null,
                    // The dots follow listening only: they fade in (600 ms) when recognition
                    // starts, fade out (800 ms) when it stops, and request no frames in between.
                    // Their first appearance fades in too. The pill keeps its soft fill and
                    // a resting inner glow regardless; only the dots come and go.
                    active = voiceState is VoiceState.Listening || voiceState is VoiceState.Processing,
                    fadeInOnFirstAppearance = true,
                    restingGlow = 0.6f
                )
        ) {
            // essentials additionally blurs what is behind the window inside the pill's rect.
            // That region blur is a hidden API; the probe attaches only where it resolves and
            // logs its status either way (NeuralParticles: backdrop=...).
            if (neuralExpressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val backdropStatus = remember(context) { CapsuleBackdropBlurSupport.status(context) }
                if (backdropStatus == CapsuleBackdropBlurSupport.Status.Available) {
                    CapsuleBackdropBlur(
                        rect = { decorationRect },
                        cornerRadius = with(density) { AssistantShapes.PillCorner.toPx() },
                        radiusPx = with(density) { 40.dp.toPx() },
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    LaunchedEffect(backdropStatus) { CapsuleBackdropBlurSupport.log(backdropStatus) }
                }
            }
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
                    onRetry = viewModel::retryLastQuery,
                    onOpenInApp = viewModel::openInApp,
                    onDismiss = viewModel::dismiss,
                    onNewChat = viewModel::startNewChat,
                    pendingAttachments = pendingAttachments,
                    onAttach = viewModel::requestAttach,
                    onRemoveAttachment = viewModel::removeAttachment,
                    screenAttachment = screenAttachment,
                    onRemoveScreenContext = viewModel::removeScreenContext,
                    onOpenScreenContextSettings = onOpenScreenContextSettings,
                    onCardBoundsChange = { cardBoundsInRoot = it },
                    softEdges = neuralExpressive,
                    leaving = leaving,
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

/** One line of the pill's text area; [kind] decides whether a change animates or updates in place. */
private data class AssistantStateLine(val text: String, val emphasised: Boolean, val kind: String)

/** Timing of the staged exit: decorations animate out for this long before the window hides. */
internal object AssistantExitMotion {
    private const val BASE_MILLIS = 320L

    /** Scaled by the animator duration scale; zero (animations off) hides at once. */
    fun durationMillis(animationScale: Float): Long {
        val scale = if (animationScale.isFinite() && animationScale > 0f) animationScale.coerceAtMost(10f) else 0f
        return (BASE_MILLIS * scale).toLong()
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    /** Photos waiting to go with the question, shown as removable thumbnails inside the opened pill. */
    pendingAttachments: List<Attachment> = emptyList(),
    /** Take a photo or choose one; the per-message limit is enforced by the caller. */
    onAttach: (AssistantAttachMode) -> Unit = {},
    onRemoveAttachment: (Attachment) -> Unit = {},
    /** The screen capture staged for this question, shown as a labelled removable chip in the opened pill. */
    screenAttachment: Attachment? = null,
    onRemoveScreenContext: () -> Unit = {},
    /** The question wanted the screen and nothing arrived: opens the system's digital-assistant settings. */
    onOpenScreenContextSettings: () -> Unit = {},
    /** Soft-edged pill: no own fill or shadow; the parent paints a blurred fill behind it. */
    softEdges: Boolean = false,
    /** The session is dismissing: the sheet drops and shrinks out before the window hides. */
    leaving: Boolean = false,
    /** Re-sends the question a failed request interrupted (the failure notice's "Try again"). */
    onRetry: () -> Unit = {},
    onCardBoundsChange: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    // Content colours resolved against the fills they are drawn on (see AssistantContrast).
    val palette = rememberAssistantSurfacePalette()

    val voiceEnabled = hasMicPermission && uiState.isVoiceInputEnabled
    val capturing = voiceState is VoiceState.Listening || voiceState is VoiceState.Processing
    val speaking = voiceState is VoiceState.Speaking || uiState.isSpeaking
    val busy = uiState.isLoading || uiState.isStreaming || speaking ||
        voiceState is VoiceState.Thinking || voiceState is VoiceState.Responding
    val thinking = busy && !speaking && !capturing
    val hasResponse = messages.isNotEmpty() || uiState.isLoading
    // Worded for what the platform reports (API 37 request state when available).
    val screenUnavailableNotice = remember(context) {
        ScreenContextAccess.notice(ScreenContextAccess.requestState(context))
    }
    // The one notice the window shows (message, severity, its single action), resolved by the
    // same pure function the JVM test enumerates. Nothing here reads a raw string any more.
    val notice = AssistantNotices.resolve(
        error = uiState.error,
        notice = uiState.notice,
        voiceError = voiceState as? VoiceState.Error,
        hasQuestion = uiState.userQuery.isNotBlank(),
        screenContextUnavailable = uiState.screenContextUnavailable,
        screenUnavailableNotice = screenUnavailableNotice,
        hasMicPermission = hasMicPermission,
        voiceInputEnabled = uiState.isVoiceInputEnabled,
        keyboardMode = keyboardMode
    )
    val partialTranscript = uiState.textInput.ifBlank {
        (voiceState as? VoiceState.Processing)?.partialText.orEmpty()
    }
    val hasText = uiState.textInput.isNotBlank()

    // The photo reveal inside the pill (see AssistantAttachRevealState): the `+` in the trailing
    // cluster opens the two attach actions within the pill's own surface; pending photos keep the
    // pill opened on their own so the thumbnails stay removable.
    var attachReveal by remember { mutableStateOf(AssistantAttachRevealState()) }
    // The staged screen capture opens the pill exactly like a photo would.
    val attachmentCount = pendingAttachments.size + (if (screenAttachment != null) 1 else 0)
    val attachAffordanceVisible =
        AssistantAttachRevealState.affordanceVisible(keyboardMode, attachmentCount)
    val attachExpanded = AssistantAttachRevealState.expanded(attachReveal.open, attachmentCount)
    LaunchedEffect(attachAffordanceVisible) {
        attachReveal = attachReveal.affordanceVisibilityChanged(attachAffordanceVisible)
    }
    LaunchedEffect(leaving) {
        if (leaving) attachReveal = attachReveal.pillHidden()
    }

    val hideKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        keyboardMode = false
        attachReveal = attachReveal.keyboardHidden()
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
    // The notice's one action, routed to the same handlers the pill's own controls use so a
    // retry from the notice behaves exactly like pressing the microphone or Send.
    val onNoticeAction: (AssistantNoticeAction) -> Unit = { action ->
        when (action) {
            AssistantNoticeAction.RetryRequest -> onRetry()
            AssistantNoticeAction.RetryListening -> {
                hideKeyboard()
                onInterrupt()
                onVoiceClick()
            }
            AssistantNoticeAction.TypeInstead -> enterKeyboard()
            AssistantNoticeAction.OpenApp -> onOpenInApp()
            AssistantNoticeAction.ScreenSettings -> onOpenScreenContextSettings()
        }
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

    // Entrance: the sheet rises from below the screen edge on the container spring and settles
    // with a visible overshoot (M3 Expressive spatial). Exit: it drops a short way and shrinks
    // toward its bottom anchor on a critically damped spring, so it leaves without bouncing back
    // into view, and settles inside AssistantExitMotion's window before the window hides.
    val shown = isVisible && !leaving
    val offsetY by animateDpAsState(
        targetValue = when {
            shown -> 0.dp
            leaving -> 120.dp
            else -> 400.dp
        },
        animationSpec = if (leaving) AssistantMotion.exit() else AssistantMotion.container(),
        label = "sheetOffset"
    )

    // Scale anchored to the bottom centre: the surface grows up out of the pill's resting spot on
    // arrival (playful hero spring) and sinks back into it on departure.
    val sheetScale by animateFloatAsState(
        targetValue = when {
            shown -> 1f
            leaving -> 0.9f
            else -> 0.95f
        },
        animationSpec = if (leaving) AssistantMotion.exit() else AssistantMotion.playful(),
        label = "sheetScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = AssistantMotion.effect(),
        label = "sheetAlpha"
    )

    // Keep the exact card bounds/padding. The parent draws outside the opaque surfaces, without
    // reserving additional space above the IME. ModulateAlpha avoids an offscreen alpha texture
    // clipping the outward halo while the sheet enters; each surface's own shape clips content.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AssistantSpacing.Inset,
                vertical = AssistantKeyboardSpacing.GAP_DP.dp
            )
            .heightIn(max = 600.dp)
            .offset { IntOffset(0, offsetY.roundToPx()) }
            .graphicsLayer {
                scaleX = sheetScale
                scaleY = sheetScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                this.alpha = alpha
                clip = false
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .testTag("assistant_sheet"),
            verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Between)
        ) {
            // Response surface above the pill: latest exchange, action row, close.
            // The answer surface rises out of the pill: a weighty spring settle with a slight
            // scale-up, anchored to its bottom edge where the pill is.
            AnimatedVisibility(
                visible = hasResponse,
                enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                    slideInVertically(
                        animationSpec = AssistantMotion.surfaceReveal(),
                        initialOffsetY = { it / 2 }
                    ) +
                    scaleIn(
                        animationSpec = AssistantMotion.surfaceReveal(),
                        initialScale = 0.92f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    ),
                exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                    slideOutVertically(
                        animationSpec = AssistantMotion.surfaceReveal(),
                        targetOffsetY = { it / 2 }
                    ) +
                    scaleOut(
                        animationSpec = AssistantMotion.surfaceReveal(),
                        targetScale = 0.92f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    ),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                AssistantResponseSurface(
                    messages = messages,
                    isLoading = uiState.isLoading,
                    notice = notice,
                    onNoticeAction = onNoticeAction,
                    screenContextUsed = uiState.screenContextUsed,
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

            // The notice surface above the pill when there is no response surface. The last
            // notice is kept for the exit transition so the surface leaves with its text.
            var lastNotice by remember { mutableStateOf(notice) }
            LaunchedEffect(notice) { if (notice != null) lastNotice = notice }
            AnimatedVisibility(
                visible = !hasResponse && notice != null,
                enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                    scaleIn(animationSpec = AssistantMotion.stateSwitch(), initialScale = 0.9f),
                exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                    scaleOut(animationSpec = AssistantMotion.stateSwitch(), targetScale = 0.9f)
            ) {
                (notice ?: lastNotice)?.let { AssistantNoticeSurface(notice = it, onAction = onNoticeAction) }
            }

            // The pill. At rest it is exactly the 76dp capsule (what the particle test and the
            // hosted gate measure through the sheet's edges). Photos open it: the reveal region
            // (thumbnails, the two attach actions) grows upward out of the same surface, under the
            // same 38dp corners, so the pill reads as opening rather than a second row arriving.
            // Nothing is reserved while it is closed and nothing is attached.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .testTag("assistant_pill")
                    // The particle field and soft fill wrap the pill itself.
                    .onGloballyPositioned { onCardBoundsChange(it.boundsInRoot()) },
                shape = AssistantShapes.Pill,
                color = if (softEdges) Color.Transparent else palette.pill.fill,
                // A transparent Surface would otherwise pick black content (icons were black).
                // The content colour is resolved against the pill's painted fill, which is the
                // same colour whether the particle field feathers it or the Surface paints it.
                contentColor = palette.pill.body,
                tonalElevation = if (softEdges) 0.dp else 6.dp,
                shadowElevation = if (softEdges) 0.dp else 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                // The opened region sits above the row and is anchored to it: it expands from
                // its bottom edge on the answer surface's spring, so the pill's top rises.
                AnimatedVisibility(
                    visible = attachExpanded,
                    enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                        expandVertically(
                            animationSpec = AssistantMotion.surfaceReveal(),
                            expandFrom = Alignment.Bottom
                        ),
                    exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                        shrinkVertically(
                            animationSpec = AssistantMotion.surfaceReveal(),
                            shrinkTowards = Alignment.Bottom
                        )
                ) {
                    AssistantAttachRevealContent(
                        attachments = pendingAttachments,
                        showActions = attachReveal.open,
                        onAttach = { mode ->
                            attachReveal = attachReveal.modeChosen()
                            onAttach(mode)
                        },
                        onRemoveAttachment = onRemoveAttachment,
                        screenAttachment = screenAttachment,
                        onRemoveScreenContext = onRemoveScreenContext
                    )
                }

                // The pill's row: unchanged 76dp geometry.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(start = AssistantSpacing.PillInset, end = AssistantSpacing.Row),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AssistantSpacing.Row)
                ) {
                    // Leading state glyph inside its own 40dp container. The container is the
                    // state signal beyond colour alone: transparent at rest, it fills with the
                    // phase's tonal container (primary while capturing, secondary while thinking,
                    // tertiary while speaking) and morphs from a circle to the M3 selected
                    // rounded square while thinking, on the fast spatial spring. The glyph
                    // itself pops in on each phase change instead of a hard swap.
                    val phase = when {
                        capturing -> AssistantPhase.Capturing
                        thinking -> AssistantPhase.Thinking
                        speaking -> AssistantPhase.Speaking
                        else -> AssistantPhase.Idle
                    }
                    val glyphCorner by animateDpAsState(
                        targetValue = if (phase == AssistantPhase.Thinking) AssistantShapes.GlyphSquareCorner
                        else AssistantShapes.GlyphRoundCorner,
                        animationSpec = AssistantMotion.morph(),
                        label = "glyphCorner"
                    )
                    val glyphContainer by animateColorAsState(
                        targetValue = AssistantColors.glyphContainer(MaterialTheme.colorScheme, phase),
                        animationSpec = AssistantMotion.effect(),
                        label = "glyphContainer"
                    )
                    // The content follows the container on the same effects spring: both ends
                    // are the palette's contrast-resolved pairs, and the glyph never sits on
                    // the new fill in the old fill's colour for longer than the fill itself
                    // takes to arrive.
                    val glyphContent by animateColorAsState(
                        targetValue = palette.glyph(phase).content,
                        animationSpec = AssistantMotion.effect(),
                        label = "glyphContent"
                    )
                    Box(
                        modifier = Modifier
                            .size(AssistantSizes.Control)
                            .clip(RoundedCornerShape(glyphCorner))
                            .background(glyphContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = phase,
                            transitionSpec = {
                                (fadeIn(animationSpec = AssistantMotion.effect()) +
                                    scaleIn(animationSpec = AssistantMotion.stateSwitch(), initialScale = 0.6f))
                                    .togetherWith(
                                        fadeOut(animationSpec = AssistantMotion.effect()) +
                                            scaleOut(animationSpec = AssistantMotion.stateSwitch(), targetScale = 0.6f)
                                    )
                            },
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(AssistantSizes.Glyph),
                            label = "assistantGlyph"
                        ) { current ->
                            Box(modifier = Modifier.size(AssistantSizes.Glyph), contentAlignment = Alignment.Center) {
                                when (current) {
                                    AssistantPhase.Capturing -> VoiceWaveform(
                                        amplitudeData = amplitudeData,
                                        barColor = glyphContent,
                                        barCount = 5,
                                        barWidth = 3.dp,
                                        barSpacing = 2.dp,
                                        minBarHeight = 4.dp,
                                        maxBarHeight = 22.dp
                                    )
                                    // The M3 Expressive loading indicator: polygons morphing into
                                    // one another, the spec's "working" motion, inside the square.
                                    // Fills its 32dp slot so the polygon morph reads at pill
                                    // size instead of as a small spinner.
                                    AssistantPhase.Thinking -> LoadingIndicator(
                                        modifier = Modifier.size(AssistantSizes.Glyph),
                                        color = glyphContent
                                    )
                                    AssistantPhase.Speaking -> Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = glyphContent
                                    )
                                    AssistantPhase.Idle -> Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = glyphContent
                                    )
                                }
                            }
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
                                    text = AssistantCopy.PROMPT,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = AssistantTypography.prompt(MaterialTheme.typography),
                                    color = palette.pill.muted
                                )
                            }
                            BasicTextField(
                                value = uiState.textInput,
                                onValueChange = onTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = AssistantTypography.input(MaterialTheme.typography).copy(
                                    color = palette.pill.body
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(palette.pill.accent),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(onSend = { submitTyped() })
                            )
                        } else {
                            val line = when {
                                capturing && partialTranscript.isNotBlank() ->
                                    AssistantStateLine(partialTranscript, emphasised = true, kind = "transcript")
                                capturing -> AssistantStateLine(AssistantCopy.LISTENING, false, "listening")
                                // Speaking first: a spoken reply can still be streaming in.
                                speaking -> AssistantStateLine(AssistantCopy.SPEAKING, false, "speaking")
                                // Streaming is its own state: the answer is arriving, not awaited.
                                uiState.isStreaming -> AssistantStateLine(AssistantCopy.ANSWERING, false, "answering")
                                thinking -> AssistantStateLine(AssistantCopy.THINKING, false, "thinking")
                                else -> {
                                    // After a retryable failure the prompt invites the retry; every
                                    // other notice leaves the resting prompt in place.
                                    val resting = notice?.restingLine ?: AssistantCopy.PROMPT
                                    AssistantStateLine(
                                        resting, false,
                                        if (resting == AssistantCopy.AFTER_ERROR) "error" else "idle"
                                    )
                                }
                            }
                            // Weight contrast: the recognised words are large and semibold, the
                            // phase words and the resting prompt are light. A phase change slides
                            // the new line up from under the old one (state-switch spring); the
                            // growing transcript updates in place, keyed by kind, so each new word
                            // does not restart the transition.
                            AnimatedContent(
                                targetState = line,
                                contentKey = { it.kind },
                                transitionSpec = {
                                    (fadeIn(animationSpec = AssistantMotion.effect()) +
                                        slideInVertically(animationSpec = AssistantMotion.stateSwitch()) { it / 2 })
                                        .togetherWith(
                                            fadeOut(animationSpec = snap()) +
                                                slideOutVertically(animationSpec = AssistantMotion.effect()) { -it / 3 }
                                        )
                                        .using(SizeTransform(clip = false))
                                },
                                contentAlignment = Alignment.CenterStart,
                                label = "stateLine"
                            ) { current ->
                                Text(
                                    text = current.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (current.emphasised) AssistantTypography.transcript(MaterialTheme.typography)
                                    else AssistantTypography.prompt(MaterialTheme.typography),
                                    color = if (current.emphasised) palette.pill.body
                                    else palette.pill.muted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }
                        }
                    }

                    // Photo affordance: a `+` that folds into the trailing cluster in keyboard
                    // mode or while photos are attached, and rotates into a × while the reveal
                    // is open. Compact (40dp) so the five-control keyboard row still leaves room
                    // for the text field on narrow screens.
                    AnimatedVisibility(
                        visible = attachAffordanceVisible,
                        enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                            expandHorizontally(animationSpec = AssistantMotion.stateSwitch()),
                        exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                            shrinkHorizontally(animationSpec = AssistantMotion.stateSwitch())
                    ) {
                        val affordanceRotation by animateFloatAsState(
                            targetValue = if (attachReveal.open) 45f else 0f,
                            animationSpec = AssistantMotion.morph(),
                            label = "attachAffordance"
                        )
                        // + -> x is a shape morph as well as a rotation: the 40dp container
                        // goes from a transparent circle to the tonal rounded square of an M3
                        // selected icon button, on the fast spatial spring; the geometry the
                        // hosted gate measures (40dp, inside the 76dp row) is unchanged.
                        val affordanceCorner by animateDpAsState(
                            targetValue = if (attachReveal.open) AssistantShapes.ConnectedPressedCorner
                            else AssistantShapes.GlyphRoundCorner,
                            animationSpec = AssistantMotion.morph(),
                            label = "attachAffordanceCorner"
                        )
                        val affordanceFill by animateColorAsState(
                            targetValue = if (attachReveal.open) palette.chip.container else Color.Transparent,
                            animationSpec = AssistantMotion.effect(),
                            label = "attachAffordanceFill"
                        )
                        val affordanceContent by animateColorAsState(
                            targetValue = if (attachReveal.open) palette.chip.content else palette.pill.body,
                            animationSpec = AssistantMotion.effect(),
                            label = "attachAffordanceContent"
                        )
                        IconButton(
                            onClick = { attachReveal = attachReveal.toggled() },
                            shapes = IconButtonShapes(
                                shape = RoundedCornerShape(affordanceCorner),
                                pressedShape = AssistantShapes.ConnectedPressed
                            ),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = affordanceFill,
                                contentColor = affordanceContent
                            ),
                            modifier = Modifier
                                .size(AssistantSizes.Control)
                                .testTag("assistant_attach")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (attachReveal.open) "Close photo options" else "Add photo",
                                modifier = Modifier.graphicsLayer { rotationZ = affordanceRotation }
                            )
                        }
                    }

                    // Keyboard toggle. It folds away while there is text: the trailing cluster
                    // (+, keyboard, send, close) would otherwise leave the field ~50dp on a
                    // 360dp screen, and the keyboard is already up when you are typing — back or
                    // a tap outside still dismisses it. The attach affordance stays.
                    AnimatedVisibility(
                        visible = !hasText,
                        enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                            expandHorizontally(animationSpec = AssistantMotion.stateSwitch()),
                        exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                            shrinkHorizontally(animationSpec = AssistantMotion.stateSwitch())
                    ) {
                        AssistantChromeIconButton(onClick = { if (keyboardMode) hideKeyboard() else enterKeyboard() }) {
                            Icon(
                                imageVector = if (keyboardMode) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                                contentDescription = if (keyboardMode) "Hide keyboard" else "Keyboard"
                            )
                        }
                    }

                    // Primary action: Send / Stop / Mic. Like an M3 Expressive toggled icon button
                    // it morphs from a circle (listen, send) to a rounded square (stop) and pops
                    // when its role changes; the fill is the bold primary while idle and sending,
                    // the primary container while capturing, tonal while interrupting.
                    val primaryRole = when {
                        keyboardMode && hasText -> "send"
                        capturing -> "stop"
                        busy -> "interrupt"
                        else -> "mic"
                    }
                    val primaryPop = remember { Animatable(1f) }
                    var lastPrimaryRole by remember { mutableStateOf(primaryRole) }
                    LaunchedEffect(primaryRole) {
                        if (primaryRole != lastPrimaryRole) {
                            lastPrimaryRole = primaryRole
                            primaryPop.snapTo(0.8f)
                            primaryPop.animateTo(1f, AssistantMotion.pop())
                        }
                    }
                    val primaryCorner by animateDpAsState(
                        targetValue = if (capturing || busy) AssistantShapes.PrimarySquareCorner else AssistantShapes.PrimaryRoundCorner,
                        animationSpec = AssistantMotion.stateSwitch(),
                        label = "primaryCorner"
                    )
                    val primaryShape = RoundedCornerShape(primaryCorner)
                    // Role colours cross-fade on the effects spring instead of swapping; the
                    // endpoints are the palette's resolved pairs for each role.
                    val primaryTarget = when (primaryRole) {
                        "stop" -> palette.stopButton
                        "interrupt" -> palette.interruptButton
                        else -> palette.primaryButton
                    }
                    val primaryContainer by animateColorAsState(
                        targetValue = primaryTarget.container,
                        animationSpec = AssistantMotion.effect(),
                        label = "primaryContainer"
                    )
                    val primaryContent by animateColorAsState(
                        targetValue = primaryTarget.content,
                        animationSpec = AssistantMotion.effect(),
                        label = "primaryContent"
                    )
                    val primaryColors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primaryContainer,
                        contentColor = primaryContent
                    )
                    val primaryModifier = Modifier
                        .size(AssistantSizes.PrimaryButton)
                        .graphicsLayer {
                            scaleX = primaryPop.value
                            scaleY = primaryPop.value
                        }
                    when {
                        keyboardMode && hasText -> FilledIconButton(
                            onClick = submitTyped,
                            modifier = primaryModifier,
                            shape = primaryShape,
                            colors = primaryColors
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                        capturing -> FilledIconButton(
                            onClick = onStopVoice,
                            modifier = primaryModifier,
                            shape = primaryShape,
                            colors = primaryColors
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop listening")
                        }
                        busy -> FilledIconButton(
                            onClick = onInterrupt,
                            modifier = primaryModifier,
                            shape = primaryShape,
                            colors = primaryColors
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
                            modifier = primaryModifier,
                            shape = primaryShape,
                            colors = primaryColors
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Microphone")
                        }
                    }

                    // Session close lives here only while there is no response surface. When the
                    // answer surface takes it over (compact -> expanded) it folds away sideways on
                    // the state-switch spring so the pill's row reflows instead of jumping.
                    AnimatedVisibility(
                        visible = !hasResponse,
                        enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                            expandHorizontally(animationSpec = AssistantMotion.stateSwitch()),
                        exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                            shrinkHorizontally(animationSpec = AssistantMotion.stateSwitch())
                    ) {
                        AssistantChromeIconButton(onClick = endSession) {
                            Icon(Icons.Default.Close, contentDescription = "End assistant session")
                        }
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
    /** The one notice (failure, limit or hint) shown under the exchange, with its single action. */
    notice: AssistantNotice?,
    onNoticeAction: (AssistantNoticeAction) -> Unit,
    /** The answer was produced with the screen capture: the small "Using screen" marker. */
    screenContextUsed: Boolean = false,
    showOpenInApp: Boolean,
    onOpenInApp: () -> Unit,
    onNewChat: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val hasAssistantText = messages.any { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }
    val palette = rememberAssistantSurfacePalette()
    // The answer surface is the primary content of the expanded screen, not a card floating above
    // the pill: the accent-tinted high container with wide organic top corners carries the
    // exchange with generous insets (M3 containment: a filled container groups the question and
    // the answer), and the controls sit in one capsule toolbar strip along its bottom edge, one
    // container step lower, like an M3 floating toolbar. The reading order is question, answer,
    // then actions; nothing competes with the answer above it.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        shape = AssistantShapes.Answer,
        color = palette.answer.fill,
        contentColor = palette.answer.body,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = AssistantMotion.contentSize())
                .padding(start = AssistantSpacing.SurfaceInset, end = AssistantSpacing.SurfaceInset, top = AssistantSpacing.SurfaceInset, bottom = AssistantSpacing.Inset),
            verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Inset)
        ) {
            AssistantResponseCard(
                messages = messages,
                isLoading = isLoading,
                onOpenInApp = onOpenInApp,
                showContinueButton = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
            if (screenContextUsed) {
                // The user must be able to tell that screen data went out with this question.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AssistantSpacing.Gap),
                    modifier = Modifier.testTag("assistant_screen_used")
                ) {
                    // The same icon size the chips and the Screen chip use.
                    Icon(
                        imageVector = Icons.Outlined.Screenshot,
                        contentDescription = null,
                        modifier = Modifier.size(AssistantSizes.ChipIcon),
                        tint = palette.answer.muted
                    )
                    Text(
                        text = AssistantCopy.USING_SCREEN,
                        style = AssistantTypography.notice(MaterialTheme.typography),
                        color = palette.answer.muted
                    )
                }
            }
            if (notice != null) {
                // The same notice surface the pill shows on its own: a message is never a bare
                // line of coloured text on the answer fill, and its action lives inside it.
                AssistantNoticeSurface(notice = notice, onAction = onNoticeAction)
            }
            AssistantAnswerToolbar(
                showOpenInApp = showOpenInApp,
                showCopy = hasAssistantText,
                onOpenInApp = onOpenInApp,
                onNewChat = onNewChat,
                onCopy = onCopy,
                onClose = onClose
            )
        }
    }
}

/**
 * The answer surface's action strip: a full-capsule tray one container step below the answer
 * (surfaceContainerLow inside the tinted high container). Open in app is the one tonal chip; the
 * icon actions keep their 48dp targets, and End assistant session stays the trailing control.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssistantAnswerToolbar(
    showOpenInApp: Boolean,
    showCopy: Boolean,
    onOpenInApp: () -> Unit,
    onNewChat: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val palette = rememberAssistantSurfacePalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AssistantShapes.Toolbar,
        color = palette.toolbar.fill,
        contentColor = palette.toolbar.muted
    ) {
        val textMeasurer = rememberTextMeasurer()
        val actionStyle = AssistantTypography.action(MaterialTheme.typography)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AssistantSpacing.Between, end = AssistantSpacing.Tight, top = AssistantSpacing.Tight, bottom = AssistantSpacing.Tight)
        ) {
            // The icon actions form one connected M3 Expressive group: 2dp joins, outer 20dp /
            // inner 8dp corners by position, every item tightening to 12dp while pressed.
            // Touch targets stay 48dp (the buttons keep their minimum interactive size).
            val iconCount = if (showCopy) 3 else 2
            // W3: at 360dp the strip is 284dp wide (336 sheet - 2 x 20 surface inset - 8 - 4);
            // three 48dp targets with 2dp joins take 148dp and the labelled chip (14 + 16 + 6 +
            // label + 14) about 138dp at font scale 1.0 and ~165dp at 1.3, so the row cannot
            // always hold both. The widths are measured from the same styles the chip draws;
            // when the chip, one spacing step and the group exceed the strip, the chip gives
            // up its label and joins the group as its leading icon instead of being clipped
            // by the capsule. Nothing is reserved, nothing is ellipsised.
            val labelWidth = with(LocalDensity.current) {
                textMeasurer.measure(AssistantCopy.OPEN_IN_APP, actionStyle, maxLines = 1).size.width.toDp()
            }
            val chipWidth = AssistantSpacing.ChipInset * 2 + AssistantSizes.ChipIcon + AssistantSpacing.Gap + labelWidth
            val groupWidth = AssistantSizes.Target * iconCount +
                ButtonGroupDefaults.ConnectedSpaceBetween * (iconCount - 1)
            val labelledChip = showOpenInApp &&
                chipWidth + AssistantSpacing.Between + groupWidth <= maxWidth
            val compactOpen = showOpenInApp && !labelledChip
            val count = iconCount + if (compactOpen) 1 else 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (labelledChip) {
                    AssistantActionChip(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = AssistantCopy.OPEN_IN_APP,
                        onClick = onOpenInApp
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var index = 0
                    if (compactOpen) {
                        AssistantToolbarAction(index = index++, count = count, onClick = onOpenInApp) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = AssistantCopy.OPEN_IN_APP)
                        }
                    }
                    AssistantToolbarAction(index = index++, count = count, onClick = onNewChat) {
                        Icon(Icons.Default.AddComment, contentDescription = "New chat")
                    }
                    if (showCopy) {
                        AssistantToolbarAction(index = index++, count = count, onClick = onCopy) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy response")
                        }
                    }
                    AssistantToolbarAction(index = index, count = count, onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "End assistant session")
                    }
                }
            }
        }
    }
}

/** One item of the answer toolbar's connected group: tonal, per-position shape, press morph. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssistantToolbarAction(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val palette = rememberAssistantSurfacePalette()
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonShapes(
            shape = AssistantShapes.connectedShape(index, count),
            pressedShape = AssistantShapes.ConnectedPressed
        ),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = palette.chip.container,
            contentColor = palette.chip.content
        ),
        content = content
    )
}

/**
 * The pill's opened region, above its 76dp row and inside its own surface: the pending photos
 * (the chat composer's preview row: 64dp images, 48dp remove targets, kept under the
 * `assistant_attachments` tag) and, while the reveal is open, the two attach actions as tonal
 * chips trailing under the pill's primary button. Each part folds in and out on its own, so
 * choosing an action closes the chips while the thumbnails stay.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssistantAttachRevealContent(
    attachments: List<Attachment>,
    showActions: Boolean,
    onAttach: (AssistantAttachMode) -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    /** The staged screen capture, above the photos, with its own remove target. */
    screenAttachment: Attachment? = null,
    onRemoveScreenContext: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = attachments.isNotEmpty() || screenAttachment != null,
            enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                expandVertically(animationSpec = AssistantMotion.surfaceReveal(), expandFrom = Alignment.Bottom),
            exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                shrinkVertically(animationSpec = AssistantMotion.surfaceReveal(), shrinkTowards = Alignment.Bottom)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (screenAttachment != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AssistantSpacing.Inset, start = AssistantSpacing.PillInset, end = AssistantSpacing.PillInset)
                    ) {
                        AssistantScreenChip(attachment = screenAttachment, onRemove = onRemoveScreenContext)
                    }
                }
                if (attachments.isNotEmpty()) {
                    // The remove button of each item overhangs its top edge by 12dp; the top inset
                    // keeps it inside the capsule's clip.
                    AttachmentPreviewRow(
                        attachments = attachments,
                        onRemoveAttachment = onRemoveAttachment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AssistantSpacing.Block, start = AssistantSpacing.PillInset, end = AssistantSpacing.PillInset)
                            .testTag("assistant_attachments")
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showActions,
            enter = fadeIn(animationSpec = AssistantMotion.effect()) +
                expandVertically(animationSpec = AssistantMotion.surfaceReveal(), expandFrom = Alignment.Bottom),
            exit = fadeOut(animationSpec = AssistantMotion.effect()) +
                shrinkVertically(animationSpec = AssistantMotion.surfaceReveal(), shrinkTowards = Alignment.Bottom)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AssistantSpacing.Inset, start = AssistantSpacing.PillInset, end = AssistantSpacing.Row),
                // The two actions are one connected pair: 2dp join, outer corners only on
                // the group's outside edges, press morph to 12dp. Same row, same 40dp height.
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistantActionChip(
                    icon = Icons.Default.PhotoCamera,
                    label = AssistantCopy.TAKE_PHOTO,
                    shape = AssistantShapes.connectedShape(index = 0, count = 2),
                    onClick = { onAttach(AssistantAttachMode.Camera) }
                )
                AssistantActionChip(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = AssistantCopy.ATTACH_PHOTO,
                    shape = AssistantShapes.connectedShape(index = 1, count = 2),
                    onClick = { onAttach(AssistantAttachMode.Gallery) }
                )
            }
        }
    }
}

/**
 * The one labelled action chip of the assistant window: 40dp tonal capsule (or a per-position
 * connected shape), 16dp leading icon, bold label, 12dp press morph, the contrast-resolved chip
 * pair. Open in app, Take photo, Attach photo and Open assistant settings all draw this, so
 * every labelled action on the answer surface and inside the opened pill reads as one family.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssistantActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AssistantShapes.Chip
) {
    val palette = rememberAssistantSurfacePalette()
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonShapes(shape = shape, pressedShape = AssistantShapes.ConnectedPressed),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = palette.chip.container,
            contentColor = palette.chip.content
        ),
        contentPadding = PaddingValues(horizontal = AssistantSpacing.ChipInset, vertical = AssistantSpacing.Gap),
        modifier = modifier.height(AssistantSizes.ChipHeight)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AssistantSizes.ChipIcon))
        Spacer(Modifier.size(AssistantSpacing.Gap))
        // A 40dp chip has one line; a label that cannot fit its row ellipsises instead of
        // wrapping into a second line the capsule would clip.
        Text(label, style = AssistantTypography.action(MaterialTheme.typography), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The staged screen capture inside the opened pill: a labelled chip with the capture's
 * thumbnail and its own remove target, so the user sees that the screen is about to go out
 * with the question and can take it out. Kept apart from the photo thumbnails because it is
 * not a photo the user chose.
 */
@Composable
private fun AssistantScreenChip(attachment: Attachment, onRemove: () -> Unit) {
    val palette = rememberAssistantSurfacePalette()
    Surface(
        shape = AssistantShapes.Chip,
        color = palette.chip.container,
        contentColor = palette.chip.content,
        modifier = Modifier
            .height(AssistantSizes.ChipHeight)
            .testTag("assistant_screen_chip")
    ) {
        Row(
            modifier = Modifier.padding(start = AssistantSpacing.Gap, end = AssistantSpacing.Hairline),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AssistantSpacing.Between)
        ) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(attachment.uri).build(),
                contentDescription = "Screen capture",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(AssistantSizes.Thumbnail)
                    .clip(RoundedCornerShape(AssistantShapes.ThumbnailCorner))
            )
            Icon(
                imageVector = Icons.Outlined.Screenshot,
                contentDescription = null,
                modifier = Modifier.size(AssistantSizes.ChipIcon)
            )
            Text(AssistantCopy.SCREEN_CHIP, style = AssistantTypography.action(MaterialTheme.typography))
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(AssistantSizes.CompactTarget)
                    .testTag("assistant_screen_remove")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove screen context",
                    modifier = Modifier.size(AssistantSizes.ChipIcon)
                )
            }
        }
    }
}

/**
 * The pill's chrome icon buttons (keyboard toggle, session close): an M3 Expressive icon button
 * whose shape tightens from a circle to a 12dp rounded square while pressed, with a short
 * compression on the fast spatial spring. Transparent container, so the content colour is the
 * pill's resolved body colour inherited from the Surface.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssistantChromeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = ExpressiveMotion.Press.Scale,
        label = "chromePress"
    )
    IconButton(
        onClick = onClick,
        shapes = IconButtonShapes(
            shape = RoundedCornerShape(AssistantShapes.GlyphRoundCorner),
            pressedShape = AssistantShapes.ConnectedPressed
        ),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * The one notice surface of the assistant window, above the pill or inside the answer surface:
 * a leading glyph and the message on the notice's own resolved content colour, inside the
 * contrast-resolved notice / error-notice container, and, when the notice offers one, its
 * single action as an outlined chip in that same content colour (the chip adds no new fill).
 * The container's arrival (fade + scale on the state-switch spring) is owned by the caller.
 */
@Composable
private fun AssistantNoticeSurface(
    notice: AssistantNotice,
    onAction: (AssistantNoticeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberAssistantSurfacePalette()
    val container = if (notice.isError) palette.errorNotice.container else palette.notice.fill
    val content = if (notice.isError) palette.errorNotice.content else palette.notice.muted
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AssistantShapes.Notice,
        color = container,
        contentColor = content,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(start = AssistantSpacing.ChipInset, end = AssistantSpacing.ChipInset, top = AssistantSpacing.Inset, bottom = AssistantSpacing.Inset),
            verticalArrangement = Arrangement.spacedBy(AssistantSpacing.Row)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AssistantSpacing.Row)
            ) {
                Icon(
                    imageVector = if (notice.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(AssistantSizes.NoticeGlyph),
                    tint = content
                )
                // W3: the longest notice (ScreenContextAccess.notice, 131 characters) has 280dp
                // at 360dp (336 sheet - 2 x 14 inset - 18 glyph - 10 gap). bodyMedium at font
                // scale 1.3 (18.2sp, ~9.5dp per character) gives ~29 characters per line, so it
                // needs five lines; six keeps every notice whole at 1.3 and still bounds a
                // runaway engine message, which the previous four lines cut mid-sentence.
                Text(
                    text = notice.message,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    style = AssistantTypography.notice(MaterialTheme.typography),
                    color = content
                )
            }
            val action = notice.action
            if (action != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AssistantNoticeActionChip(
                        icon = when (action) {
                            AssistantNoticeAction.RetryRequest,
                            AssistantNoticeAction.RetryListening -> Icons.Default.Refresh
                            AssistantNoticeAction.TypeInstead -> Icons.Default.Keyboard
                            AssistantNoticeAction.OpenApp -> Icons.AutoMirrored.Filled.OpenInNew
                            AssistantNoticeAction.ScreenSettings -> Icons.Outlined.Settings
                        },
                        label = action.label,
                        content = content,
                        onClick = { onAction(action) },
                        // The screen-context settings action keeps the tag the hosted gate reads.
                        modifier = Modifier.testTag(
                            if (action == AssistantNoticeAction.ScreenSettings) "assistant_screen_settings"
                            else "assistant_notice_action"
                        )
                    )
                }
            }
        }
    }
}

/**
 * A notice's action: the chip family's 40dp height, 16dp icon and bold label, drawn outlined in
 * the notice's own content colour (no fill of its own, so it reads on the error container as
 * well as the plain notice), with the shared press morph (20dp -> 12dp on the Press.Shape token).
 */
@Composable
private fun AssistantNoticeActionChip(
    icon: ImageVector,
    label: String,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) AssistantShapes.ConnectedPressedCorner else AssistantShapes.GlyphRoundCorner,
        animationSpec = ExpressiveMotion.Press.Shape,
        label = "noticeActionCorner"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = Color.Transparent,
        contentColor = content,
        border = BorderStroke(1.dp, content),
        interactionSource = interactionSource,
        modifier = modifier.height(AssistantSizes.ChipHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AssistantSpacing.ChipInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AssistantSpacing.Gap)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AssistantSizes.ChipIcon), tint = content)
            // "Open assistant settings" (23 characters, labelLarge bold) is ~230dp at font scale
            // 1.3 plus 50dp of insets, icon and gap: it fits the 280dp notice row, and a longer
            // label would ellipsise on its one line rather than wrap under the capsule's clip.
            Text(
                label,
                style = AssistantTypography.action(MaterialTheme.typography),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
