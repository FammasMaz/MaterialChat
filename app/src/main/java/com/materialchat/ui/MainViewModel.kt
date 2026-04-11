package com.materialchat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.Persona
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.ManagePersonasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel for actions accessible from the floating toolbar.
 * Handles conversation creation (New Chat FAB) independent of screen scope.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val createConversationUseCase: CreateConversationUseCase,
    private val managePersonasUseCase: ManagePersonasUseCase
) : ViewModel() {

    private val _navigateToChat = MutableSharedFlow<String>()
    val navigateToChat = _navigateToChat.asSharedFlow()

    private val _showError = MutableSharedFlow<String>()
    val showError = _showError.asSharedFlow()

    /** Whether the persona picker bottom sheet is visible. */
    private val _showPersonaPicker = MutableStateFlow(false)
    val showPersonaPicker: StateFlow<Boolean> = _showPersonaPicker.asStateFlow()

    /** Reactive list of all personas for the picker. */
    val personas: Flow<List<Persona>> = managePersonasUseCase.observeAllPersonas()

    init {
    }

    fun showPersonaPicker() { _showPersonaPicker.value = true }
    fun hidePersonaPicker() { _showPersonaPicker.value = false }

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

    fun createTemporaryConversation() {
        viewModelScope.launch {
            try {
                val conversationId = createConversationUseCase.temporary()
                _navigateToChat.emit(conversationId)
            } catch (e: IllegalStateException) {
                _showError.emit("No provider configured")
            } catch (e: Exception) {
                _showError.emit(e.message ?: "Failed to create temporary conversation")
            }
        }
    }

    fun createNewConversationWithPersona(personaId: String) {
        viewModelScope.launch {
            try {
                val conversationId = createConversationUseCase.withPersona(personaId)
                _navigateToChat.emit(conversationId)
            } catch (e: IllegalStateException) {
                _showError.emit("No provider configured")
            } catch (e: Exception) {
                _showError.emit(e.message ?: "Failed to create conversation")
            }
            _showPersonaPicker.value = false
        }
    }
}
