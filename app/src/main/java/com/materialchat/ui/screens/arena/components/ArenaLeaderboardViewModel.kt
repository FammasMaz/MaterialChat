package com.materialchat.ui.screens.arena.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.ModelRating
import com.materialchat.domain.usecase.GetArenaLeaderboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Arena Leaderboard screen.
 *
 * Observes model ratings reactively and exposes them as a StateFlow.
 */
@HiltViewModel
class ArenaLeaderboardViewModel @Inject constructor(
    getArenaLeaderboardUseCase: GetArenaLeaderboardUseCase
) : ViewModel() {

    /**
     * All model ratings ordered by ELO descending.
     */
    val ratings: StateFlow<List<ModelRating>> = getArenaLeaderboardUseCase
        .observeRatings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
