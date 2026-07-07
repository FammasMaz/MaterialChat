package com.materialchat.ui.screens.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val memoryQueryTokenizer = Regex("\\s+")

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<MemoriesUiState> = combine(
        memoryRepository.observeActiveMemories(),
        searchQuery
    ) { memories, query ->
        val filtered = if (query.isBlank()) {
            memories
        } else {
            val tokens = query.lowercase().split(memoryQueryTokenizer).filter { it.isNotBlank() }
            memories.filter { memory ->
                val searchable = "${memory.content} ${memory.kind.displayName}".lowercase()
                tokens.all { token -> searchable.contains(token) }
            }
        }
        MemoriesUiState(
            memories = memories,
            filteredMemories = filtered,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoriesUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
        }
    }

    fun deleteAllMemories() {
        viewModelScope.launch {
            memoryRepository.deleteAllMemories()
        }
    }
}
