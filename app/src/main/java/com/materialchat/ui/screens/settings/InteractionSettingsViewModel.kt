package com.materialchat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InteractionSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {
    val uiState = combine(
        appPreferences.hapticsEnabled,
        appPreferences.chatHapticsEnabled,
        appPreferences.navigationHapticsEnabled,
        appPreferences.listHapticsEnabled,
        appPreferences.gestureHapticsEnabled,
        appPreferences.mainButtonShape,
        appPreferences.chatButtonShape
    ) { values ->
        InteractionSettingsUiState(
            globalHaptics = values[0] as Boolean,
            chatHaptics = values[1] as Boolean,
            navigationHaptics = values[2] as Boolean,
            listHaptics = values[3] as Boolean,
            gestureHaptics = values[4] as Boolean,
            mainButtonShape = values[5] as AppPreferences.ComponentButtonShape,
            chatButtonShape = values[6] as AppPreferences.ComponentButtonShape
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InteractionSettingsUiState()
    )

    fun updateGlobalHaptics(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setHapticsEnabled(enabled)
    }

    fun updateChatHaptics(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setChatHapticsEnabled(enabled)
    }

    fun updateNavigationHaptics(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setNavigationHapticsEnabled(enabled)
    }

    fun updateListHaptics(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setListHapticsEnabled(enabled)
    }

    fun updateGestureHaptics(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setGestureHapticsEnabled(enabled)
    }

    fun updateMainButtonShape(shape: AppPreferences.ComponentButtonShape) = viewModelScope.launch {
        appPreferences.setMainButtonShape(shape)
    }

    fun updateChatButtonShape(shape: AppPreferences.ComponentButtonShape) = viewModelScope.launch {
        appPreferences.setChatButtonShape(shape)
    }
}

data class InteractionSettingsUiState(
    val globalHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val chatHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val navigationHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val listHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val gestureHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val mainButtonShape: AppPreferences.ComponentButtonShape = AppPreferences.DEFAULT_COMPONENT_BUTTON_SHAPE,
    val chatButtonShape: AppPreferences.ComponentButtonShape = AppPreferences.DEFAULT_COMPONENT_BUTTON_SHAPE
)
