package com.materialchat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantVoiceSettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val credentials: EncryptedPreferences,
    providers: ProviderRepository
) : ViewModel() {
    val settings: kotlinx.coroutines.flow.StateFlow<AssistantVoiceSettings?> = preferences.assistantVoiceSettings.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )
    val providers = providers.observeProviders().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val keyConfigured = MutableStateFlow(false)
    val saving = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            try {
                keyConfigured.value = credentials.hasAssistantGeminiTtsApiKey()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Unable to read encrypted credential status."
            }
        }
    }

    // null keeps the existing credential; empty string explicitly removes it.
    fun save(value: AssistantVoiceSettings, replacementKey: String?) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try {
                if (replacementKey != null) credentials.setAssistantGeminiTtsApiKey(replacementKey)
                preferences.setAssistantVoiceSettings(value)
                keyConfigured.value = credentials.hasAssistantGeminiTtsApiKey()
                message.value = "Voice settings saved."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Unable to save all voice settings. Please retry."
            } finally {
                saving.value = false
            }
        }
    }
}
