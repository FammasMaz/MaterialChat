package com.materialchat.ui.screens.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.materialchat.ui.components.HapticPattern
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
import kotlin.math.roundToInt
import kotlin.random.Random

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
                        onSearchClick = { isSearchActive = true }
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
                onConversationDelete = { viewModel.deleteConversation(it) },
                onRetry = { viewModel.retry() },
                onNavigateToSettings = { viewModel.navigateToSettings() },
                onToggleGroupExpanded = { viewModel.toggleGroupExpanded(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchClick: () -> Unit = {}
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

    // Per-session randomized illustration — different layout every time
    val rng = remember { List(20) { Random.nextFloat() } }

    // M3 Expressive staggered entrance: each element group animates in sequence
    // Spatial springs (can bounce) for scale/position, with increasing bounciness
    val curveProgress = remember { Animatable(0f) }
    val bubbleProgress = remember { Animatable(0f) }
    val dotProgress = remember { Animatable(0f) }
    val sparkleProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            curveProgress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 80f))
        }
        launch {
            delay(150)
            bubbleProgress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 100f))
        }
        launch {
            delay(300)
            dotProgress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 140f))
        }
        launch {
            delay(420)
            sparkleProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 160f))
        }
    }
    // Decorative elements fade out early as bar collapses
    val decorAlpha = (1f - collapseFraction * 2.5f).coerceIn(0f, 1f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

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

            // M3 Expressive decorative line illustration
            // Randomized layout + staggered spring entrance animation
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = decorAlpha }
            ) {
                val w = size.width
                val h = size.height

                // === Flowing curves — drift in from right, fade in ===
                val cp = curveProgress.value
                if (cp > 0.01f) {
                    val drift = (1f - cp) * 40f.dp.toPx()

                    val curve1 = Path().apply {
                        moveTo(-drift, h * (0.12f + rng[0] * 0.15f) + drift * 0.3f)
                        cubicTo(
                            w * (0.18f + rng[1] * 0.10f), h * (rng[2] * 0.30f) + drift * 0.2f,
                            w * (0.45f + rng[3] * 0.10f), h * (0.15f + rng[4] * 0.35f) + drift * 0.1f,
                            w * (0.80f + rng[5] * 0.15f) + drift, h * (0.05f + rng[6] * 0.20f)
                        )
                    }
                    drawPath(
                        curve1,
                        primaryColor.copy(alpha = 0.12f * cp),
                        style = Stroke(1.8f.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val curve2 = Path().apply {
                        moveTo(w * (0.05f + rng[7] * 0.12f) - drift, h * (0.30f + rng[8] * 0.15f) + drift * 0.2f)
                        cubicTo(
                            w * (0.30f + rng[9] * 0.15f), h * (0.08f + rng[10] * 0.20f) + drift * 0.15f,
                            w * (0.58f + rng[11] * 0.12f), h * (0.35f + rng[12] * 0.20f) + drift * 0.1f,
                            w * 1.05f + drift, h * (0.15f + rng[13] * 0.15f)
                        )
                    }
                    drawPath(
                        curve2,
                        tertiaryColor.copy(alpha = 0.09f * cp),
                        style = Stroke(1.4f.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val curve3 = Path().apply {
                        moveTo(w * (0.20f + rng[14] * 0.12f) - drift * 0.5f, h * (0.05f + rng[15] * 0.12f) + drift * 0.4f)
                        cubicTo(
                            w * (0.42f + rng[16] * 0.10f), h * (0.20f + rng[17] * 0.20f) + drift * 0.2f,
                            w * (0.68f + rng[18] * 0.10f), h * (rng[19] * 0.15f) + drift * 0.1f,
                            w * 1.10f + drift * 0.5f, h * (0.22f + rng[0] * 0.15f)
                        )
                    }
                    drawPath(
                        curve3,
                        primaryColor.copy(alpha = 0.06f * cp),
                        style = Stroke(1f.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // === Chat bubble outlines — scale up from center with bounce ===
                val bp = bubbleProgress.value
                if (bp > 0.01f) {
                    val b1cx = w * (0.58f + rng[1] * 0.15f)
                    val b1cy = h * (0.10f + rng[2] * 0.10f)
                    val b1w = w * (0.14f + rng[3] * 0.06f) * bp
                    val b1h = h * (0.18f + rng[4] * 0.08f) * bp
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.09f * bp),
                        topLeft = Offset(b1cx - b1w / 2f, b1cy - b1h / 2f + (1f - bp) * 10f.dp.toPx()),
                        size = Size(b1w, b1h),
                        cornerRadius = CornerRadius(10f.dp.toPx() * bp),
                        style = Stroke(1.3f.dp.toPx())
                    )

                    val b2cx = w * (0.74f + rng[5] * 0.14f)
                    val b2cy = h * (0.26f + rng[6] * 0.10f)
                    val b2w = w * (0.10f + rng[7] * 0.05f) * bp
                    val b2h = h * (0.14f + rng[8] * 0.06f) * bp
                    drawRoundRect(
                        color = tertiaryColor.copy(alpha = 0.07f * bp),
                        topLeft = Offset(b2cx - b2w / 2f, b2cy - b2h / 2f + (1f - bp) * 8f.dp.toPx()),
                        size = Size(b2w, b2h),
                        cornerRadius = CornerRadius(8f.dp.toPx() * bp),
                        style = Stroke(1f.dp.toPx())
                    )
                }

                // === Dots — pop in with bouncy radius scale ===
                val dotProg = dotProgress.value
                if (dotProg > 0.01f) {
                    val dots = listOf(
                        Triple(0.48f + rng[9] * 0.10f, 0.08f + rng[10] * 0.10f, 3f + rng[11] * 2f),
                        Triple(0.68f + rng[12] * 0.10f, 0.38f + rng[13] * 0.10f, 3.5f + rng[14] * 2f),
                        Triple(0.30f + rng[15] * 0.12f, 0.24f + rng[16] * 0.10f, 2f + rng[17] * 1.5f),
                        Triple(0.84f + rng[18] * 0.08f, 0.10f + rng[19] * 0.08f, 2.5f + rng[0] * 1.5f),
                        Triple(0.14f + rng[1] * 0.10f, 0.12f + rng[2] * 0.10f, 4f + rng[3] * 2f)
                    )
                    dots.forEachIndexed { i, (dx, dy, dr) ->
                        val color = if (i % 2 == 0) primaryColor else tertiaryColor
                        drawCircle(
                            color = color.copy(alpha = (0.08f + rng[(i + 4) % 20] * 0.08f) * dotProg),
                            radius = dr.dp.toPx() * dotProg,
                            center = Offset(w * dx, h * dy + (1f - dotProg) * 12f.dp.toPx())
                        )
                    }
                }

                // === Sparkle crosses — arms grow outward with bounce ===
                val sp = sparkleProgress.value
                if (sp > 0.01f) {
                    val sparkles = listOf(
                        Triple(0.52f + rng[5] * 0.12f, 0.28f + rng[6] * 0.12f, 5f + rng[7] * 2f),
                        Triple(0.80f + rng[8] * 0.10f, 0.06f + rng[9] * 0.08f, 3.5f + rng[10] * 2f)
                    )
                    sparkles.forEachIndexed { i, (sx, sy, sa) ->
                        val color = if (i == 0) primaryColor else tertiaryColor
                        val cx = w * sx
                        val cy = h * sy
                        val arm = sa.dp.toPx() * sp
                        drawLine(
                            color.copy(alpha = 0.13f * sp),
                            start = Offset(cx - arm, cy),
                            end = Offset(cx + arm, cy),
                            strokeWidth = 1f.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color.copy(alpha = 0.13f * sp),
                            start = Offset(cx, cy - arm),
                            end = Offset(cx, cy + arm),
                            strokeWidth = 1f.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
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
                    text = "material",
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

            // Search icon button - M3 Expressive: encircled tonal container
            val searchInteractionSource = remember { MutableInteractionSource() }
            val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
            val searchScale by animateFloatAsState(
                targetValue = if (isSearchPressed) 0.9f else 1f,
                animationSpec = ExpressiveMotion.Spatial.scale(),
                label = "searchScale"
            )

            Surface(
                onClick = { haptics.perform(HapticPattern.CLICK); onSearchClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = baseBottomPadding)
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = searchScale
                        scaleY = searchScale
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                interactionSource = searchInteractionSource
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (uiState) {
                is ConversationsUiState.Loading -> {
                    LoadingContent()
                }
                is ConversationsUiState.Empty -> {
                    EmptyContent(
                        hasActiveProvider = uiState.hasActiveProvider,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                is ConversationsUiState.Success -> {
                    if (uiState.conversationGroups.isNotEmpty()) {
                        GroupedConversationList(
                            groups = uiState.conversationGroups,
                            listState = listState,
                            onConversationClick = onConversationClick,
                            onConversationDelete = onConversationDelete,
                            onToggleGroupExpanded = onToggleGroupExpanded,
                            hapticsEnabled = uiState.hapticsEnabled
                        )
                    } else {
                        ConversationList(
                            conversations = uiState.conversations,
                            listState = listState,
                            onConversationClick = onConversationClick,
                            onConversationDelete = onConversationDelete,
                            hapticsEnabled = uiState.hapticsEnabled
                        )
                    }
                }
                is ConversationsUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent() {
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
private fun EmptyContent(
    hasActiveProvider: Boolean,
    onNavigateToSettings: () -> Unit
) {
    val haptics = rememberHapticFeedback()
    Column(
        modifier = Modifier
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
private fun ConversationList(
    conversations: List<ConversationUiItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConversationClick: (String) -> Unit,
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()
    val cornerRadius = 20.dp

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 88.dp // Extra padding for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = conversations,
            key = { _, item -> item.conversation.id }
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
                    shape = itemShape,
                    showDivider = !isLast
                )
            }
        }
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
    onConversationDelete: (com.materialchat.domain.model.Conversation) -> Unit,
    onToggleGroupExpanded: (String) -> Unit,
    hapticsEnabled: Boolean = true
) {
    val cornerRadius = 20.dp

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 88.dp // Extra padding for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = groups,
            key = { _, group -> group.parent.conversation.id }
        ) { index, group ->
            val isFirst = index == 0
            val isLast = index == groups.lastIndex

            ExpandableConversationGroup(
                group = group,
                onParentClick = onConversationClick,
                onBranchClick = onConversationClick,
                onExpandToggle = onToggleGroupExpanded,
                onDelete = onConversationDelete,
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
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    val haptics = rememberHapticFeedback()
    Column(
        modifier = Modifier
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
            key = { it.id }
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
