package com.materialchat.ui.screens.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.InsightsTimeRange
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.usecase.ArenaContenderSpec
import com.materialchat.domain.usecase.ArenaVerdict
import com.materialchat.domain.usecase.GetArenaLeaderboardUseCase
import com.materialchat.domain.usecase.GetConversationInsightsUseCase
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Codenames shown during the blind phase. Stable per contender slot so cards
 * keep their identity while the scramble animates through the pool.
 */
private val CODENAMES = listOf(
    "Aurora", "Borealis", "Comet", "Drift", "Ember",
    "Flux", "Gale", "Halcyon", "Ion", "Juno"
)

/**
 * ViewModel for the Arena screen.
 *
 * Manages blind N-model battles: contenders are picked from usage-ranked
 * model lists, stream anonymously under codenames, and are revealed only
 * after the user votes.
 */
@HiltViewModel
class ArenaViewModel @Inject constructor(
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val runArenaBattleUseCase: RunArenaBattleUseCase,
    private val voteArenaBattleUseCase: VoteArenaBattleUseCase,
    private val getArenaLeaderboardUseCase: GetArenaLeaderboardUseCase,
    private val getConversationInsightsUseCase: GetConversationInsightsUseCase,
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
     * Loads available providers, personal model-usage ranking, and defaults.
     */
    private fun loadProviders() {
        viewModelScope.launch {
            try {
                val providers = manageProvidersUseCase.getProviders()
                if (providers.isEmpty()) {
                    _uiState.value = ArenaUiState.Error(
                        "No providers configured. Add a provider in Settings first."
                    )
                    return@launch
                }

                val activeProvider = manageProvidersUseCase.getActiveProvider()
                val defaultProviderId = activeProvider?.id ?: providers.first().id

                // Most-used models first, from the user's own chat history.
                val usageRanking = try {
                    getConversationInsightsUseCase(InsightsTimeRange.ALL_TIME)
                        .modelUsage
                        .sortedByDescending { it.count }
                        .map { it.modelName }
                } catch (_: Exception) {
                    emptyList()
                }

                _uiState.value = ArenaUiState.Ready(
                    providers = providers,
                    usageRanking = usageRanking,
                    pickerProviderId = defaultProviderId
                )

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
     * Switches which provider's models the picker browses.
     */
    fun setPickerProvider(providerId: String) {
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(pickerProviderId = providerId)
            else state
        }
        loadModelsForProvider(providerId)
    }

    /**
     * Toggles a model in/out of the battle roster (max 4 entrants).
     */
    fun toggleContender(model: AiModel, providerId: String) {
        _uiState.update { state ->
            if (state !is ArenaUiState.Ready) return@update state

            val existing = state.contenders.firstOrNull { it.modelName == model.id }
            if (existing != null) {
                if (state.isBattleRunning) return@update state
                state.copy(
                    contenders = state.contenders
                        .filterNot { it.modelName == model.id }
                        .mapIndexed { index, c -> c.copy(slot = index) }
                )
            } else {
                if (state.contenders.size >= MAX_CONTENDERS) return@update state
                val slot = state.contenders.size
                state.copy(
                    contenders = state.contenders + ContenderUi(
                        slot = slot,
                        providerId = providerId,
                        modelName = model.id,
                        codename = CODENAMES[slot % CODENAMES.size]
                    )
                )
            }
        }
    }

    /**
     * Starts an N-model battle.
     */
    fun startBattle() {
        val currentState = _uiState.value
        if (currentState !is ArenaUiState.Ready) return
        if (!currentState.canStartBattle) return

        val prompt = currentState.prompt.trim()
        val specs = currentState.contenders.map {
            ArenaContenderSpec(it.providerId, it.modelName)
        }

        // Re-anonymize EVERY run: fresh random codenames so identities can
        // never be correlated across battles via a remembered name.
        val freshNames = CODENAMES.shuffled()

        _uiState.update { state ->
            if (state is ArenaUiState.Ready) {
                state.copy(
                    contenders = state.contenders.mapIndexed { index, contender ->
                        contender.copy(
                            codename = freshNames[index % freshNames.size],
                            streamState = StreamingState.Starting
                        )
                    },
                    voted = false,
                    revealed = false,
                    battleId = null
                )
            } else state
        }

        battleJob = viewModelScope.launch(ioDispatcher) {
            try {
                runArenaBattleUseCase(
                    prompt = prompt,
                    contenders = specs
                ).collect { progress ->
                    _uiState.update { state ->
                        if (state is ArenaUiState.Ready) {
                            state.copy(
                                contenders = state.contenders.mapIndexed { slot, contender ->
                                    contender.copy(
                                        streamState = progress.states.getOrElse(slot) { contender.streamState }
                                    )
                                },
                                battleId = progress.battleId
                            )
                        } else state
                    }
                }

                _events.emit(ArenaEvent.BattleComplete)
            } catch (e: Exception) {
                _events.emit(ArenaEvent.ShowSnackbar("Battle failed: ${e.message}"))
                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) {
                        state.copy(
                            contenders = state.contenders.map {
                                it.copy(streamState = StreamingState.Idle)
                            },
                            battleId = null
                        )
                    } else state
                }
            }
        }
    }

    /**
     * Votes on the current battle; names reveal once the vote is recorded.
     */
    fun vote(verdict: ArenaVerdict) {
        val currentState = _uiState.value
        if (currentState !is ArenaUiState.Ready) return
        val battleId = currentState.battleId ?: return
        if (currentState.voted) return

        _uiState.update { state ->
            if (state is ArenaUiState.Ready) state.copy(voted = true)
            else state
        }

        viewModelScope.launch {
            try {
                voteArenaBattleUseCase(battleId, verdict)
                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) state.copy(revealed = true)
                    else state
                }
                _events.emit(ArenaEvent.ShowSnackbar("Vote recorded — identities revealed!"))
            } catch (e: Exception) {
                _events.emit(ArenaEvent.ShowSnackbar("Vote failed: ${e.message}"))
                _uiState.update { state ->
                    if (state is ArenaUiState.Ready) state.copy(voted = false)
                    else state
                }
            }
        }
    }

    /**
     * Resets the arena for another round with the current roster.
     */
    fun newBattle() {
        val freshNames = CODENAMES.shuffled()
        _uiState.update { state ->
            if (state is ArenaUiState.Ready) {
                state.copy(
                    contenders = state.contenders.mapIndexed { index, contender ->
                        contender.copy(
                            codename = freshNames[index % freshNames.size],
                            streamState = StreamingState.Idle
                        )
                    },
                    battleId = null,
                    voted = false,
                    revealed = false
                )
            } else state
        }
    }

    companion object {
        const val MAX_CONTENDERS = 4
    }
}
