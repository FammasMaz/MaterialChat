package com.materialchat.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.assistant.voice.SherpaModelState
import com.materialchat.assistant.voice.SherpaTtsModel
import com.materialchat.assistant.voice.formatModelBytes
import com.materialchat.assistant.voice.installedTtsEngines
import com.materialchat.assistant.voice.recognitionEngineReport
import com.materialchat.assistant.voice.recognitionEngineLabel
import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.domain.model.ReasoningEffort

/** Inline entry from Settings; draft credentials deliberately never use rememberSaveable. */
@Composable
fun AssistantVoiceSettingsPanel(viewModel: AssistantVoiceSettingsViewModel = hiltViewModel()) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Voice configuration")
    }
    if (!open) return
    DisposableEffect(viewModel) {
        viewModel.openVoicePicker()
        onDispose { viewModel.closeVoicePicker() }
    }
    val voices by viewModel.installedVoices.collectAsStateWithLifecycle()
    val voicesLoading by viewModel.voicesLoading.collectAsStateWithLifecycle()
    val previewing by viewModel.previewing.collectAsStateWithLifecycle()
    val previewMessage by viewModel.previewMessage.collectAsStateWithLifecycle()
    val stored by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val modelPicker by viewModel.modelPicker.collectAsStateWithLifecycle()
    val configured by viewModel.keyConfigured.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val loaded = stored
    if (loaded == null) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Voice configuration") },
            text = { Text("Loading settings…") }, confirmButton = {})
        return
    }
    var draft by remember(loaded) { mutableStateOf(VoiceGeminiChoices.draftDefaults(loaded)) }
    LaunchedEffect(draft.providerId, providers) { viewModel.loadModels(draft.providerId) }
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
                Text("Inherit uses the active assistant provider. Provider default follows that provider's configured default model. Reasoning can inherit independently.")
                VoiceChoice("Provider", providers.firstOrNull { it.id == draft.providerId }?.name
                    ?: draft.providerId ?: "Inherit active provider", listOf(null to "Inherit active provider") + providers.map { it.id to it.name }) {
                    draft = draft.copy(providerId = it, model = null)
                }
                val modelChoices = modelPicker.choices(draft.model, draft.providerId == null)
                VoiceChoice("Model", modelChoices.firstOrNull { it.first == draft.model }?.second.orEmpty(), modelChoices) {
                    draft = draft.copy(model = it)
                }
                if (modelPicker.loading) Text("Loading provider models…")
                modelPicker.error?.let { Text(it) }
                if (!modelPicker.loading) {
                    if (modelPicker.error == null && modelPicker.models.isEmpty()) Text("Provider returned no models. You can keep the saved choice or use the provider default.")
                    TextButton(onClick = { viewModel.loadModels(draft.providerId) }) {
                        Text(if (modelPicker.error != null) "Retry models" else "Refresh models")
                    }
                }
                VoiceChoice("Reasoning", draft.reasoningEffort?.displayName ?: "Inherit",
                    listOf(null to "Inherit") + ReasoningEffort.entries.map { it to it.displayName }) {
                    draft = draft.copy(reasoningEffort = it)
                }
                fun continueLabel(m: Int) = when (m) { 0 -> "Never (always start fresh)"; 60 -> "Within 1 hour"; 1 -> "Within 1 minute"; else -> "Within $m minutes" }
                VoiceChoice("Continue previous assistant chat", continueLabel(draft.continueChatMinutes),
                    com.materialchat.data.local.preferences.CONTINUE_CHAT_CHOICES.map { it to continueLabel(it) }) {
                    draft = draft.copy(continueChatMinutes = it)
                }
                Text("Past assistant chats always stay in your chat list.")
                HorizontalDivider()
                Text("Speech input", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    draft.recognitionLanguageTag.orEmpty(),
                    { draft = draft.copy(recognitionLanguageTag = it) },
                    label = { Text("Recognition language (e.g. en-US)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Blank follows the device language (${Locale.getDefault().toLanguageTag()}). The installed recognizer controls speech timing; automatic language switching is not required.")
                val engineReport = remember(draft.recognitionEngine) {
                    recognitionEngineReport(context, draft.recognitionEngine)
                }
                if (engineReport.availableComponents.isNotEmpty()) {
                    VoiceChoice(
                        "Recognition engine",
                        draft.recognitionEngine?.let(::recognitionEngineLabel) ?: "Automatic (best available)",
                        listOf(null to "Automatic (best available)") +
                            engineReport.availableComponents.map { it to recognitionEngineLabel(it) }
                    ) { draft = draft.copy(recognitionEngine = it) }
                }
                Text(
                    when {
                        engineReport.selectedComponent != null ->
                            "In use: ${recognitionEngineLabel(engineReport.selectedComponent)}"
                        engineReport.usesOnDeviceFallback ->
                            "In use: Android on-device fallback. No usable external speech service was found."
                        else -> "No usable speech recognition engine found. Enable an Android speech recognition service in system settings."
                    }
                )
                HorizontalDivider()
                Text("Speech output", style = MaterialTheme.typography.titleMedium)
                VoiceChoice("Backend", backendLabel(draft.backend), AssistantSpeechBackend.entries.map { it to backendLabel(it) }) {
                    viewModel.stopPreview(); draft = draft.copy(backend = it)
                }
                if (draft.backend == AssistantSpeechBackend.ANDROID) {
                    val engines = remember(draft.ttsEnginePackage) { installedTtsEngines(context) }
                    val engineLabel = engines.firstOrNull { it.packageName == draft.ttsEnginePackage }
                        ?.let { "${it.label}${if (it.isSystemDefault) " (system default)" else ""}" }
                        ?: "System default"
                    VoiceChoice("Speech engine", engineLabel,
                        listOf(null to "System default") + engines.map {
                            it.packageName to "${it.label}${if (it.isSystemDefault) " (system default)" else ""}"
                        }) { packageName ->
                        viewModel.stopPreview()
                        draft = draft.copy(ttsEnginePackage = packageName, androidVoiceId = null)
                    }
                    Text("Neural engines installed on the phone (Piper, Kokoro, Supertonic based apps) sound far better than the stock voice. Install one, then pick it here.")
                    val selectedVoice = voices.firstOrNull { it.id == draft.androidVoiceId }
                    val voiceLabel = when {
                        draft.androidVoiceId == null -> "Engine default"
                        selectedVoice != null -> "${Locale.forLanguageTag(selectedVoice.languageTag).displayName} — ${selectedVoice.id}"
                        voicesLoading -> "${draft.androidVoiceId} (checking availability…)"
                        else -> "${draft.androidVoiceId} (unavailable)"
                    }
                    VoiceChoice("Offline voice", voiceLabel,
                        listOf(null to "Engine default") + voices.map {
                            it.id to "${Locale.forLanguageTag(it.languageTag).displayName} — ${it.id}"
                        }) { id ->
                        viewModel.stopPreview()
                        draft = draft.copy(androidVoiceId = id,
                            languageTag = voices.firstOrNull { it.id == id }?.languageTag ?: draft.languageTag)
                    }
                    if (voicesLoading) Text("Loading installed offline voices…")
                    else if (voices.isEmpty()) Text("No installed offline voices found. Install voice data in Android speech settings, then reopen this panel.")
                    OutlinedTextField(draft.languageTag.orEmpty(), { viewModel.stopPreview(); draft = draft.copy(languageTag = it) },
                        label = { Text("Language tag (e.g. en-US)") }, modifier = Modifier.fillMaxWidth())
                    Text("Speech rate: ${"%.2f".format(draft.speechRate)}×")
                    Slider(value = draft.speechRate, onValueChange = { viewModel.stopPreview(); draft = draft.copy(speechRate = it) }, valueRange = 0.25f..2f)
                    Button(enabled = !voicesLoading && !previewing, onClick = { viewModel.preview(draft) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Preview draft voice") }
                    if (previewing) TextButton(onClick = viewModel::stopPreview) { Text("Stop preview") }
                    previewMessage?.let { Text(it) }
                    Text("Blank voice and language use engine defaults. Availability depends on the installed Android speech engine.")
                } else if (draft.backend == AssistantSpeechBackend.SHERPA) {
                    val sherpaStates by viewModel.sherpaStates.collectAsStateWithLifecycle()
                    val model = SherpaTtsModel.fromId(draft.sherpaModel)
                    val state = sherpaStates[model] ?: SherpaModelState.NotDownloaded
                    Text("On-device neural voice (sherpa-onnx). The model is downloaded once into app storage and speech never leaves the phone. Until a model is installed the assistant uses the Android voice.")
                    VoiceChoice("Neural model", "${model.label} (${formatModelBytes(model.downloadBytes)})",
                        SherpaTtsModel.entries.map { it.id to "${it.label} — ${formatModelBytes(it.downloadBytes)} download" }) {
                        viewModel.stopPreview(); draft = draft.copy(sherpaModel = it)
                    }
                    Text(model.description)
                    when (state) {
                        SherpaModelState.NotDownloaded -> {
                            Button(onClick = { viewModel.downloadSherpa(model) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text("Download ${model.label} (${formatModelBytes(model.downloadBytes)})")
                            }
                            Text("Not downloaded. The assistant uses the Android voice until this model is installed.")
                        }
                        is SherpaModelState.Downloading -> {
                            if (state.stage == SherpaModelState.Downloading.Stage.DOWNLOADING) {
                                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                                Text("Downloading… ${formatModelBytes(state.downloadedBytes)} of ${formatModelBytes(state.totalBytes)}")
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(if (state.stage == SherpaModelState.Downloading.Stage.VERIFYING) "Verifying download…" else "Installing…")
                            }
                            TextButton(onClick = { viewModel.cancelSherpa(model) }) { Text("Cancel download") }
                        }
                        SherpaModelState.Installed -> {
                            Text("Installed and verified (${formatModelBytes(model.downloadBytes)} in app storage).")
                            TextButton(onClick = { viewModel.deleteSherpa(model) }) { Text("Delete model") }
                        }
                        is SherpaModelState.Failed -> {
                            Text(state.message)
                            Button(onClick = { viewModel.downloadSherpa(model) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Retry download") }
                        }
                    }
                    OutlinedTextField(draft.languageTag.orEmpty(), { viewModel.stopPreview(); draft = draft.copy(languageTag = it) },
                        label = { Text("Language tag (e.g. en-US)") }, modifier = Modifier.fillMaxWidth())
                    Text("Supertonic speaks English, Korean, Spanish, Portuguese and French; other languages use English. Kokoro is English only.")
                    Text("Speech rate: ${"%.2f".format(draft.speechRate)}×")
                    Slider(value = draft.speechRate, onValueChange = { viewModel.stopPreview(); draft = draft.copy(speechRate = it) }, valueRange = 0.25f..2f)
                    Button(enabled = state.isInstalled && !previewing, onClick = { viewModel.preview(draft) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Preview neural voice") }
                    if (previewing) TextButton(onClick = viewModel::stopPreview) { Text("Stop preview") }
                    previewMessage?.let { Text(it) }
                } else {
                    Text("Gemini sends response text to Google. These are backend-compatible choices, not a live availability check. Account access, availability and billing depend on Google.")
                    VoiceChoice("Gemini TTS model", draft.geminiModel,
                        VoiceGeminiChoices.choices(draft.geminiModel, VoiceGeminiChoices.models)) {
                        draft = draft.copy(geminiModel = it)
                    }
                    VoiceChoice("Gemini voice", draft.geminiVoice,
                        VoiceGeminiChoices.choices(draft.geminiVoice, VoiceGeminiChoices.voices)) {
                        draft = draft.copy(geminiVoice = it)
                    }
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

private fun backendLabel(backend: AssistantSpeechBackend): String = when (backend) {
    AssistantSpeechBackend.ANDROID -> "Android (system engine)"
    AssistantSpeechBackend.GEMINI -> "Gemini (cloud)"
    AssistantSpeechBackend.SHERPA -> "Neural on-device (sherpa-onnx)"
}

@Composable
private fun <T> VoiceChoice(label: String, value: String, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
