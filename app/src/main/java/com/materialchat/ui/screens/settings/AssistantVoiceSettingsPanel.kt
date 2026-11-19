@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.materialchat.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import com.materialchat.ui.components.ExpressiveSlider
import com.materialchat.ui.components.dialogEntrance
import com.materialchat.ui.theme.ExpressiveShapes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Inline entry from Settings; draft credentials deliberately never use rememberSaveable.
 *
 * The dialog speaks the Settings screen's language: section titles in `titleMedium`, helper
 * lines in `bodySmall` on `onSurfaceVariant` (the supporting-text style of every settings row),
 * sentence case without trailing periods, choice rows shaped like the screen's model picker
 * (label over value, trailing arrow, `surfaceContainerHigh`), and every action drawn with the
 * shared [ExpressiveButton] so it carries the same press morph and haptic as the rest of Settings.
 */
@Composable
fun AssistantVoiceSettingsPanel(viewModel: AssistantVoiceSettingsViewModel = hiltViewModel()) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // The Settings list's outlined expressive button, so this entry matches the screen-context
    // button under it and the other full-width actions on the screen.
    ExpressiveButton(
        onClick = { open = true },
        text = "Voice configuration",
        style = ExpressiveButtonStyle.Outlined,
        modifier = Modifier.fillMaxWidth()
    )
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
        // W4: the one dialog shape and entrance, like the loaded dialog below and every other
        // AlertDialog in Settings (docs/audit-cross-surface-w4.md, section 7).
        AlertDialog(
            onDismissRequest = { open = false },
            modifier = Modifier.dialogEntrance(),
            shape = ExpressiveShapes.Dialog,
            title = { Text("Voice configuration") },
            text = { VoiceHelp("Loading settings\u2026") },
            confirmButton = {}
        )
        return
    }
    var draft by remember(loaded) { mutableStateOf(VoiceGeminiChoices.draftDefaults(loaded)) }
    LaunchedEffect(draft.providerId, providers) { viewModel.loadModels(draft.providerId) }
    var key by remember { mutableStateOf("") }
    var removeKey by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) open = false },
        modifier = Modifier.dialogEntrance(),
        shape = ExpressiveShapes.Dialog,
        title = { Text("Voice configuration") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VoiceSection("Voice conversation")
                VoiceHelp("Inherit uses the active assistant provider. Provider default follows that provider's configured default model. Reasoning can inherit independently")
                VoiceChoice("Provider", providers.firstOrNull { it.id == draft.providerId }?.name
                    ?: draft.providerId ?: "Inherit active provider", listOf(null to "Inherit active provider") + providers.map { it.id to it.name }) {
                    draft = draft.copy(providerId = it, model = null)
                }
                val modelChoices = modelPicker.choices(draft.model, draft.providerId == null)
                VoiceChoice("Model", modelChoices.firstOrNull { it.first == draft.model }?.second.orEmpty(), modelChoices) {
                    draft = draft.copy(model = it)
                }
                if (modelPicker.loading) VoiceHelp("Loading provider models\u2026")
                modelPicker.error?.let { VoiceHelp(it) }
                if (!modelPicker.loading) {
                    if (modelPicker.error == null && modelPicker.models.isEmpty()) VoiceHelp("Provider returned no models. Keep the saved choice or use the provider default")
                    ExpressiveButton(
                        onClick = { viewModel.loadModels(draft.providerId) },
                        text = if (modelPicker.error != null) "Retry models" else "Refresh models",
                        style = ExpressiveButtonStyle.Text
                    )
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
                VoiceHelp("Past assistant chats always stay in your chat list")
                HorizontalDivider()
                VoiceSection("Speech input")
                OutlinedTextField(
                    draft.recognitionLanguageTag.orEmpty(),
                    { draft = draft.copy(recognitionLanguageTag = it) },
                    label = { Text("Recognition language (e.g. en-US)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                VoiceHelp("Blank follows the device language (${Locale.getDefault().toLanguageTag()}). The installed recognizer controls speech timing")
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
                VoiceHelp(
                    when {
                        engineReport.selectedComponent != null ->
                            "In use: ${recognitionEngineLabel(engineReport.selectedComponent)}"
                        engineReport.usesOnDeviceFallback ->
                            "In use: Android on-device fallback. No usable external speech service was found"
                        else -> "No usable speech recognition engine found. Enable an Android speech recognition service in system settings"
                    }
                )
                HorizontalDivider()
                VoiceSection("Speech output")
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
                    VoiceHelp("Neural engines installed on the phone (Piper, Kokoro, Supertonic based apps) sound far better than the stock voice. Install one, then pick it here")
                    val selectedVoice = voices.firstOrNull { it.id == draft.androidVoiceId }
                    val voiceLabel = when {
                        draft.androidVoiceId == null -> "Engine default"
                        selectedVoice != null -> "${Locale.forLanguageTag(selectedVoice.languageTag).displayName} \u2014 ${selectedVoice.id}"
                        voicesLoading -> "${draft.androidVoiceId} (checking availability\u2026)"
                        else -> "${draft.androidVoiceId} (unavailable)"
                    }
                    VoiceChoice("Offline voice", voiceLabel,
                        listOf(null to "Engine default") + voices.map {
                            it.id to "${Locale.forLanguageTag(it.languageTag).displayName} \u2014 ${it.id}"
                        }) { id ->
                        viewModel.stopPreview()
                        draft = draft.copy(androidVoiceId = id,
                            languageTag = voices.firstOrNull { it.id == id }?.languageTag ?: draft.languageTag)
                    }
                    if (voicesLoading) VoiceHelp("Loading installed offline voices\u2026")
                    else if (voices.isEmpty()) VoiceHelp("No installed offline voices found. Install voice data in Android speech settings, then reopen this panel")
                    OutlinedTextField(draft.languageTag.orEmpty(), { viewModel.stopPreview(); draft = draft.copy(languageTag = it) },
                        label = { Text("Language tag (e.g. en-US)") }, modifier = Modifier.fillMaxWidth())
                    VoiceRate(draft.speechRate) { viewModel.stopPreview(); draft = draft.copy(speechRate = it) }
                    ExpressiveButton(
                        onClick = { viewModel.preview(draft) },
                        text = "Preview draft voice",
                        enabled = !voicesLoading && !previewing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (previewing) ExpressiveButton(onClick = viewModel::stopPreview, text = "Stop preview", style = ExpressiveButtonStyle.Text)
                    previewMessage?.let { VoiceHelp(it) }
                    VoiceHelp("Blank voice and language use engine defaults. Availability depends on the installed Android speech engine")
                } else if (draft.backend == AssistantSpeechBackend.SHERPA) {
                    val sherpaStates by viewModel.sherpaStates.collectAsStateWithLifecycle()
                    val model = SherpaTtsModel.fromId(draft.sherpaModel)
                    val state = sherpaStates[model] ?: SherpaModelState.NotDownloaded
                    VoiceHelp("On-device neural voice (sherpa-onnx). The model is downloaded once into app storage and speech never leaves the phone. Until a model is installed the assistant uses the Android voice")
                    VoiceChoice("Neural model", "${model.label} (${formatModelBytes(model.downloadBytes)})",
                        SherpaTtsModel.entries.map { it.id to "${it.label} \u2014 ${formatModelBytes(it.downloadBytes)} download" }) {
                        viewModel.stopPreview(); draft = draft.copy(sherpaModel = it)
                    }
                    VoiceHelp(model.description)
                    when (state) {
                        SherpaModelState.NotDownloaded -> {
                            ExpressiveButton(
                                onClick = { viewModel.downloadSherpa(model) },
                                text = "Download ${model.label} (${formatModelBytes(model.downloadBytes)})",
                                modifier = Modifier.fillMaxWidth()
                            )
                            VoiceHelp("Not downloaded. The assistant uses the Android voice until this model is installed")
                        }
                        is SherpaModelState.Downloading -> {
                            if (state.stage == SherpaModelState.Downloading.Stage.DOWNLOADING) {
                                LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                                VoiceHelp("Downloading\u2026 ${formatModelBytes(state.downloadedBytes)} of ${formatModelBytes(state.totalBytes)}")
                            } else {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                VoiceHelp(if (state.stage == SherpaModelState.Downloading.Stage.VERIFYING) "Verifying download\u2026" else "Installing\u2026")
                            }
                            ExpressiveButton(onClick = { viewModel.cancelSherpa(model) }, text = "Cancel download", style = ExpressiveButtonStyle.Text)
                        }
                        SherpaModelState.Installed -> {
                            VoiceHelp("Installed and verified (${formatModelBytes(model.downloadBytes)} in app storage)")
                            ExpressiveButton(onClick = { viewModel.deleteSherpa(model) }, text = "Delete model", style = ExpressiveButtonStyle.Text)
                        }
                        is SherpaModelState.Failed -> {
                            VoiceHelp(state.message)
                            ExpressiveButton(
                                onClick = { viewModel.downloadSherpa(model) },
                                text = "Retry download",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedTextField(draft.languageTag.orEmpty(), { viewModel.stopPreview(); draft = draft.copy(languageTag = it) },
                        label = { Text("Language tag (e.g. en-US)") }, modifier = Modifier.fillMaxWidth())
                    VoiceHelp("Supertonic speaks English, Korean, Spanish, Portuguese and French; other languages use English. Kokoro is English only")
                    VoiceRate(draft.speechRate) { viewModel.stopPreview(); draft = draft.copy(speechRate = it) }
                    ExpressiveButton(
                        onClick = { viewModel.preview(draft) },
                        text = "Preview neural voice",
                        enabled = state.isInstalled && !previewing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (previewing) ExpressiveButton(onClick = viewModel::stopPreview, text = "Stop preview", style = ExpressiveButtonStyle.Text)
                    previewMessage?.let { VoiceHelp(it) }
                } else {
                    VoiceHelp("Gemini sends response text to Google. These are backend-compatible choices, not a live availability check. Account access, availability and billing depend on Google")
                    VoiceChoice("Gemini TTS model", draft.geminiModel,
                        VoiceGeminiChoices.choices(draft.geminiModel, VoiceGeminiChoices.models)) {
                        draft = draft.copy(geminiModel = it)
                    }
                    VoiceChoice("Gemini voice", draft.geminiVoice,
                        VoiceGeminiChoices.choices(draft.geminiVoice, VoiceGeminiChoices.voices)) {
                        draft = draft.copy(geminiVoice = it)
                    }
                    VoiceHelp(if (configured) "Encrypted key stored. Leave blank to keep it" else "No dedicated Gemini TTS key stored")
                    OutlinedTextField(key, { key = it; removeKey = false },
                        label = { Text("Optional Gemini TTS API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    ExpressiveButton(onClick = { key = ""; removeKey = true }, text = "Remove stored key on save", style = ExpressiveButtonStyle.Text)
                    if (removeKey) VoiceHelp("Stored key will be removed")
                }
                message?.let { VoiceHelp(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                viewModel.save(draft, if (removeKey) "" else key.takeIf { it.isNotBlank() })
                key = ""
                removeKey = false
            }) { Text(if (saving) "Saving\u2026" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { open = false }) { Text("Close") } }
    )
}

private fun backendLabel(backend: AssistantSpeechBackend): String = when (backend) {
    AssistantSpeechBackend.ANDROID -> "Android (system engine)"
    AssistantSpeechBackend.GEMINI -> "Gemini (cloud)"
    AssistantSpeechBackend.SHERPA -> "Neural on-device (sherpa-onnx)"
}

/** A section title inside the dialog: the Settings screen's sub-section style. */
@Composable
private fun VoiceSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** One helper line: the supporting-text style of every settings row (bodySmall on onSurfaceVariant). */
@Composable
private fun VoiceHelp(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The speech-rate control: its value line in the helper style over the shared expressive slider. */
@Composable
private fun VoiceRate(rate: Float, onChange: (Float) -> Unit) {
    VoiceHelp("Speech rate: ${"%.2f".format(rate)}\u00d7")
    ExpressiveSlider(value = rate, onValueChange = onChange, valueRange = 0.25f..2f)
}

/**
 * A choice row shaped like the Settings screen's model picker: the label in the supporting style
 * over the current value in `bodyLarge`, a trailing drop-down arrow, on `surfaceContainerHigh`
 * (the fill of every settings row), opening the same `DropdownMenu`.
 */
@Composable
private fun <T> VoiceChoice(label: String, value: String, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
