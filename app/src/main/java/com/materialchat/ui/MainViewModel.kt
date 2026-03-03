package com.materialchat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.usecase.CreateConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel for actions accessible from the floating toolbar.
 * Handles conversation creation (New Chat FAB) independent of screen scope.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val createConversationUseCase: CreateConversationUseCase
) : ViewModel() {

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
