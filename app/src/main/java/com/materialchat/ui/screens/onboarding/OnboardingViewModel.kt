package com.materialchat.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.auth.NativeAuthCredential
import com.materialchat.data.auth.NativeAuthManager
import com.materialchat.data.local.preferences.AppPreferences
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isSigningIn: Boolean = false,
    val status: String? = null,
    val error: String? = null
)

sealed class OnboardingEvent {
    data object Completed : OnboardingEvent()
    data object OpenProviderSettings : OnboardingEvent()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val nativeAuthManager: NativeAuthManager,
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    fun signInWithCodex() {
        if (_uiState.value.isSigningIn) return

        _uiState.value = OnboardingUiState(
            isSigningIn = true,
            status = "Opening OpenAI sign-in…"
        )

        viewModelScope.launch {
            try {
                val credential = nativeAuthManager.authenticate(ProviderType.CODEX_NATIVE) { status ->
                    _uiState.value = _uiState.value.copy(status = status, error = null)
                }
                val template = Provider.codexTemplate()
                manageProvidersUseCase.addOrUpdateNativeProvider(
                    type = ProviderType.CODEX_NATIVE,
                    name = template.name,
                    baseUrl = template.baseUrl,
                    defaultModel = template.defaultModel,
                    credentialJson = NativeAuthCredential.encode(credential),
                    setAsActive = true
                )
                complete(OnboardingEvent.Completed)
            } catch (e: Exception) {
                // Fixed copy: the auth flow's exception text never reaches the card (W5 ruling).
                _uiState.value = OnboardingUiState(error = SIGN_IN_FAILED)
            }
        }
    }

    fun setUpOtherProvider() {
        viewModelScope.launch {
            complete(OnboardingEvent.OpenProviderSettings)
        }
    }

    fun continueWithLocalOllama() {
        viewModelScope.launch {
            runCatching {
                manageProvidersUseCase.getProviders()
                    .firstOrNull { it.type == ProviderType.OLLAMA_NATIVE }
                    ?.let { manageProvidersUseCase.setActiveProvider(it.id) }
            }
            complete(OnboardingEvent.Completed)
        }
    }

    fun skipForNow() {
        viewModelScope.launch {
            complete(OnboardingEvent.Completed)
        }
    }

    private suspend fun complete(event: OnboardingEvent) {
        appPreferences.setOnboardingComplete(true)
        _uiState.value = OnboardingUiState()
        _events.emit(event)
    }

    companion object {
        /** Body of the error card under the Codex hero; the card's title is the screen's. */
        const val SIGN_IN_FAILED =
            "The OpenAI sign-in could not be completed. Try again or pick another way to start."
    }
}
