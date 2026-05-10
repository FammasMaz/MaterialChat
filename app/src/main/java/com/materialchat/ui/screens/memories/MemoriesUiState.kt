package com.materialchat.ui.screens.memories

import com.materialchat.domain.model.Memory

data class MemoriesUiState(
    val memories: List<Memory> = emptyList(),
    val filteredMemories: List<Memory> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)
