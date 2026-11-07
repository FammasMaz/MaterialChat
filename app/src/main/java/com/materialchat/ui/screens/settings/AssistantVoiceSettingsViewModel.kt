package com.materialchat.ui.screens.settings

import android.content.Context
import com.materialchat.assistant.voice.TextToSpeechManager
import com.materialchat.assistant.voice.TtsInitResult
import com.materialchat.assistant.voice.TtsEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Provider
import com.materialchat.assistant.voice.GeminiTtsBackend
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
    private val providerRepository: ProviderRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext context: Context
) : ViewModel() {
    val settings: kotlinx.coroutines.flow.StateFlow<AssistantVoiceSettings?> = preferences.assistantVoiceSettings.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )
    val providers = providerRepository.observeProviders().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val keyConfigured = MutableStateFlow(false)
    val saving = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val modelPicker = MutableStateFlow(VoiceModelPickerState())
    private var modelJob: Job? = null
    private var modelGeneration = 0

    fun loadModels(providerId: String?) {
        modelJob?.cancel()
        val generation = ++modelGeneration
        modelPicker.value = VoiceModelPickerState(loading = true)
        modelJob = viewModelScope.launch {
            try {
                val provider = if (providerId == null) providerRepository.getActiveProvider()
                    else providerRepository.getProvider(providerId)
                if (provider == null) {
                    modelPicker.value = VoiceModelPickerState(error = "Provider unavailable. Configure a provider in Settings.")
                    return@launch
                }
                val models = chatRepository.fetchModels(provider).getOrThrow()
                if (generation == modelGeneration) {
                    modelPicker.value = VoiceModelPickerState(provider = provider, models = models.distinctBy { it.id })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == modelGeneration) {
                    modelPicker.value = VoiceModelPickerState(error = "Unable to load models. Check provider settings and retry.")
                }
            }
        }
    }

    // Never inject the assistant's shared engine into settings.
    private val previewEngine by lazy { TextToSpeechManager(context.applicationContext) }
    val installedVoices get() = previewEngine.installedVoices
    val voicesLoading = MutableStateFlow(false)
    val previewing = MutableStateFlow(false)
    val previewMessage = MutableStateFlow<String?>(null)
    private var voiceJob: Job? = null
    private var engineCreated = false
    private var previewGeneration = 0

    fun openVoicePicker() {
        if (voiceJob?.isActive == true) return
        engineCreated = true
        voicesLoading.value = true
        previewMessage.value = null
        voiceJob = viewModelScope.launch {
            try {
                if (previewEngine.initialize().first() is TtsInitResult.Error) {
                    previewMessage.value = "Unable to load installed offline voices. Reopen to retry."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                previewMessage.value = "Unable to load installed offline voices. Reopen to retry."
            } finally {
                voicesLoading.value = false
            }
        }
    }

    fun preview(value: AssistantVoiceSettings) {
        if (voicesLoading.value) return
        stopPreview()
        val generation = previewGeneration
        previewing.value = true
        previewMessage.value = null
        engineCreated = true
        voiceJob = viewModelScope.launch {
            try {
                // A fresh settings-owned engine prevents a previous explicit voice/language
                // leaking into a subsequent engine-default preview.
                previewEngine.release()
                withTimeout(20_000) {
                    if (previewEngine.initialize().first() is TtsInitResult.Error ||
                        previewEngine.configure(value).first() is TtsInitResult.Error) {
                        previewMessage.value = "Selected voice or language is unavailable. Choose an installed offline voice."
                    } else {
                        previewEngine.enqueue("This is a preview of your assistant voice.").collect {
                            if (it is TtsEvent.Error) previewMessage.value = "Unable to play voice preview."
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) {
                previewMessage.value = "Voice preview timed out. Please retry."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                previewMessage.value = "Unable to play voice preview."
            } finally {
                if (generation == previewGeneration) {
                    previewing.value = false
                    previewEngine.stop()
                }
            }
        }
    }

    fun stopPreview() {
        previewGeneration++
        voiceJob?.cancel()
        voiceJob = null
        if (engineCreated) previewEngine.stop()
        voicesLoading.value = false
        previewing.value = false
    }

    fun closeVoicePicker() {
        stopPreview()
        if (engineCreated) previewEngine.release()
    }

    override fun onCleared() {
        closeVoicePicker()
        super.onCleared()
    }

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

data class VoiceModelPickerState(
    val provider: Provider? = null,
    val models: List<AiModel> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
) {
    fun choices(saved: String?, inherited: Boolean): List<Pair<String?, String>> {
        val defaultLabel = if (inherited) "Inherit active provider default" else "Provider default"
        val defaults = listOf(null to (provider?.defaultModel?.let { "$defaultLabel ($it)" } ?: defaultLabel))
        val missing = saved?.takeIf { id -> models.none { it.id == id } }?.let {
            listOf(it to "$it (saved; not in fetched list)")
        }.orEmpty()
        return defaults + missing + models.map { it.id to it.name }
    }
}

/** Backend-compatible choices, not a live availability or account entitlement check. */
internal object VoiceGeminiChoices {
    val models = GeminiTtsBackend.SUPPORTED_MODELS.toList()
    val voices = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede")
    fun draftDefaults(value: AssistantVoiceSettings) = value.copy(
        geminiModel = value.geminiModel.ifBlank { models.first() },
        geminiVoice = value.geminiVoice.ifBlank { voices.first() }
    )
    fun choices(saved: String, supported: List<String>): List<Pair<String, String>> =
        (if (saved.isNotBlank() && saved !in supported) listOf(saved to "$saved (saved; not a listed choice)") else emptyList()) +
            supported.map { it to it }
}
