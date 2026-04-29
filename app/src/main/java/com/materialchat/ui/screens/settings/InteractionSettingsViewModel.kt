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
    private val hapticsState = combine(
        appPreferences.hapticsEnabled,
        appPreferences.chatHapticsEnabled,
        appPreferences.navigationHapticsEnabled,
        appPreferences.listHapticsEnabled,
        appPreferences.gestureHapticsEnabled
    ) { global, chat, navigation, list, gesture ->
        HapticsState(global, chat, navigation, list, gesture)
    }

    private val buttonShapeState = combine(
        appPreferences.mainButtonShape,
        appPreferences.chatButtonShape
    ) { mainButtonShape, chatButtonShape ->
        ButtonShapeState(mainButtonShape, chatButtonShape)
    }

    val uiState = combine(hapticsState, buttonShapeState) { haptics, buttonShapes ->
        InteractionSettingsUiState(
            globalHaptics = haptics.global,
            chatHaptics = haptics.chat,
            navigationHaptics = haptics.navigation,
            listHaptics = haptics.list,
            gestureHaptics = haptics.gesture,
            mainButtonShape = buttonShapes.main,
            chatButtonShape = buttonShapes.chat
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

private data class HapticsState(
    val global: Boolean,
    val chat: Boolean,
    val navigation: Boolean,
    val list: Boolean,
    val gesture: Boolean
)

private data class ButtonShapeState(
    val main: AppPreferences.ComponentButtonShape,
    val chat: AppPreferences.ComponentButtonShape
)

data class InteractionSettingsUiState(
    val globalHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val chatHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val navigationHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val listHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val gestureHaptics: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED,
    val mainButtonShape: AppPreferences.ComponentButtonShape = AppPreferences.DEFAULT_COMPONENT_BUTTON_SHAPE,
    val chatButtonShape: AppPreferences.ComponentButtonShape = AppPreferences.DEFAULT_COMPONENT_BUTTON_SHAPE
)
