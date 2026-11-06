package com.materialchat.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.domain.model.ReasoningEffort

/** Inline entry from Settings; draft credentials deliberately never use rememberSaveable. */
@Composable
fun AssistantVoiceSettingsPanel(viewModel: AssistantVoiceSettingsViewModel = hiltViewModel()) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Voice configuration")
    }
    if (!open) return
    val stored by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val configured by viewModel.keyConfigured.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loaded = stored
    if (loaded == null) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Voice configuration") },
            text = { Text("Loading settings…") }, confirmButton = {})
        return
    }
    var draft by remember(loaded) { mutableStateOf(loaded) }
    var key by remember { mutableStateOf("") }
    var removeKey by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) open = false },
        title = { Text("Voice configuration") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Voice conversation", style = MaterialTheme.typography.titleMedium)
                Text("Blank overrides inherit the assistant configuration. A selected provider uses its default model when model is blank.")
                VoiceChoice("Provider", providers.firstOrNull { it.id == draft.providerId }?.name
                    ?: draft.providerId ?: "Inherit", listOf(null to "Inherit") + providers.map { it.id to it.name }) {
                    draft = draft.copy(providerId = it, model = null)
                }
                OutlinedTextField(draft.model.orEmpty(), { draft = draft.copy(model = it) },
                    label = { Text("Model identifier (blank to inherit)") }, modifier = Modifier.fillMaxWidth())
                VoiceChoice("Reasoning", draft.reasoningEffort?.displayName ?: "Inherit",
                    listOf(null to "Inherit") + ReasoningEffort.entries.map { it to it.displayName }) {
                    draft = draft.copy(reasoningEffort = it)
                }
                HorizontalDivider()
                Text("Speech output", style = MaterialTheme.typography.titleMedium)
                VoiceChoice("Backend", draft.backend.name, AssistantSpeechBackend.entries.map { it to it.name }) {
                    draft = draft.copy(backend = it)
                }
                if (draft.backend == AssistantSpeechBackend.ANDROID) {
                    OutlinedTextField(draft.androidVoiceId.orEmpty(), { draft = draft.copy(androidVoiceId = it) },
                        label = { Text("Android voice identifier (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.languageTag.orEmpty(), { draft = draft.copy(languageTag = it) },
                        label = { Text("Language tag (e.g. en-US)") }, modifier = Modifier.fillMaxWidth())
                    Text("Speech rate: ${"%.2f".format(draft.speechRate)}×")
                    Slider(value = draft.speechRate, onValueChange = { draft = draft.copy(speechRate = it) }, valueRange = 0.25f..2f)
                    Text("Blank voice and language use engine defaults. Availability depends on the installed Android speech engine.")
                } else {
                    Text("Gemini sends response text to Google. Supply a model and voice supported by your account; availability and billing depend on Google.")
                    OutlinedTextField(draft.geminiModel, { draft = draft.copy(geminiModel = it) },
                        label = { Text("Gemini TTS model identifier") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.geminiVoice, { draft = draft.copy(geminiVoice = it) },
                        label = { Text("Gemini voice name") }, modifier = Modifier.fillMaxWidth())
                    Text(if (configured) "Encrypted key stored. Leave blank to keep it." else "No dedicated Gemini TTS key stored.")
                    OutlinedTextField(key, { key = it; removeKey = false },
                        label = { Text("Optional Gemini TTS API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { key = ""; removeKey = true }) { Text("Remove stored key on save") }
                    if (removeKey) Text("Stored key will be removed.")
                }
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                viewModel.save(draft, if (removeKey) "" else key.takeIf { it.isNotBlank() })
                key = ""
                removeKey = false
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { open = false }) { Text("Close") } }
    )
}

@Composable
private fun <T> VoiceChoice(label: String, value: String, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
