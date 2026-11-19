package com.materialchat.ui.screens.generatedimages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.GeneratedImage
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.ui.components.LoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GeneratedImagesViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    /** Bumped by [retry]; each value re-subscribes to the repository from scratch. */
    private val loadAttempt = MutableStateFlow(0)

    val uiState: StateFlow<GeneratedImagesUiState> = loadAttempt
        .flatMapLatest {
            // The repository call runs inside the builder so a synchronous throw is caught too.
            flow { emitAll(conversationRepository.observeGeneratedImages()) }
                .map { images -> GeneratedImagesUiState(images = images) }
                .onStart { emit(GeneratedImagesUiState(isLoading = true)) }
                .catch { emit(GeneratedImagesUiState(error = LoadError.Unavailable)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneratedImagesUiState(isLoading = true)
        )

    /** Re-observes the image library after a load failure; the screen shows loading meanwhile. */
    fun retry() {
        loadAttempt.update { it + 1 }
    }
}

data class GeneratedImagesUiState(
    val images: List<GeneratedImage> = emptyList(),
    val isLoading: Boolean = false,
    /** Non-null when the image library could not be observed; [images] is empty then. */
    val error: LoadError? = null
)
