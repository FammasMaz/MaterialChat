package com.materialchat.ui.screens.insights

import com.materialchat.domain.model.InsightsData

/**
 * UI state for the Insights screen.
 *
 * Represents the three possible states: loading, success with data,
 * or error with a user-facing message.
 */
sealed interface InsightsUiState {

    /**
     * Data is being loaded from the database.
     */
    data object Loading : InsightsUiState

    /**
     * Data loaded successfully.
     *
     * @property data The aggregated insights data to display
     */
    data class Success(val data: InsightsData) : InsightsUiState

    /**
     * An error occurred while loading data.
     *
     * @property message User-facing error message
     */
    data class Error(val message: String) : InsightsUiState
}
