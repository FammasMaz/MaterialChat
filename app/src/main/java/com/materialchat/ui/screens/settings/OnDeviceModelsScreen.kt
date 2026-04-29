package com.materialchat.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.LocalModelAvailability
import com.materialchat.domain.model.LocalModelBackend
import com.materialchat.domain.model.LocalModelState
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
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
                title = { Text("On-device models") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh model status"
                        )
                    }
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
                    HuggingFaceTokenCard(
                        tokenPreview = uiState.huggingFaceTokenPreview,
                        onSave = viewModel::saveHuggingFaceToken,
                        onClear = viewModel::clearHuggingFaceToken
                    )
                }
                items(uiState.models, key = { it.descriptor.id }) { state ->
                    OnDeviceModelCard(
                        state = state,
                        isBusy = uiState.activeModelId == state.descriptor.id,
                        onDownload = { viewModel.download(state.descriptor.id) },
                        onDelete = { pendingDelete = state }
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
                text = "Download small models in-app for offline chat and native title generation. Gemma may require accepting its Hugging Face license before the download succeeds.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun HuggingFaceTokenCard(
    tokenPreview: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit
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
                text = "Optional, but required for gated models like Gemma after you accept the model license on Hugging Face. Stored encrypted on-device.",
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
private fun OnDeviceModelCard(
    state: LocalModelState,
    isBusy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val descriptor = state.descriptor
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
                    imageVector = if (descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO) {
                        Icons.Outlined.SmartToy
                    } else {
                        Icons.Outlined.Memory
                    },
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
            }

            Text(
                text = statusText(state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            state.progress?.takeIf { state.availability == LocalModelAvailability.DOWNLOADING }?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
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
                if (state.isUsable) {
                    ExpressiveButton(
                        onClick = onDelete,
                        text = "Delete",
                        leadingIcon = Icons.Outlined.Delete,
                        style = ExpressiveButtonStyle.Text,
                        enabled = !isBusy
                    )
                } else {
                    ExpressiveButton(
                        onClick = onDownload,
                        text = if (descriptor.backend == LocalModelBackend.AICORE_GEMINI_NANO) "Download / Check" else "Download",
                        style = ExpressiveButtonStyle.FilledTonal,
                        enabled = !isBusy && state.availability != LocalModelAvailability.DOWNLOADING
                    )
                }
            }
        }
    }
}

private fun statusText(state: LocalModelState): String {
    val size = state.totalBytes?.let { " • ${formatBytes(it)}" }.orEmpty()
    return when (state.availability) {
        LocalModelAvailability.NOT_DOWNLOADED -> "Not downloaded$size"
        LocalModelAvailability.DOWNLOADABLE -> "Downloadable$size"
        LocalModelAvailability.DOWNLOADING -> "Downloading ${formatBytes(state.downloadedBytes)}${state.totalBytes?.let { " / ${formatBytes(it)}" }.orEmpty()}"
        LocalModelAvailability.DOWNLOADED -> "Downloaded${state.downloadedBytes.takeIf { it > 0L }?.let { " • ${formatBytes(it)}" }.orEmpty()}"
        LocalModelAvailability.AVAILABLE -> "Available on this device"
        LocalModelAvailability.UNAVAILABLE -> "Unavailable on this device"
        LocalModelAvailability.ERROR -> "Status unavailable"
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
