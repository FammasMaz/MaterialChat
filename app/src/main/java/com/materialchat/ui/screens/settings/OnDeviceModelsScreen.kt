package com.materialchat.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.LocalModelAvailability
import com.materialchat.domain.model.LocalModelBackend
import com.materialchat.domain.model.LocalModelState
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.theme.CustomShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDeviceModelsScreen(
    onNavigateBack: () -> Unit,
    viewModel: OnDeviceModelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var pendingDelete by remember { mutableStateOf<LocalModelState?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnDeviceModelsEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    ExpressiveTopBarTitle(
                        title = "On-device models",
                        subtitle = "Local AI downloads"
                    )
                },
                navigationIcon = {
                    ExpressiveFilledIconButton(
                        onClick = onNavigateBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                },
                actions = {
                    ExpressiveFilledIconButton(
                        onClick = viewModel::refresh,
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "Refresh model status"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = CustomShapes.Snackbar,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = CustomShapes.BottomSheet
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OnDeviceModelsHero()
                }
                item {
                    RuntimeMemoryStatusCard(models = uiState.models)
                }
                item {
                    ModelDownloadGuideCard(
                        onOpenLink = { url -> uriHandler.openUri(url) }
                    )
                }
                item {
                    HuggingFaceTokenCard(
                        tokenPreview = uiState.huggingFaceTokenPreview,
                        onSave = viewModel::saveHuggingFaceToken,
                        onClear = viewModel::clearHuggingFaceToken,
                        onOpenLink = { url -> uriHandler.openUri(url) }
                    )
                }
                items(uiState.models, key = { it.descriptor.id }) { state ->
                    OnDeviceModelCard(
                        state = state,
                        isBusy = uiState.activeModelId == state.descriptor.id,
                        onDownload = { viewModel.download(state.descriptor.id) },
                        onDelete = { pendingDelete = state },
                        onUnmount = { viewModel.unmount(state.descriptor.id) }
                    )
                }
            }
        }
    }

    pendingDelete?.let { state ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = CustomShapes.Dialog,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete model") },
            text = {
                Text(
                    if (state.descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO) {
                        "Gemini Nano is managed by Android AICore, not by MaterialChat. The app can refresh or download it when Android allows, but deleting the system-managed model must be done through Android/AICore storage settings."
                    } else {
                        "Remove ${state.descriptor.displayName} from this device? You can download it again later."
                    }
                )
            },
            confirmButton = {
                ExpressiveButton(
                    onClick = {
                        if (state.descriptor.backend != LocalModelBackend.AICORE_GEMINI_NANO) {
                            viewModel.delete(state.descriptor.id)
                        }
                        pendingDelete = null
                    },
                    text = if (state.descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO) "Got it" else "Delete",
                    style = ExpressiveButtonStyle.Text
                )
            },
            dismissButton = {
                ExpressiveButton(
                    onClick = { pendingDelete = null },
                    text = "Cancel",
                    style = ExpressiveButtonStyle.Text
                )
            }
        )
    }
}

@Composable
private fun OnDeviceModelsHero() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = CustomShapes.ProviderCard
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "Private local intelligence",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Download small models in-app for offline chat and native title generation. Start with Qwen for the easiest setup; Gemma needs Hugging Face license access.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ModelDownloadGuideCard(onOpenLink: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = CustomShapes.ProviderCard
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "How to enable downloads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "1. Easiest: tap Download on Qwen2.5 or Qwen3. They are Apache-licensed and should not need an account.\n" +
                    "2. For Gemma: open the Gemma page, sign in to Hugging Face, accept the license, create a read token, paste it below, then download.\n" +
                    "3. Gemini Nano is different: Android AICore decides device support. Pixel 8 Pro may be unsupported for third-party Prompt API access even if Google apps use Gemini Nano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                ExpressiveButton(
                    onClick = { onOpenLink(GEMMA_MODEL_URL) },
                    text = "Gemma page",
                    leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    style = ExpressiveButtonStyle.Text
                )
                ExpressiveButton(
                    onClick = { onOpenLink(HUGGING_FACE_TOKEN_URL) },
                    text = "Create token",
                    leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    style = ExpressiveButtonStyle.FilledTonal
                )
            }
            ExpressiveButton(
                onClick = { onOpenLink(GEMINI_NANO_SUPPORTED_DEVICES_URL) },
                modifier = Modifier.fillMaxWidth(),
                text = "Check Gemini Nano supported devices",
                leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                style = ExpressiveButtonStyle.Outlined
            )
        }
    }
}

@Composable
private fun HuggingFaceTokenCard(
    tokenPreview: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = CustomShapes.ProviderCard
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Hugging Face access token",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Required only for gated models like Gemma. Use a Hugging Face read token after accepting the model license. Stored encrypted on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!tokenPreview.isNullOrBlank()) {
                Text(
                    text = "Saved token: $tokenPreview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                ExpressiveButton(
                    onClick = { onOpenLink(GEMMA_MODEL_URL) },
                    text = "Accept Gemma license",
                    leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    style = ExpressiveButtonStyle.Text
                )
                ExpressiveButton(
                    onClick = { onOpenLink(HUGGING_FACE_TOKEN_URL) },
                    text = "New token",
                    leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    style = ExpressiveButtonStyle.Text
                )
            }
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HF token") },
                singleLine = true,
                shape = CustomShapes.SearchBar
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                ExpressiveButton(
                    onClick = {
                        onClear()
                        token = ""
                    },
                    text = "Clear",
                    style = ExpressiveButtonStyle.Text,
                    enabled = !tokenPreview.isNullOrBlank()
                )
                ExpressiveButton(
                    onClick = {
                        onSave(token)
                        token = ""
                    },
                    text = "Save token",
                    style = ExpressiveButtonStyle.FilledTonal,
                    enabled = token.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun RuntimeMemoryStatusCard(models: List<LocalModelState>) {
    val mounted = models.firstOrNull { it.isMountedInRam }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CustomShapes.ProviderCard,
        colors = CardDefaults.cardColors(
            containerColor = if (mounted != null) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (mounted != null) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Runtime memory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = mounted?.let { "Mounted in RAM: ${it.descriptor.displayName}" }
                    ?: "No local model is currently mounted in RAM",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (mounted != null) {
                    "Use the Unmount button on that model card if local generation gets stuck."
                } else {
                    "A model appears here after the first successful local chat/title generation load."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mounted != null) {
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun OnDeviceModelCard(
    state: LocalModelState,
    isBusy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onUnmount: () -> Unit
) {
    val descriptor = state.descriptor
    val isAicore = descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO
    val aicoreUnsupported = isAicore &&
        (state.availability == LocalModelAvailability.UNAVAILABLE || state.availability == LocalModelAvailability.ERROR)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CustomShapes.ProviderCard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isAicore) Icons.Outlined.SmartToy else Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = descriptor.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = descriptor.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isAicore && state.isUsable) {
                    Text(
                        text = if (state.isMountedInRam) "In RAM" else "Not in RAM",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isMountedInRam) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (state.isMountedInRam) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = statusText(state),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isMountedInRam) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )

            if (state.availability == LocalModelAvailability.DOWNLOADING) {
                val progress = state.progress
                if (progress != null) {
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else {
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                when {
                    state.isUsable && isAicore -> {
                        ExpressiveButton(
                            onClick = {},
                            text = "Available",
                            style = ExpressiveButtonStyle.Outlined,
                            enabled = false
                        )
                    }
                    state.isUsable -> {
                        if (state.isMountedInRam) {
                            ExpressiveButton(
                                onClick = onUnmount,
                                text = "Unmount",
                                style = ExpressiveButtonStyle.FilledTonal,
                                enabled = !isBusy
                            )
                        }
                        ExpressiveButton(
                            onClick = onDelete,
                            text = "Delete",
                            leadingIcon = Icons.Outlined.Delete,
                            style = ExpressiveButtonStyle.Text,
                            enabled = !isBusy
                        )
                    }
                    aicoreUnsupported -> {
                        ExpressiveButton(
                            onClick = {},
                            text = "Not supported",
                            style = ExpressiveButtonStyle.Outlined,
                            enabled = false
                        )
                    }
                    else -> {
                        ExpressiveButton(
                            onClick = onDownload,
                            text = if (isAicore) "Download / Check" else "Download",
                            style = ExpressiveButtonStyle.FilledTonal,
                            enabled = !isBusy && state.availability != LocalModelAvailability.DOWNLOADING
                        )
                    }
                }
            }
        }
    }
}

private fun statusText(state: LocalModelState): String {
    val size = state.totalBytes?.let { " • ${formatBytes(it)}" }.orEmpty()
    if (state.isMountedInRam) {
        return "Mounted in memory · Unmount if it gets stuck or you need RAM"
    }
    return when (state.availability) {
        LocalModelAvailability.NOT_DOWNLOADED -> "Not downloaded$size"
        LocalModelAvailability.DOWNLOADABLE -> "Downloadable$size"
        LocalModelAvailability.DOWNLOADING -> "Downloading ${formatBytes(state.downloadedBytes)}${state.totalBytes?.let { " / ${formatBytes(it)}" }.orEmpty()}"
        LocalModelAvailability.DOWNLOADED -> "Downloaded · not currently in memory${state.downloadedBytes.takeIf { it > 0L }?.let { " • ${formatBytes(it)}" }.orEmpty()}"
        LocalModelAvailability.AVAILABLE -> "Available on this device"
        LocalModelAvailability.UNAVAILABLE -> if (state.descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO) {
            "Not supported on this device"
        } else {
            "Unavailable on this device"
        }
        LocalModelAvailability.ERROR -> if (state.descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO) {
            "AICore status check failed"
        } else {
            "Download failed"
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "%.0f MB".format(mb)
    }
}

private const val GEMMA_MODEL_URL = "https://huggingface.co/litert-community/Gemma3-1B-IT"
private const val HUGGING_FACE_TOKEN_URL = "https://huggingface.co/settings/tokens"
private const val GEMINI_NANO_SUPPORTED_DEVICES_URL = "https://developers.google.com/ml-kit/genai#prompt-device"
