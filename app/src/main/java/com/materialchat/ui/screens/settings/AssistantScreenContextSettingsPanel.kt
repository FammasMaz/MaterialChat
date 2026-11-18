package com.materialchat.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.materialchat.assistant.screen.ScreenContextAccess
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.data.local.preferences.ScreenContextMode
import com.materialchat.ui.screens.settings.components.SettingsGroup
import com.materialchat.ui.screens.settings.components.SettingsToggleRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantScreenContextSettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {
    val settings: StateFlow<AssistantScreenContextSettings?> =
        preferences.assistantScreenContextSettings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (AssistantScreenContextSettings) -> AssistantScreenContextSettings) {
        viewModelScope.launch {
            val current = settings.value ?: preferences.assistantScreenContextSettings.first()
            runCatching { preferences.setAssistantScreenContextSettings(transform(current)) }
        }
    }
}

/**
 * Settings > Assistant > Screen context: the master switch (off = never, on = when the question
 * refers to the screen or the model asks for it), "always", and which halves of a capture are
 * sent. The platform's own switches are a separate page; the button opens it.
 */
@Composable
fun AssistantScreenContextSettingsPanel(viewModel: AssistantScreenContextSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val context = LocalContext.current
    val enabled = current.mode != ScreenContextMode.NEVER
    val rowCount = if (enabled) 4 else 1
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Screen context",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        SettingsGroup(modifier = Modifier.testTag("assistant_screen_context_settings")) {
            SettingsToggleRow(
                index = 0,
                count = rowCount,
                title = "Use screen context",
                supporting = "Answer questions about what is on screen when you call the assistant",
                icon = Icons.Outlined.Screenshot,
                checked = enabled,
                onCheckedChange = { on ->
                    viewModel.update { it.copy(mode = if (on) ScreenContextMode.AUTOMATIC else ScreenContextMode.NEVER) }
                }
            )
            if (enabled) {
                SettingsToggleRow(
                    index = 1,
                    count = rowCount,
                    title = "Always include the screen",
                    supporting = "Off: only when the question refers to the screen, or when the model asks for it",
                    checked = current.mode == ScreenContextMode.ALWAYS,
                    onCheckedChange = { on ->
                        viewModel.update { it.copy(mode = if (on) ScreenContextMode.ALWAYS else ScreenContextMode.AUTOMATIC) }
                    }
                )
                SettingsToggleRow(
                    index = 2,
                    count = rowCount,
                    title = "Include screenshot",
                    supporting = "Sent as an image to models that can see",
                    icon = Icons.Outlined.Image,
                    checked = current.includeScreenshot,
                    onCheckedChange = { on -> viewModel.update { it.copy(includeScreenshot = on) } }
                )
                SettingsToggleRow(
                    index = 3,
                    count = rowCount,
                    title = "Include screen text",
                    supporting = "The visible text, so text-only models work too",
                    icon = Icons.Outlined.Notes,
                    checked = current.includeScreenText,
                    onCheckedChange = { on -> viewModel.update { it.copy(includeScreenText = on) } }
                )
            }
        }
        Text(
            text = "Android shares the screen with the assistant only while \u201cUse screenshot\u201d and \u201cUse text from screen\u201d are on for MaterialChat under Settings \u203a Apps \u203a Default apps \u203a Digital assistant app. Private (secure) screens and the lock screen are never shared.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Android 16+ can show its own grant dialog while the request is still allowed; that
        // intent needs an activity result, which this panel (inside the Settings activity) has.
        // Everywhere else the system settings page is the only way.
        val requestIntent = remember(context) { ScreenContextAccess.requestIntent(context) }
        val requestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
        OutlinedButton(
            onClick = {
                if (requestIntent == null || runCatching { requestLauncher.launch(requestIntent) }.isFailure) {
                    ScreenContextAccess.openSettings(context)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("assistant_screen_context_open_settings")
        ) {
            Text(if (requestIntent != null) "Allow screen context" else "Open digital assistant settings")
        }
    }
}
