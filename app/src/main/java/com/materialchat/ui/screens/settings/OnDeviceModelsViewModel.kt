package com.materialchat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.LocalModelState
import com.materialchat.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnDeviceModelsUiState(
    val models: List<LocalModelState> = emptyList(),
    val isRefreshing: Boolean = false,
    val activeModelId: String? = null,
    val huggingFaceTokenPreview: String? = null
)

sealed interface OnDeviceModelsEvent {
    data class ShowSnackbar(val message: String) : OnDeviceModelsEvent
}

@HiltViewModel
class OnDeviceModelsViewModel @Inject constructor(
    private val localModelRepository: LocalModelRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnDeviceModelsUiState(isRefreshing = true))
    val uiState: StateFlow<OnDeviceModelsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnDeviceModelsEvent>()
    val events: SharedFlow<OnDeviceModelsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            localModelRepository.observeModels().collectLatest { models ->
                _uiState.value = _uiState.value.copy(models = models)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            runCatching {
                localModelRepository.refreshStatuses()
                localModelRepository.getHuggingFaceTokenPreview()
            }
                .onSuccess { preview -> _uiState.value = _uiState.value.copy(huggingFaceTokenPreview = preview) }
                .onFailure { _events.emit(OnDeviceModelsEvent.ShowSnackbar(it.message ?: "Status refresh failed")) }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun saveHuggingFaceToken(token: String) {
        viewModelScope.launch {
            localModelRepository.setHuggingFaceToken(token)
            _uiState.value = _uiState.value.copy(
                huggingFaceTokenPreview = localModelRepository.getHuggingFaceTokenPreview()
            )
            _events.emit(OnDeviceModelsEvent.ShowSnackbar("Hugging Face token saved"))
        }
    }

    fun clearHuggingFaceToken() {
        viewModelScope.launch {
            localModelRepository.clearHuggingFaceToken()
            _uiState.value = _uiState.value.copy(huggingFaceTokenPreview = null)
            _events.emit(OnDeviceModelsEvent.ShowSnackbar("Hugging Face token cleared"))
        }
    }

    fun download(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(activeModelId = modelId)
            val isAicore = com.materialchat.data.localmodel.LocalModelCatalog.descriptor(modelId)
                ?.backend == com.materialchat.domain.model.LocalModelBackend.AICORE_GEMINI_NANO
            localModelRepository.download(modelId)
                .onSuccess {
                    val message = if (isAicore) {
                        "Downloading Gemini Nano via Android AICore…"
                    } else {
                        "Download started. You can leave the app; progress continues in notifications."
                    }
                    _events.emit(OnDeviceModelsEvent.ShowSnackbar(message))
                }
                .onFailure { _events.emit(OnDeviceModelsEvent.ShowSnackbar(it.message ?: "Model download failed")) }
            _uiState.value = _uiState.value.copy(activeModelId = null)
        }
    }

    fun delete(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(activeModelId = modelId)
            localModelRepository.delete(modelId)
                .onSuccess { _events.emit(OnDeviceModelsEvent.ShowSnackbar("Downloaded model deleted")) }
                .onFailure { _events.emit(OnDeviceModelsEvent.ShowSnackbar(it.message ?: "Could not delete model")) }
            _uiState.value = _uiState.value.copy(activeModelId = null)
        }
    }

    fun unmount(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(activeModelId = modelId)
            localModelRepository.unmount(modelId)
                .onSuccess { _events.emit(OnDeviceModelsEvent.ShowSnackbar("Model unmounted from RAM")) }
                .onFailure { _events.emit(OnDeviceModelsEvent.ShowSnackbar(it.message ?: "Could not unmount model")) }
            _uiState.value = _uiState.value.copy(activeModelId = null)
        }
    }
}
