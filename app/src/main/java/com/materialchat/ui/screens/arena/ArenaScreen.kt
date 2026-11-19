package com.materialchat.ui.screens.arena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.screens.arena.components.ArenaBattleView
import com.materialchat.ui.screens.arena.components.ArenaModelPicker
import com.materialchat.ui.screens.arena.components.ArenaPromptInput
import com.materialchat.ui.screens.arena.components.ArenaVotingBar
import com.materialchat.ui.components.ExpressiveEmptyState
import com.materialchat.ui.components.ExpressiveSnackbar
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.LoadErrorContent
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.ExpressiveSizes

/**
 * Main Arena screen composable.
 *
 * Provides the full arena battle experience:
 * 1. Model picker with dual provider/model dropdowns
 * 2. Prompt input field
 * 3. Split-screen battle view with VS badge
 * 4. Voting bar after completion
 *
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToLeaderboard Callback to navigate to the leaderboard
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArenaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArenaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberHapticFeedback()

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ArenaEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ArenaEvent.NavigateToLeaderboard -> {
                    onNavigateToLeaderboard()
                }
                is ArenaEvent.BattleComplete -> {
                    // Battle complete - voting bar will appear automatically
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    ExpressiveTopBarTitle(
                        title = "Model Arena",
                        subtitle = "Compare model responses"
                    )
                },
                navigationIcon = {
                    ExpressiveFilledIconButton(
                        onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                },
                actions = {
                    ExpressiveButton(
                        onClick = { onNavigateToLeaderboard() },
                        text = "Leaderboard",
                        leadingIcon = Icons.Filled.EmojiEvents,
                        style = ExpressiveButtonStyle.Text
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                // Same snackbar as home/settings: inverse surface, 20dp squircle, 12dp inset.
                ExpressiveSnackbar(snackbarData = snackbarData)
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            shape = ExpressiveShapes.ScreenContainer,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
        when (val state = uiState) {
            is ArenaUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(ExpressiveSizes.IndicatorHero),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading providers\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is ArenaUiState.NoProviders -> {
                // Not a failure: the same empty-state card as the other drawer screens.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = Icons.Filled.EmojiEvents,
                        title = "No providers yet",
                        message = "Add a provider in Settings, then come back to run a battle",
                        actionText = "Go back",
                        onAction = onNavigateBack
                    )
                }
            }

            is ArenaUiState.Error -> {
                LoadErrorContent(
                    title = "Arena could not be loaded",
                    message = state.message,
                    onRetry = viewModel::retry,
                    onBack = onNavigateBack
                )
            }

            is ArenaUiState.Ready -> {
                ArenaReadyContent(
                    state = state,
                    onPromptChanged = viewModel::updatePrompt,
                    onProviderSelected = viewModel::setPickerProvider,
                    onToggleContender = { model ->
                        viewModel.toggleContender(model, state.pickerProviderId ?: "")
                    },
                    onStartBattle = viewModel::startBattle,
                    onVote = viewModel::vote,
                    onNewBattle = viewModel::newBattle,
                    modifier = Modifier
                )
            }
        }
        }
    }
}

@Composable
private fun ArenaReadyContent(
    state: ArenaUiState.Ready,
    onPromptChanged: (String) -> Unit,
    onProviderSelected: (String) -> Unit,
    onToggleContender: (com.materialchat.domain.model.AiModel) -> Unit,
    onStartBattle: () -> Unit,
    onVote: (com.materialchat.domain.usecase.ArenaVerdict) -> Unit,
    onNewBattle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val realNamesBySlot = state.contenders.associate { it.slot to it.modelName }
    val haptics = rememberHapticFeedback()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(animationSpec = ExpressiveMotion.Spatial.contentSize()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Model picker
        ArenaModelPicker(
            providers = state.providers,
            pickerProviderId = state.pickerProviderId,
            pickerModels = state.pickerModels,
            selectedContenders = state.contenders,
            usageRanking = state.usageRanking,
            onProviderSelected = onProviderSelected,
            onToggleContender = onToggleContender,
            enabled = !state.isBattleRunning,
            isLoadingModels = state.isLoadingModels
        )

        // Prompt input
        ArenaPromptInput(
            prompt = state.prompt,
            onPromptChanged = onPromptChanged,
            onSend = onStartBattle,
            enabled = !state.isBattleRunning,
            canSend = state.canStartBattle
        )

        // Battle view (visible when battle has started)
        AnimatedVisibility(
            visible = state.battleId != null || state.isBattleRunning,
            enter = fadeIn(animationSpec = ExpressiveMotion.Effects.reveal()) + slideInVertically(
                animationSpec = ExpressiveMotion.Spatial.container(),
                initialOffsetY = { it / 4 }
            ),
            exit = fadeOut(animationSpec = ExpressiveMotion.Disclosure.FadeOut) + slideOutVertically(animationSpec = ExpressiveMotion.Spatial.container())
        ) {
            ArenaBattleView(
                contenders = state.contenders,
                revealed = state.revealed,
                realNamesBySlot = realNamesBySlot,
                modifier = Modifier.height(420.dp)
            )
        }

        // Voting bar (visible when battle is complete and not yet voted)
        AnimatedVisibility(
            visible = state.isBattleComplete,
            enter = fadeIn(animationSpec = ExpressiveMotion.Effects.reveal()) + slideInVertically(
                animationSpec = ExpressiveMotion.Spatial.container(),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(animationSpec = ExpressiveMotion.Disclosure.FadeOut)
        ) {
            Column {
                ArenaVotingBar(
                    contenders = state.contenders,
                    voted = state.voted,
                    onVote = onVote
                )

                // New battle button after voting
                AnimatedVisibility(
                    visible = state.voted,
                    enter = fadeIn(animationSpec = ExpressiveMotion.Effects.reveal()) + slideInVertically(
                animationSpec = ExpressiveMotion.Spatial.container(),
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    ExpressiveButton(
                        onClick = { onNewBattle() },
                        text = "New battle",
                        leadingIcon = Icons.Filled.Add,
                        style = ExpressiveButtonStyle.FilledTonal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
