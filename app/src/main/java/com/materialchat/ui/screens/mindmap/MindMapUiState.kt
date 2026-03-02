package com.materialchat.ui.screens.mindmap

import com.materialchat.domain.model.ConversationTreeNode

/**
 * UI state for the Mind Map screen.
 *
 * Represents the three possible states: loading the conversation tree,
 * success with the tree data and optional selection, or error with a
 * user-facing message.
 */
sealed interface MindMapUiState {

    /**
     * The conversation tree is being loaded.
     */
    data object Loading : MindMapUiState

    /**
     * The conversation tree loaded successfully.
     *
     * @property tree The root node of the conversation tree
     * @property selectedNodeId The currently selected node ID, if any
     */
    data class Success(
        val tree: ConversationTreeNode,
        val selectedNodeId: String? = null
    ) : MindMapUiState

    /**
     * An error occurred while loading the conversation tree.
     *
     * @property message User-facing error message
     */
    data class Error(val message: String) : MindMapUiState
}
