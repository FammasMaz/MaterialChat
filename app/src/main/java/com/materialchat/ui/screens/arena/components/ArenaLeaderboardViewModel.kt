package com.materialchat.ui.screens.arena.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.ModelRating
import com.materialchat.domain.usecase.GetArenaLeaderboardUseCase
import com.materialchat.ui.components.LoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * UI state for the Arena Leaderboard screen.
 *
 * @property ratings All model ratings ordered by Elo descending
 * @property isLoading The first emission has not arrived yet
 * @property error Non-null when the ratings could not be observed; [ratings] is empty then
 */
data class ArenaLeaderboardUiState(
    val ratings: List<ModelRating> = emptyList(),
    val isLoading: Boolean = false,
    val error: LoadError? = null
)

/**
 * ViewModel for the Arena Leaderboard screen.
 *
 * Observes model ratings reactively and exposes them as a StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArenaLeaderboardViewModel @Inject constructor(
    private val getArenaLeaderboardUseCase: GetArenaLeaderboardUseCase
) : ViewModel() {

    /** Bumped by [retry]; each value re-subscribes to the use case from scratch. */
    private val loadAttempt = MutableStateFlow(0)

    val uiState: StateFlow<ArenaLeaderboardUiState> = loadAttempt
        .flatMapLatest {
            // The use-case call runs inside the builder so a synchronous throw is caught too.
            flow { emitAll(getArenaLeaderboardUseCase.observeRatings()) }
                .map { ratings -> ArenaLeaderboardUiState(ratings = ratings) }
                .onStart { emit(ArenaLeaderboardUiState(isLoading = true)) }
                .catch { emit(ArenaLeaderboardUiState(error = LoadError.Unavailable)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ArenaLeaderboardUiState(isLoading = true)
        )

    /** Re-observes the ratings after a load failure; the screen shows loading meanwhile. */
    fun retry() {
        loadAttempt.update { it + 1 }
    }
}
