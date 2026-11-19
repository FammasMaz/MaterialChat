package com.materialchat.ui.screens.memories

import com.materialchat.domain.model.Memory
import com.materialchat.ui.components.LoadError

data class MemoriesUiState(
    val memories: List<Memory> = emptyList(),
    val filteredMemories: List<Memory> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    /** Non-null when the memory store could not be observed; the lists are empty then. */
    val error: LoadError? = null
)
