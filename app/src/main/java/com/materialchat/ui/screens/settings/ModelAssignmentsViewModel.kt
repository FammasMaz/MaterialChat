package com.materialchat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.localmodel.LocalModelCatalog
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.LightweightOnDeviceModels
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.LocalModelRepository
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.util.TaskModelAssignmentCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelAssignmentsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val localModelRepository: LocalModelRepository
) : ViewModel() {

    private val _pickerState = MutableStateFlow(TitleModelPickerState())
    val pickerState: StateFlow<TitleModelPickerState> = _pickerState.asStateFlow()

    private val _providers = MutableStateFlow<List<Provider>>(emptyList())

    val uiState: StateFlow<ModelAssignmentsUiState> = combine(
        combine(
            appPreferences.preferOnDeviceBackgroundTasks,
            appPreferences.titleGenerationModel,
            appPreferences.memoryExtractionModel,
            appPreferences.defaultImageGenerationModel,
            appPreferences.aiGeneratedTitlesEnabled
        ) { preferOnDevice, titleModel, memoryModel, imageModel, aiTitles ->
            AssignmentPrefs(
                preferOnDevice = preferOnDevice,
                titleModel = titleModel,
                memoryModel = memoryModel,
                imageModel = imageModel,
                aiTitlesEnabled = aiTitles
            )
        },
        _providers,
        localModelRepository.observeModels(),
        _pickerState
    ) { prefs, providers, localStates, picker ->
        val onDeviceRows = LightweightOnDeviceModels.preferredOrder.mapNotNull { id ->
            localStates.find { it.descriptor.id == id }?.let { state ->
                OnDeviceModelRow(
                    id = id,
                    displayName = state.descriptor.displayName,
                    sizeLabel = state.descriptor.approximateSizeBytes?.let { formatSize(it) },
                    availability = state.availability.name.replace('_', ' ').lowercase(),
                    isUsable = state.isUsable
                )
            } ?: LocalModelCatalog.descriptor(id)?.let { d ->
                OnDeviceModelRow(
                    id = id,
                    displayName = d.displayName,
                    sizeLabel = d.approximateSizeBytes?.let { formatSize(it) },
                    availability = "not downloaded",
                    isUsable = false
                )
            }
        }
        ModelAssignmentsUiState(
            preferOnDeviceBackgroundTasks = prefs.preferOnDevice,
            titleModelRaw = prefs.titleModel,
            memoryModelRaw = prefs.memoryModel,
            imageModelRaw = prefs.imageModel,
            aiGeneratedTitlesEnabled = prefs.aiTitlesEnabled,
            providers = providers,
            onDeviceModels = onDeviceRows,
            pickerState = picker,
            smallestRecommendation = LightweightOnDeviceModels.SMALLEST_RECOMMENDATION
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelAssignmentsUiState()
    )

    init {
        viewModelScope.launch {
            manageProvidersUseCase.observeProviders().collect { list ->
                _providers.value = list
            }
        }
        viewModelScope.launch {
            localModelRepository.refreshStatuses()
        }
        loadCloudModels()
    }

    fun loadCloudModels(force: Boolean = false) {
        val current = _pickerState.value
        if (current.isLoading) return
        if (!force && current.hasLoaded && current.modelsByProvider.isNotEmpty()) return
        val providers = _providers.value
        if (providers.isEmpty()) return

        _pickerState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val results = mutableMapOf<String, List<AiModel>>()
            val failures = mutableListOf<String>()
            for (provider in providers) {
                manageProvidersUseCase.fetchModels(provider.id)
                    .onSuccess { models ->
                        if (models.isNotEmpty()) {
                            results[provider.id] = models.sortedBy { it.name }
                        }
                    }
                    .onFailure { err ->
                        failures += "${provider.name}: ${err.message ?: "fetch failed"}"
                    }
            }
            _pickerState.update {
                it.copy(
                    isLoading = false,
                    hasLoaded = true,
                    modelsByProvider = results,
                    error = if (results.isEmpty() && failures.isNotEmpty()) {
                        failures.joinToString("; ")
                    } else null
                )
            }
        }
    }

    fun setPreferOnDevice(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setPreferOnDeviceBackgroundTasks(enabled)
        }
    }

    fun setTitleModel(providerId: String?, modelId: String) {
        viewModelScope.launch {
            appPreferences.setTitleGenerationModel(
                TaskModelAssignmentCodec.encode(providerId, modelId)
            )
        }
    }

    fun setMemoryModel(providerId: String?, modelId: String) {
        viewModelScope.launch {
            appPreferences.setMemoryExtractionModel(
                TaskModelAssignmentCodec.encode(providerId, modelId)
            )
        }
    }

    fun setImageModel(modelId: String) {
        viewModelScope.launch {
            appPreferences.setDefaultImageGenerationModel(modelId)
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
    }
}

data class ModelAssignmentsUiState(
    val preferOnDeviceBackgroundTasks: Boolean = AppPreferences.DEFAULT_PREFER_ON_DEVICE_BACKGROUND_TASKS,
    val titleModelRaw: String = "",
    val memoryModelRaw: String = "",
    val imageModelRaw: String = AppPreferences.DEFAULT_IMAGE_GENERATION_MODEL,
    val aiGeneratedTitlesEnabled: Boolean = true,
    val providers: List<Provider> = emptyList(),
    val onDeviceModels: List<OnDeviceModelRow> = emptyList(),
    val pickerState: TitleModelPickerState = TitleModelPickerState(),
    val smallestRecommendation: String = LightweightOnDeviceModels.SMALLEST_RECOMMENDATION
)

data class OnDeviceModelRow(
    val id: String,
    val displayName: String,
    val sizeLabel: String?,
    val availability: String,
    val isUsable: Boolean
)

private data class AssignmentPrefs(
    val preferOnDevice: Boolean,
    val titleModel: String,
    val memoryModel: String,
    val imageModel: String,
    val aiTitlesEnabled: Boolean
)