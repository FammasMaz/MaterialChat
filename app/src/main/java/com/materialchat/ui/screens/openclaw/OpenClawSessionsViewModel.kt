package com.materialchat.ui.screens.openclaw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawSessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel for the OpenClaw Sessions screen.
 *
 * Manages the list of active sessions, supports refresh and deletion.
 */
@HiltViewModel
class OpenClawSessionsViewModel @Inject constructor(
    private val manageOpenClawSessionsUseCase: ManageOpenClawSessionsUseCase,
    private val connectGatewayUseCase: ConnectGatewayUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<OpenClawSessionsUiState>(OpenClawSessionsUiState.Loading)
    val uiState: StateFlow<OpenClawSessionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OpenClawSessionsEvent>()
    val events: SharedFlow<OpenClawSessionsEvent> = _events.asSharedFlow()

    init {
        loadSessions()
    }

    /**
     * Loads sessions from the gateway.
     */
    fun loadSessions() {
        if (!connectGatewayUseCase.isConnected()) {
            _uiState.value = OpenClawSessionsUiState.Error("Not connected to gateway")
            return
        }

        viewModelScope.launch {
            try {
                val sessions = manageOpenClawSessionsUseCase.listSessions()
                _uiState.value = if (sessions.isEmpty()) {
                    OpenClawSessionsUiState.Empty
                } else {
                    OpenClawSessionsUiState.Success(sessions = sessions)
                }
            } catch (e: Exception) {
                _uiState.value = OpenClawSessionsUiState.Error(
                    message = e.message ?: "Failed to load sessions"
                )
            }
        }
    }

    /**
     * Deletes a session by its key.
     */
    fun deleteSession(sessionKey: String) {
        viewModelScope.launch {
            try {
                manageOpenClawSessionsUseCase.deleteSession(sessionKey)
                _events.emit(
                    OpenClawSessionsEvent.ShowSnackbar("Session deleted")
                )
                // Reload the list
                refresh()
            } catch (e: Exception) {
                _events.emit(
                    OpenClawSessionsEvent.ShowSnackbar(
                        "Failed to delete session: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Refreshes the session list.
     */
    fun refresh() {
        val currentState = _uiState.value
        if (currentState is OpenClawSessionsUiState.Success) {
            _uiState.update { state ->
                if (state is OpenClawSessionsUiState.Success) {
                    state.copy(isRefreshing = true)
                } else state
            }
        }

        viewModelScope.launch {
            try {
                val sessions = manageOpenClawSessionsUseCase.listSessions()
                _uiState.value = if (sessions.isEmpty()) {
                    OpenClawSessionsUiState.Empty
                } else {
                    OpenClawSessionsUiState.Success(
                        sessions = sessions,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is OpenClawSessionsUiState.Success) {
                        state.copy(isRefreshing = false)
                    } else state
                }
                _events.emit(
                    OpenClawSessionsEvent.ShowSnackbar(
                        "Failed to refresh: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Navigates to a session's chat history.
     */
    fun openSession(sessionKey: String) {
        viewModelScope.launch {
            _events.emit(OpenClawSessionsEvent.NavigateToChat(sessionKey))
        }
    }
}
