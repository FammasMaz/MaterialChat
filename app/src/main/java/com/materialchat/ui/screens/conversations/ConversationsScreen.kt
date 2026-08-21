package com.materialchat.ui.screens.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import com.materialchat.ui.components.rememberDeviceTilt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.components.ExpressiveFastScrollBar
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.fastScrollDateLabel
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.conversations.components.ConversationItem
import com.materialchat.ui.screens.conversations.components.ExpandableConversationGroup
import com.materialchat.ui.screens.conversations.components.SwipeToDeleteBox
import com.materialchat.ui.screens.conversations.components.SwipeCornerSpec
import com.materialchat.ui.screens.search.SearchUiState
import com.materialchat.ui.screens.search.SearchViewModel
import com.materialchat.ui.screens.search.components.ChatSearchBar
import com.materialchat.ui.screens.search.components.SearchResultItem
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapeToken
import com.materialchat.ui.theme.LocalMainButtonShape
import com.materialchat.ui.theme.expressiveControlShape
import com.materialchat.ui.theme.toExpressiveShapeToken
import kotlin.math.roundToInt
import kotlin.random.Random
import java.text.DateFormat
import java.util.Date

/**
 * Main conversations screen showing the list of all conversations.
 *
 * Features:
 * - Large collapsing top app bar with Material 3 Expressive styling
 * - Conversation list with swipe-to-delete
 * - Extended FAB for creating new conversations
 * - Empty state with illustration
 * - Settings navigation
 * - Search functionality for finding chats by title and content
 *
 * @param onNavigateToChat Callback to navigate to a chat screen
 * @param onNavigateToSettings Callback to navigate to settings
 * @param viewModel The ViewModel for this screen
 * @param searchViewModel The ViewModel for search functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Shared list state for scroll detection (M3 Expressive FAB behavior)
    val conversationListState = rememberLazyListState()

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by searchViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()

    // Rename dialog state
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameConversation by remember { mutableStateOf<com.materialchat.domain.model.Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var metadataConversation by remember { mutableStateOf<ConversationUiItem?>(null) }

    // Handle back gesture when search is active
    BackHandler(enabled = isSearchActive) {
        searchViewModel.clearSearch()
        isSearchActive = false
    }

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
                is ConversationsEvent.ShowNoProviderError -> {
                    coroutineScope.launch {
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
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
                },
                label = "topBarTransition"
            ) { searchActive ->
                if (searchActive) {
                    // Search mode - show search bar
                    Surface(
                        modifier = Modifier.statusBarsPadding(),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        ChatSearchBar(
                            query = searchQuery,
                            onQueryChange = searchViewModel::onQueryChange,
                            onClear = searchViewModel::clearSearch,
                            onClose = {
                                searchViewModel.clearSearch()
                                isSearchActive = false
                            }
                        )
                    }
                } else {
                    // Normal mode - show regular top bar
                    ConversationsTopBar(
                        scrollBehavior = scrollBehavior,
                        onSearchClick = { isSearchActive = true },
                        onTempChatClick = { viewModel.createTemporaryConversation() }
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
        // Show search results when search is active, otherwise show conversation list
        if (isSearchActive) {
            SearchContent(
                searchState = searchUiState,
                paddingValues = paddingValues,
                onResultClick = { conversationId ->
                    searchViewModel.clearSearch()
                    isSearchActive = false
                    onNavigateToChat(conversationId)
                }
            )
        } else {
            ConversationsContent(
                uiState = uiState,
                paddingValues = paddingValues,
                listState = conversationListState,
                onConversationClick = { viewModel.openConversation(it) },
                onConversationLongClick = { metadataConversation = it },
                onConversationDelete = { viewModel.deleteConversation(it) },
                onConversationArchiveToggle = { conversation ->
                    if (conversation.isArchived) {
                        viewModel.unarchiveConversation(conversation)
                    } else {
                        viewModel.archiveConversation(conversation)
                    }
                },
                onSelectFilter = { viewModel.selectFilter(it) },

                onConversationSwipeRight = { conversation ->
                    renameConversation = conversation
                    renameText = conversation.title
                    showRenameDialog = true
                },
                onCreateTemporaryConversation = { viewModel.createTemporaryConversation() },
                onRetry = { viewModel.retry() },
                onNavigateToSettings = { viewModel.navigateToSettings() },
                onToggleGroupExpanded = { viewModel.toggleGroupExpanded(it) }
            )
        }
    }

    metadataConversation?.let { item ->
        ConversationMetadataDialog(
            item = item,
            onDismiss = { metadataConversation = null }
        )
    }

    // Rename dialog
    if (showRenameDialog && renameConversation != null) {
        val dialogHaptics = rememberHapticFeedback()
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameConversation = null
            },
            title = { Text("Edit Title") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            dialogHaptics.perform(HapticPattern.CLICK)
                            renameConversation?.let { conv ->
                                viewModel.retryTitleGeneration(conv)
                            }
                            showRenameDialog = false
                            renameConversation = null
                        }
                    ) {
                        Text("Retry with AI")
                    }
                    TextButton(
                        onClick = {
                            dialogHaptics.perform(HapticPattern.CONFIRM)
                            renameConversation?.let { conv ->
                                viewModel.deleteConversation(conv)
                            }
                            showRenameDialog = false
                            renameConversation = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    val conv = renameConversation
                    if (conv != null) {
                        TextButton(
                            onClick = {
                                dialogHaptics.perform(HapticPattern.CLICK)
                                if (conv.isArchived) {
                                    viewModel.unarchiveConversation(conv)
                                } else {
                                    viewModel.archiveConversation(conv)
                                }
                                showRenameDialog = false
                                renameConversation = null
                            }
                        ) {
                            Text(
                                if (conv.isArchived) "Unarchive" else "Archive",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogHaptics.perform(HapticPattern.CLICK)
                        renameConversation?.let { conv ->
                            if (renameText.isNotBlank()) {
                                viewModel.renameConversation(conv.id, renameText.trim())
                            }
                        }
                        showRenameDialog = false
                        renameConversation = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogHaptics.perform(HapticPattern.CLICK)
                        showRenameDialog = false
                        renameConversation = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConversationsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchClick: () -> Unit = {},
    onTempChatClick: () -> Unit = {}
) {
    val expandedHeight = 152.dp
    val collapsedHeight = 72.dp
    val collapseFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val barHeight = expandedHeight - (expandedHeight - collapsedHeight) * collapseFraction
    
    val titleScaleTarget = 1f - 0.22f * collapseFraction
    val materialWidthTarget = (1f - collapseFraction * 1.25f).coerceIn(0f, 1f)
    val suffixWidthTarget = ((collapseFraction - 0.2f) / 0.8f).coerceIn(0f, 1f)

    val spatialFloatSpec = ExpressiveMotion.Spatial.container<Float>()
    val alphaFloatSpec = ExpressiveMotion.Effects.alpha<Float>()
    
    val titleScale by animateFloatAsState(
        targetValue = titleScaleTarget,
        animationSpec = spatialFloatSpec,
        label = "titleScale"
    )
    val materialWidth by animateFloatAsState(
        targetValue = materialWidthTarget,
        animationSpec = spatialFloatSpec,
        label = "materialWidth"
    )
    val materialAlpha by animateFloatAsState(
        targetValue = materialWidthTarget,
        animationSpec = alphaFloatSpec,
        label = "materialAlpha"
    )
    val suffixWidth by animateFloatAsState(
        targetValue = suffixWidthTarget,
        animationSpec = spatialFloatSpec,
        label = "suffixWidth"
    )
    val suffixAlpha by animateFloatAsState(
        targetValue = suffixWidthTarget,
        animationSpec = alphaFloatSpec,
        label = "suffixAlpha"
    )

    // M3 Expressive shape art — a dense, layered composition that fills the bar.
    // Two meshing gears counter-rotate, a burst breathes against them, an orbit
    // trio drifts across the middle, and a clover bobs by the title. Motion is
    // deliberately visible (fast enough to notice, slow enough to stay calm).
    val decorAlpha = (1f - collapseFraction * 2.5f).coerceIn(0f, 1f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Physical tilt drives layered parallax so the art answers your hand.
    val tiltPose = rememberDeviceTilt(maxDegrees = 10f)

    val density = LocalDensity.current
    val haptics = rememberHapticFeedback()
    SideEffect {
        scrollBehavior.state.heightOffsetLimit = with(density) {
            (collapsedHeight - expandedHeight).toPx()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(
            modifier = Modifier
                .height(barHeight)
                .padding(horizontal = 16.dp)
        ) {
            val baseBottomPadding = 12.dp

            if (decorAlpha > 0f) {
                // Continuous motion engines
                val gears = rememberInfiniteTransition(label = "bannerGears")
                val gearLarge by gears.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
                    label = "gearLarge"
                )
                val gearSmall by gears.animateFloat(
                    initialValue = 360f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(9_500, easing = LinearEasing)),
                    label = "gearSmall"
                )
                val burstSpin by gears.animateFloat(
                    initialValue = 0f,
                    targetValue = -360f,
                    animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
                    label = "burstSpin"
                )
                val burstBreath by gears.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(3_400), RepeatMode.Reverse),
                    label = "burstBreath"
                )
                val cloverBob by gears.animateFloat(
                    initialValue = -1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(4_000), RepeatMode.Reverse),
                    label = "cloverBob"
                )
                val sunnySpin by gears.animateFloat(
                    initialValue = 360f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
                    label = "sunnySpin"
                )
                val orbitDrift by gears.animateFloat(
                    initialValue = -1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(7_500), RepeatMode.Reverse),
                    label = "orbitDrift"
                )
                val flowerDrift by gears.animateFloat(
                    initialValue = 1f,
                    targetValue = -1f,
                    animationSpec = infiniteRepeatable(tween(9_200), RepeatMode.Reverse),
                    label = "flowerDrift"
                )

                // Staggered spring pop-in
                @Composable
                fun entrance(
                    delayMs: Long,
                    stiffness: Float
                ): Animatable<Float, AnimationVector1D> {
                    val anim = remember(delayMs, stiffness) { Animatable(0f) }
                    LaunchedEffect(anim, delayMs, stiffness) {
                        delay(delayMs)
                        anim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = stiffness))
                    }
                    return anim
                }
                val popGearLarge = entrance(0L, 140f)
                val popGearSmall = entrance(110L, 160f)
                val popBurst = entrance(220L, 120f)
                val popClover = entrance(320L, 180f)
                val popSunny = entrance(420L, 150f)
                val popOrbit = entrance(520L, 170f)

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = decorAlpha }
                ) {
                    // Gear A — large cookie bleeding off the top-right edge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 14.dp, y = (-18).dp)
                            .size(92.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * -1.0f
                translationY = tilt.pitch * -0.5f
                rotationZ = rotationZ + (tilt.roll * 0.35f)
                                rotationZ = gearLarge
                                scaleX = popGearLarge.value
                                scaleY = popGearLarge.value
                            }
                            .background(
                                color = primaryColor.copy(alpha = 0.20f),
                                shape = MaterialShapes.Cookie12Sided.toShape()
                            )
                    )
                    // Gear B — small cookie meshed into A's teeth, spinning opposite
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 74.dp, y = 40.dp)
                            .size(48.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * -1.7f
                translationY = tilt.pitch * -0.9f
                rotationZ = rotationZ + (tilt.roll * 0.55f)
                                rotationZ = gearSmall
                                scaleX = popGearSmall.value
                                scaleY = popGearSmall.value
                            }
                            .background(
                                color = tertiaryColor.copy(alpha = 0.22f),
                                shape = MaterialShapes.Cookie6Sided.toShape()
                            )
                    )
                    // Soft burst — breathes and spins against the gear pair
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-16).dp, y = (-6).dp)
                            .size(78.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * 1.4f
                translationY = tilt.pitch * 0.7f
                                rotationZ = burstSpin
                                scaleX = burstBreath * popBurst.value
                                scaleY = burstBreath * popBurst.value
                            }
                            .background(
                                color = tertiaryColor.copy(alpha = 0.16f),
                                shape = MaterialShapes.SoftBurst.toShape()
                            )
                    )
                    // Sunny — half off the left edge at mid height, slow roll
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-26).dp, y = (-10).dp)
                            .size(54.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * -0.8f
                translationY = tilt.pitch * -0.4f
                                rotationZ = sunnySpin
                                scaleX = popSunny.value
                                scaleY = popSunny.value
                            }
                            .background(
                                color = secondaryColor.copy(alpha = 0.18f),
                                shape = MaterialShapes.Sunny.toShape()
                            )
                    )
                    // Orbit trio across the middle so the center never sits empty:
                    // a pill-soft puff drifting right of the title zone...
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (orbitDrift * 34).dp + 46.dp, y = 2.dp)
                            .size(30.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * -1.6f
                translationY = tilt.pitch * -0.8f
                                rotationZ = orbitDrift * 24f
                                scaleX = popOrbit.value
                                scaleY = popOrbit.value
                            }
                            .background(
                                color = primaryColor.copy(alpha = 0.15f),
                                shape = MaterialShapes.Puffy.toShape()
                            )
                    )
                    // ...a flower drifting the opposite way near center-right...
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = (-58).dp, y = (flowerDrift * 12).dp + (-4).dp)
                            .size(36.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * 1.5f
                translationY = tilt.pitch * 0.75f
                                rotationZ = flowerDrift * 30f
                                scaleX = popOrbit.value
                                scaleY = popOrbit.value
                            }
                            .background(
                                color = onSurfaceColor.copy(alpha = 0.10f),
                                shape = MaterialShapes.Flower.toShape()
                            )
                    )
                    // ...and a tiny echo dot riding between them.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (flowerDrift * 26).dp + 118.dp, y = (orbitDrift * 6).dp + 14.dp)
                            .size(16.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * -2.0f
                translationY = tilt.pitch * -1.0f
                scaleX = popOrbit.value; scaleY = popOrbit.value }
                            .background(
                                color = tertiaryColor.copy(alpha = 0.20f),
                                shape = MaterialShapes.Pill.toShape()
                            )
                    )
                    // Clover — bobs gently just above the title baseline
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 96.dp, y = (cloverBob * 4).dp + 4.dp)
                            .size(34.dp)
                            .graphicsLayer {
                val tilt = tiltPose.value
                translationX = tilt.roll * 2.2f
                translationY = tilt.pitch * 1.1f
                                rotationZ = cloverBob * 12f
                                scaleX = popClover.value
                                scaleY = popClover.value
                            }
                            .background(
                                color = primaryColor.copy(alpha = 0.13f),
                                shape = MaterialShapes.Clover4Leaf.toShape()
                            )
                    )
                }
            }


            // Title row - aligned to bottom left with fixed padding
            Row(
                modifier = Modifier
                    .height(44.dp) // Same height as icon buttons for alignment
                    .align(Alignment.BottomStart)
                    .padding(bottom = baseBottomPadding)
                    .graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // NBSP gives the italic final glyph room before the animated clip.
                    text = "material\u00A0",
                    modifier = Modifier
                        .clipToBounds()
                        .shrinkWidth(materialWidth)
                        .graphicsLayer { alpha = materialAlpha.coerceIn(0f, 1f) },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "s",
                    modifier = Modifier
                        .clipToBounds()
                        .shrinkWidth(suffixWidth)
                        .graphicsLayer { alpha = suffixAlpha.coerceIn(0f, 1f) },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Temp chat & Search buttons - aligned bottom-end
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = baseBottomPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopBarShapeAction(
                    icon = Icons.Outlined.AutoDelete,
                    contentDescription = "Temporary chat",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shapeToken = ExpressiveShapeToken.Clover,
                    startAngle = 45,
                    onClick = {
                        haptics.perform(HapticPattern.MORPH_TRANSITION)
                        onTempChatClick()
                    }
                )

                TopBarShapeAction(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shapeToken = ExpressiveShapeToken.CookieSoft,
                    startAngle = 30,
                    onClick = {
                        haptics.perform(HapticPattern.CLICK)
                        onSearchClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBarShapeAction(
    icon: ImageVector,
    contentDescription: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    shapeToken: ExpressiveShapeToken,
    startAngle: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val preferredShape = LocalMainButtonShape.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "topBarActionScale"
    )
    val shape = expressiveControlShape(
        token = preferredShape.toExpressiveShapeToken(shapeToken),
        pressed = isPressed,
        startAngle = startAngle
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor
            )
        }
    }
}

private fun Modifier.shrinkWidth(factor: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = (placeable.width * factor.coerceIn(0f, 1f)).roundToInt()
    layout(width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
private fun ConversationsContent(
    uiState: ConversationsUiState,
    paddingValues: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConversationClick: (String) -> Unit,
    onConversationLongClick: (ConversationUiItem) -> Unit = {},
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    onConversationArchiveToggle: (com.materialchat.domain.model.Conversation) -> Unit,
    onSelectFilter: (ConversationListFilter) -> Unit,
    onConversationSwipeRight: (com.materialchat.domain.model.Conversation) -> Unit = {},
    onCreateTemporaryConversation: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToggleGroupExpanded: (String) -> Unit = {}
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
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            when (uiState) {
                is ConversationsUiState.Loading -> {
                    LoadingContent(modifier = Modifier.weight(1f))
                }
                is ConversationsUiState.Empty -> {
                    EmptyContent(
                        hasActiveProvider = uiState.hasActiveProvider,
                        onNavigateToSettings = onNavigateToSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
                is ConversationsUiState.Success -> {
                    val showingArchived = uiState.selectedFilter == ConversationListFilter.ARCHIVED
                    val displayedGroups = if (showingArchived) {
                        uiState.archivedConversationGroups
                    } else {
                        uiState.conversationGroups
                    }
                    val displayedConversations = if (showingArchived) {
                        uiState.archivedConversations
                    } else {
                        uiState.conversations
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        ConversationFilterBar(
                            selectedFilter = uiState.selectedFilter,
                            archivedCount = uiState.archivedConversationGroups.size,
                            onSelectFilter = onSelectFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        if (displayedGroups.isNotEmpty()) {
                            GroupedConversationList(
                                groups = displayedGroups,
                                listState = listState,
                                onConversationClick = onConversationClick,
                                onConversationLongClick = onConversationLongClick,
                                onConversationDelete = onConversationDelete,
                                onConversationArchiveToggle = onConversationArchiveToggle,
                                onConversationSwipeRight = onConversationSwipeRight,
                                onToggleGroupExpanded = onToggleGroupExpanded,
                                hapticsEnabled = uiState.hapticsEnabled,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (displayedConversations.isNotEmpty()) {
                            ConversationList(
                                conversations = displayedConversations,
                                listState = listState,
                                onConversationClick = onConversationClick,
                                onConversationLongClick = onConversationLongClick,
                                onConversationDelete = onConversationDelete,
                                onConversationArchiveToggle = onConversationArchiveToggle,
                                onConversationSwipeRight = onConversationSwipeRight,
                                hapticsEnabled = uiState.hapticsEnabled,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            FilteredEmptyContent(
                                filter = uiState.selectedFilter,
                                hasActiveProvider = uiState.activeProvider != null,
                                hasArchivedChats = uiState.archivedConversationGroups.isNotEmpty(),
                                onNavigateToSettings = onNavigateToSettings,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is ConversationsUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationMetadataDialog(
    item: ConversationUiItem,
    onDismiss: () -> Unit
) {
    val conversation = item.conversation
    val titleModel = conversation.titleGeneratedByModel
    val titleProvider = conversation.titleGeneratedByProviderId
    val titleSource = if (!titleModel.isNullOrBlank()) {
        buildString {
            if (!titleProvider.isNullOrBlank()) append(titleProvider).append(" · ")
            append(titleModel)
        }
    } else {
        "Not recorded yet — retry/regenerate the title to capture the source"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetadataRow("Title", listOfNotNull(conversation.icon, conversation.title).joinToString(" "))
                MetadataRow("Provider", item.providerName)
                MetadataRow("Chat model", conversation.modelName)
                MetadataRow("Title generated by", titleSource)
                conversation.titleGeneratedAt?.let {
                    MetadataRow("Title generated", formatMetadataTime(it))
                }
                MetadataRow("Created", formatMetadataTime(conversation.createdAt))
                MetadataRow("Updated", formatMetadataTime(conversation.updatedAt))
                MetadataRow("State", when {
                    conversation.isArchived -> "Archived"
                    conversation.isBranch -> "Branch chat"
                    conversation.isEphemeral -> "Temporary chat"
                    else -> "Active chat"
                })
                item.messagePreview?.takeIf { it.isNotBlank() }?.let {
                    MetadataRow("Latest", it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatMetadataTime(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}

@Composable
private fun ConversationFilterBar(
    selectedFilter: ConversationListFilter,
    archivedCount: Int,
    onSelectFilter: (ConversationListFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedFilter == ConversationListFilter.ACTIVE,
            onClick = { onSelectFilter(ConversationListFilter.ACTIVE) },
            label = { Text("Chats") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        FilterChip(
            selected = selectedFilter == ConversationListFilter.ARCHIVED,
            onClick = { onSelectFilter(ConversationListFilter.ARCHIVED) },
            label = {
                Text(
                    if (archivedCount > 0) "Archive ($archivedCount)" else "Archive"
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (selectedFilter == ConversationListFilter.ARCHIVED) {
                        Icons.Outlined.Unarchive
                    } else {
                        Icons.Outlined.Archive
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyContent(
    hasActiveProvider: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    Column(
        modifier = modifier
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

            ExpressiveButton(onClick = { onNavigateToSettings() }, text = "Go to Settings", style = ExpressiveButtonStyle.FilledTonal)
        }
    }
}

@Composable
private fun FilteredEmptyContent(
    filter: ConversationListFilter,
    hasActiveProvider: Boolean,
    hasArchivedChats: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = if (filter == ConversationListFilter.ARCHIVED) {
        Icons.Outlined.Archive
    } else {
        Icons.Outlined.ChatBubbleOutline
    }
    val title = if (filter == ConversationListFilter.ARCHIVED) {
        "No archived chats"
    } else {
        "No active chats"
    }
    val body = if (filter == ConversationListFilter.ARCHIVED) {
        "Swipe any chat and tap Archive to keep it out of your main list."
    } else if (hasArchivedChats) {
        "Your archived chats are tucked away. Start a new chat or switch to Archive."
    } else if (hasActiveProvider) {
        "Tap the button below to start a new chat"
    } else {
        "Add an AI provider in settings to get started"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primaryContainer
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!hasActiveProvider && filter == ConversationListFilter.ACTIVE) {
            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveButton(
                onClick = onNavigateToSettings,
                text = "Go to Settings",
                style = ExpressiveButtonStyle.FilledTonal
            )
        }
    }
}

@Composable
private fun ConversationScrollHaptics(
    listState: androidx.compose.foundation.lazy.LazyListState,
    hapticsEnabled: Boolean
) {
    val haptics = rememberHapticFeedback()
    val density = LocalDensity.current
    val tickPx = remember(density) {
        with(density) { 28.dp.toPx().roundToInt().coerceAtLeast(1) }
    }
    var lastTickBucket by remember(listState) { mutableIntStateOf(Int.MIN_VALUE) }

    LaunchedEffect(listState, hapticsEnabled, tickPx) {
        snapshotFlow {
            if (!listState.isScrollInProgress) {
                null
            } else {
                val firstVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val approximateItemSize = firstVisibleItem?.size?.coerceAtLeast(tickPx) ?: tickPx * 3
                ((listState.firstVisibleItemIndex * approximateItemSize) +
                    listState.firstVisibleItemScrollOffset) / tickPx
            }
        }.collect { bucket ->
            if (bucket == null) {
                lastTickBucket = Int.MIN_VALUE
                return@collect
            }
            if (lastTickBucket == Int.MIN_VALUE) {
                lastTickBucket = bucket
                return@collect
            }
            if (bucket != lastTickBucket) {
                haptics.perform(HapticPattern.SEGMENT_TICK, hapticsEnabled)
                lastTickBucket = bucket
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<ConversationUiItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConversationClick: (String) -> Unit,
    onConversationLongClick: (ConversationUiItem) -> Unit = {},
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    onConversationArchiveToggle: (com.materialchat.domain.model.Conversation) -> Unit,
    onConversationSwipeRight: (com.materialchat.domain.model.Conversation) -> Unit = {},
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val cornerRadius = 20.dp
    ConversationScrollHaptics(listState = listState, hapticsEnabled = hapticsEnabled)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = 88.dp // Extra padding for FAB
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(
                items = conversations,
                key = { _, item -> item.conversation.id },
                contentType = { _, item -> item.conversation.isArchived }
            ) { index, conversationItem ->
            val isFirst = index == 0
            val isLast = index == conversations.lastIndex
            val baseCorners = when {
                isFirst && isLast -> SwipeCornerSpec(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                isFirst -> SwipeCornerSpec(cornerRadius, cornerRadius, 0.dp, 0.dp)
                isLast -> SwipeCornerSpec(0.dp, 0.dp, cornerRadius, cornerRadius)
                else -> SwipeCornerSpec(0.dp, 0.dp, 0.dp, 0.dp)
            }
            val itemShape = RoundedCornerShape(
                topStart = baseCorners.topStart,
                topEnd = baseCorners.topEnd,
                bottomStart = baseCorners.bottomStart,
                bottomEnd = baseCorners.bottomEnd
            )
            SwipeToDeleteBox(
                onDelete = { onConversationDelete(conversationItem.conversation) },
                hapticsEnabled = hapticsEnabled,
                onArchive = { onConversationArchiveToggle(conversationItem.conversation) },
                isArchived = conversationItem.conversation.isArchived,
                onSwipeRight = { onConversationSwipeRight(conversationItem.conversation) },
                baseCorners = baseCorners,
                activeCorners = SwipeCornerSpec(cornerRadius, cornerRadius, cornerRadius, cornerRadius),
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
                    },
                    onLongClick = { onConversationLongClick(conversationItem) },
                    shape = itemShape,
                    showDivider = !isLast
                )
            }
            }
        }

        ExpressiveFastScrollBar(
            listState = listState,
            dragLabelProvider = { index ->
                conversations.getOrNull(index)?.conversation?.updatedAt?.let { fastScrollDateLabel(it) }
            },
            dragLabelMinWidth = 124.dp,
            dragLabelMaxWidth = 228.dp,
            dragLabelMinHeight = 64.dp,
            hapticsEnabled = hapticsEnabled,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 16.dp, bottom = 88.dp, end = 2.dp)
        )
    }
}

/**
 * Displays a grouped list of conversations with expandable branch sections.
 * Uses M3 Expressive animations for expand/collapse.
 */
@Composable
private fun GroupedConversationList(
    groups: List<ConversationGroupUiItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConversationClick: (String) -> Unit,
    onConversationLongClick: (ConversationUiItem) -> Unit = {},
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    onConversationArchiveToggle: (com.materialchat.domain.model.Conversation) -> Unit,
    onConversationSwipeRight: (com.materialchat.domain.model.Conversation) -> Unit = {},
    onToggleGroupExpanded: (String) -> Unit,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val cornerRadius = 20.dp
    ConversationScrollHaptics(listState = listState, hapticsEnabled = hapticsEnabled)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = 88.dp // Extra padding for FAB
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(
                items = groups,
                key = { _, group -> group.parent.conversation.id },
                contentType = { _, group -> group.parent.conversation.isArchived }
            ) { index, group ->
            val isFirst = index == 0
            val isLast = index == groups.lastIndex

            ExpandableConversationGroup(
                group = group,
                onParentClick = onConversationClick,
                onBranchClick = onConversationClick,
                onParentLongClick = onConversationLongClick,
                onBranchLongClick = onConversationLongClick,
                onExpandToggle = onToggleGroupExpanded,
                onDelete = onConversationDelete,
                onArchiveToggle = onConversationArchiveToggle,
                onSwipeRight = onConversationSwipeRight,
                cornerRadius = cornerRadius,
                isFirst = isFirst,
                isLast = isLast,
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
            )
            }
        }

        ExpressiveFastScrollBar(
            listState = listState,
            dragLabelProvider = { index ->
                groups.getOrNull(index)?.parent?.conversation?.updatedAt?.let { fastScrollDateLabel(it) }
            },
            dragLabelMinWidth = 124.dp,
            dragLabelMaxWidth = 228.dp,
            dragLabelMinHeight = 64.dp,
            hapticsEnabled = hapticsEnabled,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 16.dp, bottom = 88.dp, end = 2.dp)
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    Column(
        modifier = modifier
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

        ExpressiveButton(onClick = { onRetry() }, text = "Retry", style = ExpressiveButtonStyle.Text)
    }
}

/**
 * Content displayed when search is active.
 * Shows search results, loading state, or empty state.
 */
@Composable
private fun SearchContent(
    searchState: SearchUiState,
    paddingValues: PaddingValues,
    onResultClick: (String) -> Unit
) {
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
        when (searchState) {
            is SearchUiState.Idle -> {
                SearchIdleContent()
            }
            is SearchUiState.Loading -> {
                SearchLoadingContent()
            }
            is SearchUiState.Results -> {
                SearchResultsList(
                    results = searchState.results,
                    onResultClick = onResultClick
                )
            }
            is SearchUiState.Empty -> {
                SearchEmptyContent(query = searchState.query)
            }
            is SearchUiState.Error -> {
                SearchErrorContent(message = searchState.message)
            }
        }
    }
}

@Composable
private fun SearchIdleContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primaryContainer
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Search your chats",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Find conversations by title or message content",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLoadingContent() {
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

@Composable
private fun SearchResultsList(
    results: List<com.materialchat.ui.screens.search.SearchResultUiItem>,
    onResultClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = results,
            key = { it.id },
            contentType = { "searchResult" }
        ) { item ->
            SearchResultItem(
                item = item,
                onClick = { onResultClick(item.conversationId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
            )
        }
    }
}

@Composable
private fun SearchEmptyContent(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primaryContainer
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No results found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No chats match \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchErrorContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Search failed",
            style = MaterialTheme.typography.titleMedium,
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
    }
}
