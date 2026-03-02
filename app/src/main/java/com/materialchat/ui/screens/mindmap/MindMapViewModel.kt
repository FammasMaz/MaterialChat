package com.materialchat.ui.screens.mindmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.usecase.GetConversationTreeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Mind Map (Branch Visualizer) screen.
 *
 * Loads the full conversation tree rooted at the conversation identified
 * by the [SavedStateHandle] key `"conversationId"` and exposes it as a
 * [MindMapUiState]. Supports selecting individual nodes for preview.
 */
@HiltViewModel
class MindMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConversationTreeUseCase: GetConversationTreeUseCase
) : ViewModel() {

    private val conversationId: String =
        checkNotNull(savedStateHandle["conversationId"]) {
            "conversationId is required to display the mind map"
        }

    private val _uiState = MutableStateFlow<MindMapUiState>(MindMapUiState.Loading)
    val uiState: StateFlow<MindMapUiState> = _uiState.asStateFlow()

    init {
        loadTree()
    }

    /**
     * Selects a node in the mind map by its ID.
     * Only applies when the current state is [MindMapUiState.Success].
     */
    fun selectNode(nodeId: String) {
        _uiState.update { current ->
            if (current is MindMapUiState.Success) {
                current.copy(selectedNodeId = nodeId)
            } else {
                current
            }
        }
    }

    /**
     * Loads the conversation tree from the use case.
     */
    private fun loadTree() {
        viewModelScope.launch {
            _uiState.value = MindMapUiState.Loading
            try {
                val tree = getConversationTreeUseCase(conversationId)
                if (tree != null) {
                    _uiState.value = MindMapUiState.Success(
                        tree = tree,
                        selectedNodeId = conversationId
                    )
                } else {
                    _uiState.value = MindMapUiState.Error(
                        "Could not build conversation tree"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MindMapUiState.Error(
                    e.message ?: "Failed to load conversation tree"
                )
            }
        }
    }
}
