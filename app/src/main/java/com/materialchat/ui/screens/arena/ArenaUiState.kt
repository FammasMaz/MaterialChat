package com.materialchat.ui.screens.arena

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState

/**
 * UI state for the Arena screen.
 */
sealed interface ArenaUiState {

    /**
     * Loading state while providers are being fetched.
     */
    data object Loading : ArenaUiState

    /**
     * Ready state with all arena configuration and battle data.
     *
     * @property providers Available AI providers
     * @property availableModels Models available per provider (providerId -> models)
     * @property leftProviderId Selected provider for the left panel
     * @property rightProviderId Selected provider for the right panel
     * @property leftModelName Selected model for the left panel
     * @property rightModelName Selected model for the right panel
     * @property prompt The user prompt text
     * @property leftStreamingState Streaming state for the left model
     * @property rightStreamingState Streaming state for the right model
     * @property battleId The current battle ID, set once streaming begins
     * @property voted Whether the user has voted on the current battle
     * @property isLoadingModels Whether models are currently being fetched
     */
    data class Ready(
        val providers: List<Provider> = emptyList(),
        val availableModels: Map<String, List<AiModel>> = emptyMap(),
        val leftProviderId: String? = null,
        val rightProviderId: String? = null,
        val leftModelName: String? = null,
        val rightModelName: String? = null,
        val prompt: String = "",
        val leftStreamingState: StreamingState = StreamingState.Idle,
        val rightStreamingState: StreamingState = StreamingState.Idle,
        val battleId: String? = null,
        val voted: Boolean = false,
        val isLoadingModels: Boolean = false
    ) : ArenaUiState {

        /**
         * Whether a battle is currently running (either model is streaming).
         */
        val isBattleRunning: Boolean
            get() = leftStreamingState is StreamingState.Starting ||
                    leftStreamingState is StreamingState.Streaming ||
                    rightStreamingState is StreamingState.Starting ||
                    rightStreamingState is StreamingState.Streaming

        /**
         * Whether both models have completed their responses.
         */
        val isBattleComplete: Boolean
            get() = battleId != null &&
                    (leftStreamingState is StreamingState.Completed ||
                            leftStreamingState is StreamingState.Error) &&
                    (rightStreamingState is StreamingState.Completed ||
                            rightStreamingState is StreamingState.Error)

        /**
         * Whether the user can start a new battle.
         */
        val canStartBattle: Boolean
            get() = prompt.isNotBlank() &&
                    leftModelName != null &&
                    rightModelName != null &&
                    leftProviderId != null &&
                    rightProviderId != null &&
                    !isBattleRunning

        /**
         * The accumulated content from the left model.
         */
        val leftContent: String
            get() = when (leftStreamingState) {
                is StreamingState.Streaming -> leftStreamingState.content
                is StreamingState.Completed -> leftStreamingState.finalContent
                is StreamingState.Error -> leftStreamingState.partialContent
                    ?: "Error: ${leftStreamingState.error.message}"
                else -> ""
            }

        /**
         * The accumulated content from the right model.
         */
        val rightContent: String
            get() = when (rightStreamingState) {
                is StreamingState.Streaming -> rightStreamingState.content
                is StreamingState.Completed -> rightStreamingState.finalContent
                is StreamingState.Error -> rightStreamingState.partialContent
                    ?: "Error: ${rightStreamingState.error.message}"
                else -> ""
            }

        /**
         * Models available for the left provider.
         */
        val leftModels: List<AiModel>
            get() = leftProviderId?.let { availableModels[it] } ?: emptyList()

        /**
         * Models available for the right provider.
         */
        val rightModels: List<AiModel>
            get() = rightProviderId?.let { availableModels[it] } ?: emptyList()
    }

    /**
     * Error state when loading fails.
     */
    data class Error(val message: String) : ArenaUiState
}

/**
 * One-time events for the Arena screen.
 */
sealed interface ArenaEvent {
    data class ShowSnackbar(val message: String) : ArenaEvent
    data object NavigateToLeaderboard : ArenaEvent
    data object BattleComplete : ArenaEvent
}
