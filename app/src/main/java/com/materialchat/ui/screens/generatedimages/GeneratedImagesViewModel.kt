package com.materialchat.ui.screens.generatedimages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.GeneratedImage
import com.materialchat.domain.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GeneratedImagesViewModel @Inject constructor(
    conversationRepository: ConversationRepository
) : ViewModel() {
    val uiState: StateFlow<GeneratedImagesUiState> = conversationRepository
        .observeGeneratedImages()
        .map { images -> GeneratedImagesUiState(images = images) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneratedImagesUiState()
        )
}

data class GeneratedImagesUiState(
    val images: List<GeneratedImage> = emptyList()
)
