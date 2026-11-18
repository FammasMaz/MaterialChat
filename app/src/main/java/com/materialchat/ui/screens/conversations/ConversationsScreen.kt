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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import com.materialchat.ui.components.rememberDeviceTilt
import com.materialchat.ui.components.rememberShakeEnergy
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
import com.materialchat.ui.components.ExpressiveSnackbar
import com.materialchat.ui.components.dialogEntrance
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialchat.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.components.ExpressiveFastScrollBar
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.fastScrollDateLabel
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.conversations.components.ConversationItem
import com.materialchat.ui.screens.conversations.components.ExpandableConversationGroup
import com.materialchat.ui.screens.conversations.components.SegmentedRowShapes
import com.materialchat.ui.screens.conversations.components.SwipeToDeleteBox
import com.materialchat.ui.screens.search.SearchUiState
import com.materialchat.ui.screens.search.SearchViewModel
import com.materialchat.ui.screens.search.components.ChatSearchBar
import com.materialchat.ui.screens.search.components.SearchResultItem
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.components.HomeSettingsSlot
import com.materialchat.ui.components.rememberHomeSettingsPalette
import com.materialchat.ui.theme.ExpressiveShapes
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
    val statusText = homeStatusText(uiState)

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
                        statusText = statusText,
                        onSearchClick = { isSearchActive = true },
                        onTempChatClick = { viewModel.createTemporaryConversation() }
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                ExpressiveSnackbar(snackbarData = snackbarData)
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
                onCreateConversation = { viewModel.createNewConversation() },
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
            modifier = Modifier.dialogEntrance(),
            shape = ExpressiveShapes.Dialog,
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
    statusText: String?,
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
    val shakeEnergy = rememberShakeEnergy()

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
                // Three shapes, top-right only: the left half belongs to the status line and the
                // wordmark, so nothing decorative ever sits under them.
                val motion = rememberParticleMotionGate().motionAllowed

                val gears = if (motion) rememberInfiniteTransition(label = "bannerGears") else null
                val gearLarge = if (gears != null) {
                    gears.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
                        label = "gearLarge"
                    ).value
                } else 0f

                // Staggered spring pop-in
                @Composable
                fun entrance(
                    delayMs: Long,
                    stiffness: Float
                ): Animatable<Float, AnimationVector1D> {
                    val anim = remember(delayMs, stiffness) { Animatable(0f) }
                    LaunchedEffect(anim, delayMs, stiffness) {
                        delay(delayMs)
                        anim.animateTo(1f, ExpressiveMotion.Spatial.playful())
                    }
                    return anim
                }
                val popGearLarge = entrance(0L, 140f)

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = decorAlpha }
                ) {
                    // Gear A - large cookie bleeding off the top-right edge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 14.dp, y = (-18).dp)
                            .size(92.dp)
                            .graphicsLayer {
                                val tilt = tiltPose.value
                                translationX = tilt.roll * -1.0f
                                translationY = tilt.pitch * -0.5f
                                translationX += kotlin.math.sin(shakeEnergy.value * 19f) * shakeEnergy.value * 14f
                                rotationZ = gearLarge + tilt.roll * 0.35f
                                scaleX = popGearLarge.value
                                scaleY = popGearLarge.value
                            }
                            .background(
                                color = primaryColor.copy(alpha = 0.13f),
                                shape = MaterialShapes.Cookie12Sided.toShape()
                            )
                    )
                }
            }


            // Live status line above the title; fades out with the shape art on collapse.
            if (statusText != null && decorAlpha > 0f) {
                HomeStatusChip(
                    text = statusText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = baseBottomPadding + 50.dp)
                        .graphicsLayer { alpha = decorAlpha }
                )
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
                    // NBSP keeps the final glyph clear of the animated clip edge.
                    text = "material\u00A0",
                    modifier = Modifier
                        .clipToBounds()
                        .shrinkWidth(materialWidth)
                        .graphicsLayer { alpha = materialAlpha.coerceIn(0f, 1f) },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
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

/**
 * One-line status for the header chip: chat count plus the active provider,
 * or a setup hint while the list is empty. Null while loading or in error.
 */
@Composable
private fun homeStatusText(uiState: ConversationsUiState): String? = when (uiState) {
    is ConversationsUiState.Success -> {
        val count = uiState.conversationGroups.size.takeIf { it > 0 } ?: uiState.conversations.size
        val countText = pluralStringResource(R.plurals.home_status_chat_count, count, count)
        uiState.activeProvider?.let { "$countText \u00B7 ${it.name}" } ?: countText
    }
    is ConversationsUiState.Empty -> stringResource(
        if (uiState.hasActiveProvider) R.string.home_status_ready else R.string.home_status_no_provider
    )
    else -> null
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
    onCreateConversation: () -> Unit,
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
        shape = ExpressiveShapes.ScreenContainer,
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
                        onCreateConversation = onCreateConversation,
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
                                onCreateConversation = onCreateConversation,
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
        modifier = Modifier.dialogEntrance(),
        shape = ExpressiveShapes.Dialog,
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
            // Resolved against the dialog container: primary reads 4.1:1 on light VIOLET/CITRUS.
            color = rememberHomeSettingsPalette()[HomeSettingsSlot.DialogLabel]
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

/**
 * Chats / Archive switch as one M3 Expressive connected button group: the selected
 * segment morphs its shape and the pressed one its width on the shared spatial spring.
 * The archived count stays in the label so no information is lost.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConversationFilterBar(
    selectedFilter: ConversationListFilter,
    archivedCount: Int,
    onSelectFilter: (ConversationListFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val activeSelected = selectedFilter == ConversationListFilter.ACTIVE
    val archivedSelected = selectedFilter == ConversationListFilter.ARCHIVED
    val activeSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val archivedSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    androidx.compose.material3.ButtonGroup(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween
        )
    ) {
        androidx.compose.material3.ToggleButton(
            checked = activeSelected,
            onCheckedChange = {
                if (!activeSelected) {
                    haptics.perform(HapticPattern.CLICK)
                    onSelectFilter(ConversationListFilter.ACTIVE)
                }
            },
            modifier = Modifier
                .weight(1f)
                .animateWidth(activeSource),
            shapes = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes(),
            interactionSource = activeSource,
            colors = androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(androidx.compose.material3.ToggleButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(androidx.compose.material3.ToggleButtonDefaults.IconSpacing))
            Text(text = "Chats", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        androidx.compose.material3.ToggleButton(
            checked = archivedSelected,
            onCheckedChange = {
                if (!archivedSelected) {
                    haptics.perform(HapticPattern.CLICK)
                    onSelectFilter(ConversationListFilter.ARCHIVED)
                }
            },
            modifier = Modifier
                .weight(1f)
                .animateWidth(archivedSource),
            shapes = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes(),
            interactionSource = archivedSource,
            colors = androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = if (archivedSelected) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                contentDescription = null,
                modifier = Modifier.size(androidx.compose.material3.ToggleButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(androidx.compose.material3.ToggleButtonDefaults.IconSpacing))
            Text(
                text = if (archivedCount > 0) "Archive ($archivedCount)" else "Archive",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    onCreateConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeEmptyState(
        headline = "No conversations yet",
        supportingText = if (hasActiveProvider) {
            "Tap the button below to start a new chat"
        } else {
            "Add an AI provider in settings to get started"
        },
        icon = Icons.Outlined.ChatBubbleOutline,
        accent = EmptyStateAccent.Primary,
        action = if (hasActiveProvider) {
            EmptyStateAction(stringResource(R.string.home_empty_action_start_chat), onCreateConversation)
        } else {
            EmptyStateAction("Go to Settings", onNavigateToSettings)
        },
        modifier = modifier
    )
}

@Composable
private fun FilteredEmptyContent(
    filter: ConversationListFilter,
    hasActiveProvider: Boolean,
    hasArchivedChats: Boolean,
    onCreateConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val archived = filter == ConversationListFilter.ARCHIVED
    val body = when {
        archived -> "Swipe any chat and tap Archive to keep it out of your main list."
        hasArchivedChats -> "Your archived chats are tucked away. Start a new chat or switch to Archive."
        hasActiveProvider -> "Tap the button below to start a new chat"
        else -> "Add an AI provider in settings to get started"
    }
    val action = when {
        archived -> null
        hasActiveProvider -> EmptyStateAction(
            label = stringResource(R.string.home_empty_action_start_chat),
            onClick = onCreateConversation
        )
        else -> EmptyStateAction(label = "Go to Settings", onClick = onNavigateToSettings)
    }

    HomeEmptyState(
        headline = if (archived) "No archived chats" else "No active chats",
        supportingText = body,
        icon = if (archived) Icons.Outlined.Archive else Icons.Outlined.ChatBubbleOutline,
        accent = if (archived) EmptyStateAccent.Secondary else EmptyStateAccent.Primary,
        action = action,
        modifier = modifier
    )
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
    val entrance = rememberStaggeredEntranceState()
    ConversationScrollHaptics(listState = listState, hapticsEnabled = hapticsEnabled)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = listBottomClearance() // clears the floating toolbar and its "+"
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentedRowShapes.Gap)
        ) {
            itemsIndexed(
                items = conversations,
                key = { _, item -> item.conversation.id },
                contentType = { _, item -> item.conversation.isArchived }
            ) { index, conversationItem ->
            val baseCorners = SegmentedRowShapes.corners(index = index, count = conversations.size)
            SwipeToDeleteBox(
                onDelete = { onConversationDelete(conversationItem.conversation) },
                hapticsEnabled = hapticsEnabled,
                onArchive = { onConversationArchiveToggle(conversationItem.conversation) },
                isArchived = conversationItem.conversation.isArchived,
                onSwipeRight = { onConversationSwipeRight(conversationItem.conversation) },
                baseCorners = baseCorners,
                activeCorners = SegmentedRowShapes.Pressed,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index = index, state = entrance)
                    .animateItem(
                        fadeInSpec = ExpressiveMotion.ListItem.FadeIn,
                        fadeOutSpec = ExpressiveMotion.ListItem.FadeOut,
                        placementSpec = ExpressiveMotion.ListItem.Placement
                    )
            ) {
                ConversationItem(
                    conversationItem = conversationItem,
                    onClick = {
                        haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                        onConversationClick(conversationItem.conversation.id)
                    },
                    onLongClick = { onConversationLongClick(conversationItem) },
                    corners = baseCorners
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
                .padding(top = 16.dp, bottom = listBottomClearance(), end = 2.dp)
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
    val entrance = rememberStaggeredEntranceState()
    ConversationScrollHaptics(listState = listState, hapticsEnabled = hapticsEnabled)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = listBottomClearance() // clears the floating toolbar and its "+"
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentedRowShapes.Gap)
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
                cornerRadius = SegmentedRowShapes.OuterRadius,
                seamRadius = SegmentedRowShapes.InnerRadius,
                showDivider = false,
                isFirst = isFirst,
                isLast = isLast,
                hapticsEnabled = hapticsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index = index, state = entrance)
                    .animateItem(
                        fadeInSpec = ExpressiveMotion.ListItem.FadeIn,
                        fadeOutSpec = ExpressiveMotion.ListItem.FadeOut,
                        placementSpec = ExpressiveMotion.ListItem.Placement
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
                .padding(top = 16.dp, bottom = listBottomClearance(), end = 2.dp)
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
        shape = ExpressiveShapes.ScreenContainer,
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
                        fadeInSpec = ExpressiveMotion.ListItem.FadeIn,
                        fadeOutSpec = ExpressiveMotion.ListItem.FadeOut,
                        placementSpec = ExpressiveMotion.ListItem.Placement
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
