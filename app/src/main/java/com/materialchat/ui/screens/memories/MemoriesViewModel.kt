package com.materialchat.ui.screens.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.Memory
import com.materialchat.domain.repository.MemoryRepository
import com.materialchat.ui.components.LoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.materialchat.ui.theme.CopyRules

private val memoryQueryTokenizer = Regex("\\s+")

/** One observation attempt of the memory store: `null` while loading, then a result. */
private typealias MemoriesLoad = Result<List<Memory>>?

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")

    /** Failures of best-effort actions (delete) that the list state cannot carry. */
    private val _events = MutableSharedFlow<MemoriesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MemoriesEvent> = _events.asSharedFlow()

    /** Bumped by [retry]; each value re-subscribes to the repository from scratch. */
    private val loadAttempt = MutableStateFlow(0)

    private val memoriesLoad: Flow<MemoriesLoad> = loadAttempt.flatMapLatest {
        // The repository call runs inside the builder so a synchronous throw is caught too.
        flow { emitAll(memoryRepository.observeActiveMemories()) }
            .map<List<Memory>, MemoriesLoad> { Result.success(it) }
            .onStart { emit(null) }
            .catch { emit(Result.failure(it)) }
    }

    val uiState: StateFlow<MemoriesUiState> = combine(
        memoriesLoad,
        searchQuery
    ) { load, query ->
        when {
            load == null -> MemoriesUiState(searchQuery = query, isLoading = true)
            load.isFailure -> MemoriesUiState(
                searchQuery = query,
                isLoading = false,
                error = LoadError.Unavailable
            )
            else -> {
                val memories = load.getOrThrow()
                MemoriesUiState(
                    memories = memories,
                    filteredMemories = filter(memories, query),
                    searchQuery = query,
                    isLoading = false
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoriesUiState()
    )

    private fun filter(memories: List<Memory>, query: String): List<Memory> {
        if (query.isBlank()) return memories
        val tokens = query.lowercase().split(memoryQueryTokenizer).filter { it.isNotBlank() }
        return memories.filter { memory ->
            val searchable = "${memory.content} ${memory.kind.displayName}".lowercase()
            tokens.all { token -> searchable.contains(token) }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Re-observes the memory store after a load failure; the screen shows loading meanwhile. */
    fun retry() {
        loadAttempt.update { it + 1 }
    }

    /** A repository throw here used to escape viewModelScope and crash; now it is a notice. */
    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            runCatching { memoryRepository.deleteMemory(memoryId) }
                .onFailure { _events.emit(MemoriesEvent.ShowSnackbar(CopyRules.failureLine("delete memory"))) }
        }
    }

    fun deleteAllMemories() {
        viewModelScope.launch {
            runCatching { memoryRepository.deleteAllMemories() }
                .onFailure { _events.emit(MemoriesEvent.ShowSnackbar(CopyRules.failureLine("delete memories"))) }
        }
    }
}
