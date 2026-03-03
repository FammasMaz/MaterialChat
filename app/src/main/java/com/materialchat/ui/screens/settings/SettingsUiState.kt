package com.materialchat.ui.screens.settings

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.UpdateState

/**
 * UI state for the Settings screen.
 *
 * This sealed interface represents all possible states of the settings screen.
 */
sealed interface SettingsUiState {

    /**
     * Loading state while settings are being fetched.
     */
    data object Loading : SettingsUiState

    /**
     * Success state with settings data.
     *
     * @property providers List of all configured providers
     * @property systemPrompt Current global system prompt
     * @property themeMode Current theme mode setting
     * @property dynamicColorEnabled Whether dynamic color is enabled
     * @property isDynamicColorSupported Whether the device supports dynamic color (Android 12+)
     * @property hapticsEnabled Whether haptic feedback is enabled
     * @property aiGeneratedTitlesEnabled Whether AI-generated conversation titles are enabled
     * @property titleGenerationModel Custom model for title generation (empty = use conversation model)
     * @property rememberLastModelEnabled Whether to use the last used model for new chats
     * @property appVersion Current app version for display
     * @property autoCheckUpdates Whether automatic update checking is enabled
     * @property updateState Current state of the update system
     * @property showAddProviderSheet Whether to show the add provider bottom sheet
     * @property editingProvider Provider being edited, if any
     * @property showDeleteConfirmation Provider being deleted (for confirmation dialog)
     * @property isSaving Whether a save operation is in progress
     */
    data class Success(
        val providers: List<ProviderUiItem>,
        val systemPrompt: String,
        val themeMode: AppPreferences.ThemeMode,
        val dynamicColorEnabled: Boolean,
        val isDynamicColorSupported: Boolean,
        val hapticsEnabled: Boolean = true,
        val aiGeneratedTitlesEnabled: Boolean = true,
        val titleGenerationModel: String = "",
        val rememberLastModelEnabled: Boolean = true,
        val appVersion: String = "",
        val autoCheckUpdates: Boolean = true,
        val updateState: UpdateState = UpdateState.Idle,
        // Assistant settings
        val assistantEnabled: Boolean = true,
        val assistantVoiceEnabled: Boolean = true,
        val assistantTtsEnabled: Boolean = true,
        val beautifulModelNamesEnabled: Boolean = true,
        val alwaysShowThinking: Boolean = false,
        val showAddProviderSheet: Boolean = false,
        val editingProvider: Provider? = null,
        val showDeleteConfirmation: Provider? = null,
        val isSaving: Boolean = false
    ) : SettingsUiState

    /**
     * Error state when something goes wrong.
     *
     * @property message The error message to display
     */
    data class Error(
        val message: String
    ) : SettingsUiState
}

/**
 * UI representation of a provider item in the settings list.
 *
 * @property provider The domain provider model
 * @property hasApiKey Whether this provider has an API key configured
 * @property isConnected Last known connection status (null if not tested)
 */
data class ProviderUiItem(
    val provider: Provider,
    val hasApiKey: Boolean = false,
    val isConnected: Boolean? = null
)

/**
 * Events that can occur on the Settings screen.
 * These are one-time events that should be handled and consumed.
 */
sealed interface SettingsEvent {

    /**
     * Navigate back to the previous screen.
     */
    data object NavigateBack : SettingsEvent

    /**
     * Show a snackbar message.
     *
     * @property message The message to display
     * @property actionLabel Optional action button label
     */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null
    ) : SettingsEvent

    /**
     * Provider was saved successfully.
     *
     * @property providerName The name of the saved provider
     * @property isNew Whether this is a new provider or an update
     */
    data class ProviderSaved(
        val providerName: String,
        val isNew: Boolean
    ) : SettingsEvent

    /**
     * Provider was deleted successfully.
     *
     * @property providerName The name of the deleted provider
     */
    data class ProviderDeleted(
        val providerName: String
    ) : SettingsEvent

    /**
     * Provider activation changed.
     *
     * @property providerName The name of the activated provider
     */
    data class ProviderActivated(
        val providerName: String
    ) : SettingsEvent

    /**
     * Settings were saved successfully.
     */
    data object SettingsSaved : SettingsEvent

    /**
     * Connection test completed.
     *
     * @property providerName The name of the tested provider
     * @property success Whether the connection was successful
     * @property errorMessage Error message if the connection failed
     */
    data class ConnectionTestResult(
        val providerName: String,
        val success: Boolean,
        val errorMessage: String? = null
    ) : SettingsEvent
}

/**
 * Form state for adding or editing a provider.
 *
 * @property name Provider display name
 * @property type Provider type (OpenAI-compatible or Ollama)
 * @property baseUrl Base URL for API requests
 * @property defaultModel Default model to use
 * @property apiKey API key (optional, required for OpenAI-compatible)
 * @property hasExistingKey Whether the provider already has a saved API key
 * @property nameError Validation error for name field
 * @property baseUrlError Validation error for base URL field
 * @property defaultModelError Validation error for default model field
 * @property apiKeyError Validation error for API key field
 * @property availableModels Models fetched from the provider
 * @property isFetchingModels Whether model list is being fetched
 * @property modelsError Error message from model fetch
 */
data class ProviderFormState(
    val name: String = "",
    val type: com.materialchat.domain.model.ProviderType = com.materialchat.domain.model.ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val defaultModel: String = "",
    val apiKey: String = "",
    val hasExistingKey: Boolean = false,
    val nameError: String? = null,
    val baseUrlError: String? = null,
    val defaultModelError: String? = null,
    val apiKeyError: String? = null,
    val availableModels: List<com.materialchat.domain.model.AiModel> = emptyList(),
    val isFetchingModels: Boolean = false,
    val modelsError: String? = null
) {
    /**
     * Whether the form has validation errors.
     */
    val hasErrors: Boolean
        get() = nameError != null || baseUrlError != null ||
                defaultModelError != null || apiKeyError != null

    /**
     * Whether the form can be submitted (all required fields filled).
     */
    val canSubmit: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() &&
                defaultModel.isNotBlank() && !hasErrors &&
                (type != com.materialchat.domain.model.ProviderType.OPENAI_COMPATIBLE || apiKey.isNotBlank() || hasExistingKey)
}
