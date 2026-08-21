package com.materialchat.ui.screens.arena

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState

/**
 * One anonymized entrant in an N-model arena battle.
 *
 * The model name is carried here for orchestration, but the UI must only
 * render [displayName] until the battle is voted and revealed.
 */
data class ContenderUi(
    val slot: Int,
    val providerId: String,
    val modelName: String,
    val streamState: StreamingState = StreamingState.Idle,
    /** Stable codename shown during the blind phase (e.g. "Aurora"). */
    val codename: String = ""
) {
    val content: String
        get() = when (streamState) {
            is StreamingState.Streaming -> streamState.content
            is StreamingState.Completed -> streamState.finalContent
            is StreamingState.Error -> streamState.partialContent
                ?: "Error: ${streamState.error.message}"
            else -> ""
        }

    val isFinished: Boolean
        get() = streamState is StreamingState.Completed || streamState is StreamingState.Error
}

/**
 * UI state for the Arena screen.
 */
sealed interface ArenaUiState {

    data object Loading : ArenaUiState

    /**
     * Ready state with all arena configuration and battle data.
     */
    data class Ready(
        val providers: List<Provider> = emptyList(),
        val availableModels: Map<String, List<AiModel>> = emptyMap(),
        /** Model names ranked by personal usage, most used first. */
        val usageRanking: List<String> = emptyList(),
        /** Provider whose model list is being browsed in the picker. */
        val pickerProviderId: String? = null,
        val contenders: List<ContenderUi> = emptyList(),
        val prompt: String = "",
        val battleId: String? = null,
        val voted: Boolean = false,
        /** True once names have been revealed (after voting). */
        val revealed: Boolean = false,
        val isLoadingModels: Boolean = false
    ) : ArenaUiState {

        val isBattleRunning: Boolean
            get() = contenders.any {
                it.streamState is StreamingState.Starting ||
                        it.streamState is StreamingState.Streaming
            }

        val isBattleComplete: Boolean
            get() = battleId != null && contenders.isNotEmpty() && contenders.all { it.isFinished }

        val canStartBattle: Boolean
            get() = prompt.isNotBlank() &&
                    contenders.size >= 2 &&
                    !isBattleRunning

        val pickerModels: List<AiModel>
            get() = pickerProviderId?.let { availableModels[it] } ?: emptyList()
    }

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
