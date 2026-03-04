package com.materialchat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel for actions accessible from the floating toolbar.
 * Handles conversation creation (New Chat FAB) independent of screen scope.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val createConversationUseCase: CreateConversationUseCase,
    connectGatewayUseCase: ConnectGatewayUseCase
) : ViewModel() {

    /** Whether the OpenClaw Gateway is currently connected. */
    val isOpenClawConnected: StateFlow<Boolean> = connectGatewayUseCase.connectionState
        .map { it is GatewayConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _navigateToChat = MutableSharedFlow<String>()
    val navigateToChat = _navigateToChat.asSharedFlow()

    private val _showError = MutableSharedFlow<String>()
    val showError = _showError.asSharedFlow()

    fun createNewConversation() {
        viewModelScope.launch {
            try {
                val conversationId = createConversationUseCase()
                _navigateToChat.emit(conversationId)
            } catch (e: IllegalStateException) {
                _showError.emit("No provider configured")
            } catch (e: Exception) {
                _showError.emit(e.message ?: "Failed to create conversation")
            }
        }
    }
}
