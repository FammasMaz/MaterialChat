package com.materialchat.ui.screens.arena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialchat.ui.screens.arena.components.ArenaBattleView
import com.materialchat.ui.screens.arena.components.ArenaModelPicker
import com.materialchat.ui.screens.arena.components.ArenaPromptInput
import com.materialchat.ui.screens.arena.components.ArenaVotingBar

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArenaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.SportsKabaddi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Model Arena")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToLeaderboard) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leaderboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        when (val state = uiState) {
            is ArenaUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading providers...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is ArenaUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is ArenaUiState.Ready -> {
                ArenaReadyContent(
                    state = state,
                    onPromptChanged = viewModel::updatePrompt,
                    onLeftProviderSelected = viewModel::selectLeftProvider,
                    onRightProviderSelected = viewModel::selectRightProvider,
                    onLeftModelSelected = viewModel::selectLeftModel,
                    onRightModelSelected = viewModel::selectRightModel,
                    onStartBattle = viewModel::startBattle,
                    onVote = viewModel::vote,
                    onNewBattle = viewModel::newBattle,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ArenaReadyContent(
    state: ArenaUiState.Ready,
    onPromptChanged: (String) -> Unit,
    onLeftProviderSelected: (String) -> Unit,
    onRightProviderSelected: (String) -> Unit,
    onLeftModelSelected: (com.materialchat.domain.model.AiModel) -> Unit,
    onRightModelSelected: (com.materialchat.domain.model.AiModel) -> Unit,
    onStartBattle: () -> Unit,
    onVote: (com.materialchat.domain.model.ArenaVote) -> Unit,
    onNewBattle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 300f
                )
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Model picker
        ArenaModelPicker(
            providers = state.providers,
            leftProviderId = state.leftProviderId,
            rightProviderId = state.rightProviderId,
            leftModelName = state.leftModelName,
            rightModelName = state.rightModelName,
            leftModels = state.leftModels,
            rightModels = state.rightModels,
            onLeftProviderSelected = onLeftProviderSelected,
            onRightProviderSelected = onRightProviderSelected,
            onLeftModelSelected = onLeftModelSelected,
            onRightModelSelected = onRightModelSelected,
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
            enter = fadeIn(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)
            ) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                initialOffsetY = { it / 4 }
            ),
            exit = fadeOut() + slideOutVertically()
        ) {
            ArenaBattleView(
                leftModelName = state.leftModelName ?: "Model A",
                rightModelName = state.rightModelName ?: "Model B",
                leftContent = state.leftContent,
                rightContent = state.rightContent,
                leftStreamingState = state.leftStreamingState,
                rightStreamingState = state.rightStreamingState,
                modifier = Modifier.height(400.dp)
            )
        }

        // Voting bar (visible when battle is complete and not yet voted)
        AnimatedVisibility(
            visible = state.isBattleComplete,
            enter = fadeIn(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)
            ) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut()
        ) {
            Column {
                if (!state.voted) {
                    Text(
                        text = "Which response is better?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                ArenaVotingBar(
                    onVote = onVote,
                    enabled = state.isBattleComplete,
                    voted = state.voted
                )

                // New battle button after voting
                AnimatedVisibility(
                    visible = state.voted,
                    enter = fadeIn() + slideInVertically(
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    TextButton(
                        onClick = onNewBattle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Battle")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
