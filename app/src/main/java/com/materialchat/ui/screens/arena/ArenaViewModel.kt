package com.materialchat.ui.screens.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.ArenaVote
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.usecase.GetArenaLeaderboardUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RunArenaBattleUseCase
import com.materialchat.domain.usecase.VoteArenaBattleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Arena screen.
 *
 * Manages model selection, battle execution, voting, and leaderboard navigation.
 */
@HiltViewModel
class ArenaViewModel @Inject constructor(
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val runArenaBattleUseCase: RunArenaBattleUseCase,
    private val voteArenaBattleUseCase: VoteArenaBattleUseCase,
    private val getArenaLeaderboardUseCase: GetArenaLeaderboardUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArenaUiState>(ArenaUiState.Loading)
    val uiState: StateFlow<ArenaUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ArenaEvent>()
    val events: SharedFlow<ArenaEvent> = _events.asSharedFlow()

    private var battleJob: Job? = null

    init {
        loadProviders()
    }

    /**
     * Loads available providers and sets initial selections.
     */
    private fun loadProviders() {
        viewModelScope.launch {
            try {
                val providers = manageProvidersUseCase.getProviders()
                if (providers.isEmpty()) {
                    _uiState.value = ArenaUiState.Error("No providers configured. Add a provider in Settings first.")
                    return@launch
                }

                val activeProvider = manageProvidersUseCase.getActiveProvider()
                val defaultProviderId = activeProvider?.id ?: providers.first().id

                _uiState.value = ArenaUiState.Ready(
                    providers = providers,
                    leftProviderId = defaultProviderId,
                    rightProviderId = defaultProviderId
                )

                // Auto-load models for the default provider
                loadModelsForProvider(defaultProviderId)
            } catch (e: Exception) {
                _uiState.value = ArenaUiState.Error(
                    message = e.message ?: "Failed to load providers"
                )
            }
        }
    }

    /**
     * Loads models for a given provider and caches them.
     */
    private fun loadModelsForProvider(providerId: String) {
        val currentState = _uiState.value
        if (currentState !is ArenaUiState.Ready) return

        // Skip if already loaded
        if (currentState.availableModels.containsKey(providerId)) return

        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(isLoadingModels = true)
            else state
        }

        viewModelScope.launch {
            try {
                val result = manageProvidersUseCase.fetchModels(providerId)
                val models = result.getOrElse { emptyList() }

                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) {
                        state.copy(
                            availableModels = state.availableModels + (providerId to models),
                            isLoadingModels = false
                        )
                    } else state
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) state.copy(isLoadingModels = false)
                    else state
                }
                _events.emit(ArenaEvent.ShowSnackbar("Failed to load models: ${e.message}"))
            }
        }
    }

    /**
     * Updates the prompt text.
     */
    fun updatePrompt(prompt: String) {
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(prompt = prompt)
            else state
        }
    }

    /**
     * Selects the left provider and loads its models.
     */
    fun selectLeftProvider(providerId: String) {
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) {
                state.copy(leftProviderId = providerId, leftModelName = null)
            } else state
        }
        loadModelsForProvider(providerId)
    }

    /**
     * Selects the right provider and loads its models.
     */
    fun selectRightProvider(providerId: String) {
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) {
                state.copy(rightProviderId = providerId, rightModelName = null)
            } else state
        }
        loadModelsForProvider(providerId)
    }

    /**
     * Selects the left model.
     */
    fun selectLeftModel(model: AiModel) {
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(leftModelName = model.id)
            else state
        }
    }

    /**
     * Selects the right model.
     */
    fun selectRightModel(model: AiModel) {
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(rightModelName = model.id)
            else state
        }
    }

    /**
     * Starts a battle between the two selected models.
     */
    fun startBattle() {
        val currentState = _uiState.value
        if (currentState !is ArenaUiState.Ready) return
        if (!currentState.canStartBattle) return

        val prompt = currentState.prompt.trim()
        val leftModel = currentState.leftModelName!!
        val leftProvider = currentState.leftProviderId!!
        val rightModel = currentState.rightModelName!!
        val rightProvider = currentState.rightProviderId!!

        // Reset streaming states
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) {
                state.copy(
                    leftStreamingState = StreamingState.Starting,
                    rightStreamingState = StreamingState.Starting,
                    voted = false,
                    battleId = null
                )
            } else state
        }

        battleJob = viewModelScope.launch(ioDispatcher) {
            try {
                runArenaBattleUseCase(
                    prompt = prompt,
                    leftModelName = leftModel,
                    leftProviderId = leftProvider,
                    rightModelName = rightModel,
                    rightProviderId = rightProvider
                ).collect { progress ->
                    _uiState.update { state ->
                        if (state is ArenaUiState.Ready) {
                            state.copy(
                                leftStreamingState = progress.leftState,
                                rightStreamingState = progress.rightState,
                                battleId = progress.battleId
                            )
                        } else state
                    }
                }

                _events.emit(ArenaEvent.BattleComplete)
            } catch (e: Exception) {
                _events.emit(ArenaEvent.ShowSnackbar(
                    "Battle failed: ${e.message}"
                ))
                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) {
                        state.copy(
                            leftStreamingState = StreamingState.Idle,
                            rightStreamingState = StreamingState.Idle,
                            battleId = null
                        )
                    } else state
                }
            }
        }
    }

    /**
     * Votes on the current battle and updates ELO ratings.
     */
    fun vote(vote: ArenaVote) {
        val currentState = _uiState.value
        if (currentState !is ArenaUiState.Ready) return
        if (currentState.battleId == null) return
        if (currentState.voted) return

        val battleId = currentState.battleId

        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(voted = true)
            else state
        }

        viewModelScope.launch {
            try {
                voteArenaBattleUseCase(battleId, vote)
                _events.emit(ArenaEvent.ShowSnackbar("Vote recorded!"))
            } catch (e: Exception) {
                _events.emit(ArenaEvent.ShowSnackbar(
                    "Failed to record vote: ${e.message}"
                ))
                // Revert voted state on error
                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) state.copy(voted = false)
                    else state
                }
            }
        }
    }

    /**
     * Resets the arena for a new battle.
     */
    fun newBattle() {
        battleJob?.cancel()
        battleJob = null

        _uiState.update { state ->
            if (state is ArenaUiState.Ready) {
                state.copy(
                    prompt = "",
                    leftStreamingState = StreamingState.Idle,
                    rightStreamingState = StreamingState.Idle,
                    battleId = null,
                    voted = false
                )
            } else state
        }
    }

    override fun onCleared() {
        super.onCleared()
        battleJob?.cancel()
    }
}
