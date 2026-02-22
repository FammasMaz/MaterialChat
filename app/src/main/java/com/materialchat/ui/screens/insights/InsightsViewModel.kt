package com.materialchat.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.InsightsTimeRange
import com.materialchat.domain.usecase.GetConversationInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Insights screen.
 *
 * Manages loading of conversation intelligence data and
 * time range filtering. Data is refreshed on init and
 * whenever the selected time range changes.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val getConversationInsightsUseCase: GetConversationInsightsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(InsightsTimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<InsightsTimeRange> = _selectedTimeRange.asStateFlow()

    init {
        loadInsights()
    }

    /**
     * Updates the selected time range and reloads insights data.
     */
    fun selectTimeRange(timeRange: InsightsTimeRange) {
        _selectedTimeRange.value = timeRange
        loadInsights()
    }

    /**
     * Loads insights data for the currently selected time range.
     */
    fun loadInsights() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = InsightsUiState.Loading
            try {
                val data = getConversationInsightsUseCase(_selectedTimeRange.value)
                _uiState.value = InsightsUiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = InsightsUiState.Error(
                    e.message ?: "Failed to load insights"
                )
            }
        }
    }
}
