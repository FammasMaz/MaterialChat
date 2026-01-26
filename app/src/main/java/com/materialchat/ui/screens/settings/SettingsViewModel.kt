package com.materialchat.ui.screens.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.repository.UpdateManager
import com.materialchat.domain.model.AppUpdate
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.usecase.ManageProvidersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages provider configuration, theme settings, and system prompt.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val appPreferences: AppPreferences,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val _formState = MutableStateFlow(ProviderFormState())
    val formState: StateFlow<ProviderFormState> = _formState.asStateFlow()

    init {
        observeSettings()
    }

    /**
     * Observes settings data and updates the UI state.
     */
    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                manageProvidersUseCase.observeProviders(),
                appPreferences.systemPrompt,
                appPreferences.themeMode,
                appPreferences.dynamicColorEnabled,
                combine(
                    appPreferences.hapticsEnabled,
                    appPreferences.aiGeneratedTitlesEnabled,
                    appPreferences.titleGenerationModel,
                    appPreferences.autoCheckUpdates,
                    updateManager.updateState
                ) { haptics, aiTitles, titleModel, autoCheck, updateState ->
                    SettingsToggles(haptics, aiTitles, titleModel, autoCheck, updateState)
                }
            ) { providers, systemPrompt, themeMode, dynamicColorEnabled, toggles ->
                SettingsData(
                    providers = providers,
                    systemPrompt = systemPrompt,
                    themeMode = themeMode,
                    dynamicColorEnabled = dynamicColorEnabled,
                    hapticsEnabled = toggles.haptics,
                    aiGeneratedTitlesEnabled = toggles.aiTitles,
                    titleGenerationModel = toggles.titleModel,
                    autoCheckUpdates = toggles.autoCheck,
                    updateState = toggles.updateState
                )
            }
                .catch { e ->
                    _uiState.value = SettingsUiState.Error(
                        message = e.message ?: "Failed to load settings"
                    )
                }
                .collect { data ->
                    // Always fetch fresh API key status for each provider
                    val apiKeyStatusMap = data.providers.associate { provider ->
                        provider.id to manageProvidersUseCase.hasApiKey(provider.id)
                    }

                    val currentState = _uiState.value
                    val providerUiItems = data.providers.map { provider ->
                        ProviderUiItem(
                            provider = provider,
                            hasApiKey = apiKeyStatusMap[provider.id] ?: false
                        )
                    }

                    _uiState.value = SettingsUiState.Success(
                        providers = providerUiItems,
                        systemPrompt = data.systemPrompt,
                        themeMode = data.themeMode,
                        dynamicColorEnabled = data.dynamicColorEnabled,
                        isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        hapticsEnabled = data.hapticsEnabled,
                        aiGeneratedTitlesEnabled = data.aiGeneratedTitlesEnabled,
                        titleGenerationModel = data.titleGenerationModel,
                        appVersion = updateManager.getCurrentVersion(),
                        autoCheckUpdates = data.autoCheckUpdates,
                        updateState = data.updateState,
                        showAddProviderSheet = if (currentState is SettingsUiState.Success) {
                            currentState.showAddProviderSheet
                        } else false,
                        editingProvider = if (currentState is SettingsUiState.Success) {
                            currentState.editingProvider
                        } else null,
                        showDeleteConfirmation = if (currentState is SettingsUiState.Success) {
                            currentState.showDeleteConfirmation
                        } else null,
                        isSaving = if (currentState is SettingsUiState.Success) {
                            currentState.isSaving
                        } else false
                    )
                }
        }
    }

    /**
     * Navigates back to the previous screen.
     */
    fun navigateBack() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.NavigateBack)
        }
    }

    // ========== Provider Management ==========

    /**
     * Shows the add provider bottom sheet.
     */
    fun showAddProviderSheet() {
        val currentState = _uiState.value
        if (currentState is SettingsUiState.Success) {
            _formState.value = ProviderFormState()
            _uiState.value = currentState.copy(
                showAddProviderSheet = true,
                editingProvider = null
            )
        }
    }

    /**
     * Hides the add/edit provider bottom sheet.
     */
    fun hideProviderSheet() {
        val currentState = _uiState.value
        if (currentState is SettingsUiState.Success) {
            _uiState.value = currentState.copy(
                showAddProviderSheet = false,
                editingProvider = null
            )
            _formState.value = ProviderFormState()
        }
    }

    /**
     * Shows the edit provider bottom sheet for an existing provider.
     */
    fun editProvider(provider: Provider) {
        val currentState = _uiState.value
        if (currentState is SettingsUiState.Success) {
            // Get fresh API key status from the provider list
            val hasExistingKey = currentState.providers
                .find { it.provider.id == provider.id }?.hasApiKey ?: false
            _formState.value = ProviderFormState(
                name = provider.name,
                type = provider.type,
                baseUrl = provider.baseUrl,
                defaultModel = provider.defaultModel,
                apiKey = "", // Don't show existing API key
                hasExistingKey = hasExistingKey
            )
            _uiState.value = currentState.copy(
                showAddProviderSheet = true,
                editingProvider = provider
            )
        }
    }

    /**
     * Updates a form field value.
     */
    fun updateFormField(
        name: String? = null,
        type: ProviderType? = null,
        baseUrl: String? = null,
        defaultModel: String? = null,
        apiKey: String? = null
    ) {
        val current = _formState.value
        val shouldResetModels = type != null || baseUrl != null || apiKey != null
        val availableModels = if (shouldResetModels) emptyList() else current.availableModels
        val isFetchingModels = if (shouldResetModels) false else current.isFetchingModels
        val modelsError = if (shouldResetModels) null else current.modelsError

        _formState.value = current.copy(
            name = name ?: current.name,
            type = type ?: current.type,
            baseUrl = baseUrl ?: current.baseUrl,
            defaultModel = defaultModel ?: current.defaultModel,
            apiKey = apiKey ?: current.apiKey,
            // Clear errors when user edits fields
            nameError = if (name != null) null else current.nameError,
            baseUrlError = if (baseUrl != null) null else current.baseUrlError,
            defaultModelError = if (defaultModel != null) null else current.defaultModelError,
            apiKeyError = if (apiKey != null) null else current.apiKeyError,
            availableModels = availableModels,
            isFetchingModels = isFetchingModels,
            modelsError = modelsError
        )
    }

    fun fetchProviderModels() {
        val currentState = _uiState.value
        if (currentState !is SettingsUiState.Success) return

        val currentFormState = _formState.value
        if (currentFormState.baseUrl.isBlank()) {
            viewModelScope.launch {
                _events.emit(SettingsEvent.ShowSnackbar("Base URL is required to fetch models."))
            }
            return
        }

        if (currentFormState.type == ProviderType.OPENAI_COMPATIBLE) {
            val hasExistingKey = currentState.editingProvider?.let { provider ->
                currentState.providers.find { it.provider.id == provider.id }?.hasApiKey
            } ?: false
            val hasApiKey = currentFormState.apiKey.isNotBlank() || hasExistingKey
            if (!hasApiKey) {
                viewModelScope.launch {
                    _events.emit(SettingsEvent.ShowSnackbar("API key is required to fetch models."))
                }
                return
            }
        }

        if (currentFormState.isFetchingModels) return

        _formState.value = currentFormState.copy(
            isFetchingModels = true,
            modelsError = null
        )

        viewModelScope.launch {
            val result = manageProvidersUseCase.fetchModelsForConfig(
                providerId = currentState.editingProvider?.id,
                type = currentFormState.type,
                baseUrl = currentFormState.baseUrl,
                apiKey = currentFormState.apiKey.takeIf { it.isNotBlank() }
            )

            result.onSuccess { models ->
                _formState.value = _formState.value.copy(
                    availableModels = models.sortedBy { it.name },
                    isFetchingModels = false,
                    modelsError = null
                )
                if (models.isEmpty()) {
                    _events.emit(SettingsEvent.ShowSnackbar("No models were returned by the provider."))
                }
            }.onFailure { error ->
                val message = error.message ?: "Failed to fetch models."
                _formState.value = _formState.value.copy(
                    isFetchingModels = false,
                    modelsError = message
                )
                _events.emit(SettingsEvent.ShowSnackbar(message))
            }
        }
    }

    /**
     * Validates and saves the provider form.
     */
    fun saveProvider() {
        val form = _formState.value
        val currentState = _uiState.value

        if (currentState !is SettingsUiState.Success) return

        // Validate form
        var hasErrors = false
        var nameError: String? = null
        var baseUrlError: String? = null
        var defaultModelError: String? = null
        var apiKeyError: String? = null

        if (form.name.isBlank()) {
            nameError = "Name is required"
            hasErrors = true
        } else if (form.name.length > 50) {
            nameError = "Name must be 50 characters or less"
            hasErrors = true
        }

        if (form.baseUrl.isBlank()) {
            baseUrlError = "Base URL is required"
            hasErrors = true
        } else if (!isValidUrl(form.baseUrl)) {
            baseUrlError = "Invalid URL format"
            hasErrors = true
        }

        if (form.defaultModel.isBlank()) {
            defaultModelError = "Default model is required"
            hasErrors = true
        }

        if (form.type == ProviderType.OPENAI_COMPATIBLE) {
            val editingProvider = currentState.editingProvider
            val hasExistingKey = editingProvider?.let { provider ->
                currentState.providers.find { it.provider.id == provider.id }?.hasApiKey
            } ?: false

            if (form.apiKey.isBlank() && !hasExistingKey) {
                apiKeyError = "API key is required for OpenAI-compatible providers"
                hasErrors = true
            }
        }

        if (hasErrors) {
            _formState.value = form.copy(
                nameError = nameError,
                baseUrlError = baseUrlError,
                defaultModelError = defaultModelError,
                apiKeyError = apiKeyError
            )
            return
        }

        // Save provider
        _uiState.value = currentState.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val editingProvider = currentState.editingProvider
                val isNew = editingProvider == null

                if (isNew) {
                    // Create new provider
                    manageProvidersUseCase.addProvider(
                        name = form.name,
                        type = form.type,
                        baseUrl = form.baseUrl,
                        defaultModel = form.defaultModel,
                        apiKey = if (form.apiKey.isNotBlank()) form.apiKey else null,
                        setAsActive = false
                    )
                } else {
                    // Update existing provider
                    manageProvidersUseCase.updateProvider(
                        providerId = editingProvider.id,
                        name = form.name,
                        baseUrl = form.baseUrl,
                        defaultModel = form.defaultModel,
                        apiKey = if (form.apiKey.isNotBlank()) form.apiKey else null
                    )
                }

                hideProviderSheet()
                _events.emit(SettingsEvent.ProviderSaved(form.name, isNew))
            } catch (e: Exception) {
                val updatedState = _uiState.value
                if (updatedState is SettingsUiState.Success) {
                    _uiState.value = updatedState.copy(isSaving = false)
                }
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = e.message ?: "Failed to save provider"
                ))
            }
        }
    }

    /**
     * Sets a provider as the active provider.
     */
    fun setActiveProvider(providerId: String) {
        viewModelScope.launch {
            try {
                val provider = manageProvidersUseCase.getProvider(providerId)
                manageProvidersUseCase.setActiveProvider(providerId)
                _events.emit(SettingsEvent.ProviderActivated(provider?.name ?: "Provider"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = e.message ?: "Failed to activate provider"
                ))
            }
        }
    }

    /**
     * Shows the delete confirmation dialog for a provider.
     */
    fun showDeleteConfirmation(provider: Provider) {
        val currentState = _uiState.value
        if (currentState is SettingsUiState.Success) {
            _uiState.value = currentState.copy(showDeleteConfirmation = provider)
        }
    }

    /**
     * Hides the delete confirmation dialog.
     */
    fun hideDeleteConfirmation() {
        val currentState = _uiState.value
        if (currentState is SettingsUiState.Success) {
            _uiState.value = currentState.copy(showDeleteConfirmation = null)
        }
    }

    /**
     * Deletes a provider after confirmation.
     */
    fun deleteProvider(provider: Provider) {
        viewModelScope.launch {
            try {
                manageProvidersUseCase.deleteProvider(provider.id)
                hideDeleteConfirmation()
                _events.emit(SettingsEvent.ProviderDeleted(provider.name))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = e.message ?: "Failed to delete provider"
                ))
            }
        }
    }

    /**
     * Tests the connection to a provider.
     */
    fun testConnection(providerId: String) {
        viewModelScope.launch {
            try {
                val provider = manageProvidersUseCase.getProvider(providerId) ?: return@launch
                val result = manageProvidersUseCase.testConnection(providerId)

                result.fold(
                    onSuccess = {
                        _events.emit(SettingsEvent.ConnectionTestResult(
                            providerName = provider.name,
                            success = true
                        ))
                    },
                    onFailure = { e ->
                        _events.emit(SettingsEvent.ConnectionTestResult(
                            providerName = provider.name,
                            success = false,
                            errorMessage = e.message
                        ))
                    }
                )
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = e.message ?: "Connection test failed"
                ))
            }
        }
    }

    // ========== Preferences ==========

    /**
     * Updates the system prompt.
     */
    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            try {
                appPreferences.setSystemPrompt(prompt)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save system prompt"
                ))
            }
        }
    }

    /**
     * Updates the theme mode.
     */
    fun updateThemeMode(mode: AppPreferences.ThemeMode) {
        viewModelScope.launch {
            try {
                appPreferences.setThemeMode(mode)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save theme setting"
                ))
            }
        }
    }

    /**
     * Updates the dynamic color setting.
     */
    fun updateDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                appPreferences.setDynamicColorEnabled(enabled)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save dynamic color setting"
                ))
            }
        }
    }

    /**
     * Updates the haptic feedback setting.
     */
    fun updateHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                appPreferences.setHapticsEnabled(enabled)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save haptics setting"
                ))
            }
        }
    }

    /**
     * Updates the AI-generated titles setting.
     */
    fun updateAiGeneratedTitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                appPreferences.setAiGeneratedTitlesEnabled(enabled)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save AI titles setting"
                ))
            }
        }
    }

    /**
     * Updates the custom title generation model.
     */
    fun updateTitleGenerationModel(model: String) {
        viewModelScope.launch {
            try {
                appPreferences.setTitleGenerationModel(model)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save title model setting"
                ))
            }
        }
    }

    // ========== App Updates ==========

    /**
     * Updates the auto-check for updates setting.
     */
    fun updateAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            try {
                appPreferences.setAutoCheckUpdates(enabled)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowSnackbar(
                    message = "Failed to save auto-update setting"
                ))
            }
        }
    }

    /**
     * Manually checks for updates.
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates(force = true)
        }
    }

    /**
     * Downloads the specified update.
     */
    fun downloadUpdate(update: AppUpdate) {
        viewModelScope.launch {
            updateManager.downloadUpdate(update)
        }
    }

    /**
     * Installs the downloaded update.
     */
    fun installUpdate() {
        viewModelScope.launch {
            updateManager.installUpdate()
        }
    }

    /**
     * Cancels the current download.
     */
    fun cancelDownload() {
        updateManager.cancelDownload()
    }

    /**
     * Skips the currently available version.
     */
    fun skipVersion() {
        viewModelScope.launch {
            updateManager.skipVersion()
        }
    }

    /**
     * Dismisses the update banner.
     */
    fun dismissUpdateBanner() {
        updateManager.dismissBanner()
    }

    /**
     * Retries loading settings after an error.
     */
    fun retry() {
        _uiState.value = SettingsUiState.Loading
        observeSettings()
    }

    // ========== Utilities ==========

    /**
     * Validates a URL format.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val trimmed = url.trim()
            (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                trimmed.length > 10
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Internal data class for combining settings flows.
     */
    private data class SettingsData(
        val providers: List<Provider>,
        val systemPrompt: String,
        val themeMode: AppPreferences.ThemeMode,
        val dynamicColorEnabled: Boolean,
        val hapticsEnabled: Boolean,
        val aiGeneratedTitlesEnabled: Boolean,
        val titleGenerationModel: String,
        val autoCheckUpdates: Boolean,
        val updateState: com.materialchat.domain.model.UpdateState
    )

    /**
     * Internal data class for combining toggle settings.
     */
    private data class SettingsToggles(
        val haptics: Boolean,
        val aiTitles: Boolean,
        val titleModel: String,
        val autoCheck: Boolean,
        val updateState: com.materialchat.domain.model.UpdateState
    )
}
