package com.materialchat.ui.screens.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.Persona
import com.materialchat.domain.usecase.ManagePersonasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Persona Studio screen.
 *
 * Manages persona listing, creation, editing, and deletion.
 */
@HiltViewModel
class PersonaStudioViewModel @Inject constructor(
    private val managePersonasUseCase: ManagePersonasUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonaStudioUiState>(PersonaStudioUiState.Loading)
    val uiState: StateFlow<PersonaStudioUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PersonaStudioEvent>()
    val events: SharedFlow<PersonaStudioEvent> = _events.asSharedFlow()

    init {
        observePersonas()
    }

    /**
     * Starts observing all personas from the repository.
     */
    private fun observePersonas() {
        viewModelScope.launch {
            managePersonasUseCase.observeAllPersonas()
                .catch { e ->
                    _uiState.value = PersonaStudioUiState.Error(
                        message = e.message ?: "Failed to load personas"
                    )
                }
                .collect { personas ->
                    val current = _uiState.value
                    _uiState.value = if (current is PersonaStudioUiState.Success) {
                        current.copy(personas = personas)
                    } else {
                        PersonaStudioUiState.Success(personas = personas)
                    }
                }
        }
    }

    /**
     * Opens the editor sheet for creating a new persona.
     */
    fun showCreateEditor() {
        val current = _uiState.value
        if (current is PersonaStudioUiState.Success) {
            _uiState.value = current.copy(
                editingPersona = null,
                showEditorSheet = true
            )
        }
    }

    /**
     * Opens the editor sheet for editing an existing persona.
     */
    fun showEditEditor(persona: Persona) {
        val current = _uiState.value
        if (current is PersonaStudioUiState.Success) {
            _uiState.value = current.copy(
                editingPersona = persona,
                showEditorSheet = true
            )
        }
    }

    /**
     * Hides the editor sheet.
     */
    fun hideEditor() {
        val current = _uiState.value
        if (current is PersonaStudioUiState.Success) {
            _uiState.value = current.copy(
                editingPersona = null,
                showEditorSheet = false
            )
        }
    }

    /**
     * Saves a persona (creates new or updates existing).
     */
    fun savePersona(persona: Persona) {
        viewModelScope.launch {
            try {
                val current = _uiState.value as? PersonaStudioUiState.Success ?: return@launch
                val isEditing = current.editingPersona != null

                if (isEditing) {
                    managePersonasUseCase.updatePersona(persona)
                } else {
                    managePersonasUseCase.createPersona(persona)
                }

                _uiState.value = current.copy(
                    editingPersona = null,
                    showEditorSheet = false
                )

                _events.emit(
                    PersonaStudioEvent.ShowSnackbar(
                        if (isEditing) "Persona updated" else "Persona created"
                    )
                )
            } catch (e: Exception) {
                _events.emit(
                    PersonaStudioEvent.ShowSnackbar(
                        "Failed to save persona: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Deletes a persona by its ID.
     */
    fun deletePersona(personaId: String) {
        viewModelScope.launch {
            try {
                managePersonasUseCase.deletePersona(personaId)
                _events.emit(PersonaStudioEvent.ShowSnackbar("Persona deleted"))
            } catch (e: Exception) {
                _events.emit(
                    PersonaStudioEvent.ShowSnackbar(
                        "Failed to delete persona: ${e.message}"
                    )
                )
            }
        }
    }
}
