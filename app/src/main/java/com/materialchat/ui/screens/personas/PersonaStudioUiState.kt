package com.materialchat.ui.screens.personas

import com.materialchat.domain.model.Persona

/**
 * UI state for the Persona Studio screen.
 */
sealed interface PersonaStudioUiState {

    /**
     * Loading state while personas are being fetched.
     */
    data object Loading : PersonaStudioUiState

    /**
     * Success state with the list of personas.
     *
     * @property personas All available personas (built-ins first)
     * @property editingPersona The persona currently being edited, or null for create mode
     * @property showEditorSheet Whether the editor bottom sheet is visible
     */
    data class Success(
        val personas: List<Persona> = emptyList(),
        val editingPersona: Persona? = null,
        val showEditorSheet: Boolean = false
    ) : PersonaStudioUiState

    /**
     * Error state when something goes wrong.
     */
    data class Error(val message: String) : PersonaStudioUiState
}

/**
 * One-time events emitted by the Persona Studio ViewModel.
 */
sealed interface PersonaStudioEvent {

    /**
     * Show a snackbar message.
     */
    data class ShowSnackbar(val message: String) : PersonaStudioEvent
}
