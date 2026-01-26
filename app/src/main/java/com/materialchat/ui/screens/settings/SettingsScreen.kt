package com.materialchat.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsSystemDaydream
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.AppUpdate
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.UpdateState
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.settings.components.AddProviderSheet
import com.materialchat.ui.screens.settings.components.ProviderCard
import com.materialchat.ui.screens.settings.components.SystemPromptField
import com.materialchat.ui.theme.CustomShapes

/**
 * Settings screen for managing providers, theme, and app preferences.
 *
 * Features:
 * - Provider list with add/edit/delete
 * - System prompt configuration
 * - Theme selection (System/Light/Dark)
 * - Dynamic color toggle
 *
 * @param onNavigateBack Callback to navigate back
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.NavigateBack -> {
                    onNavigateBack()
                }
                is SettingsEvent.ShowSnackbar -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ProviderSaved -> {
                    val action = if (event.isNew) "added" else "updated"
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "${event.providerName} $action",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ProviderDeleted -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "${event.providerName} deleted",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ProviderActivated -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "${event.providerName} is now active",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.SettingsSaved -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Settings saved",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ConnectionTestResult -> {
                    val message = if (event.success) {
                        "Connected to ${event.providerName}"
                    } else {
                        "Connection failed: ${event.errorMessage ?: "Unknown error"}"
                    }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    shape = CustomShapes.Snackbar,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { paddingValues ->
        SettingsContent(
            uiState = uiState,
            paddingValues = paddingValues,
            onAddProvider = { viewModel.showAddProviderSheet() },
            onEditProvider = { viewModel.editProvider(it) },
            onDeleteProvider = { viewModel.showDeleteConfirmation(it) },
            onSetActiveProvider = { viewModel.setActiveProvider(it) },
            onTestConnection = { viewModel.testConnection(it) },
            onSystemPromptChange = { viewModel.updateSystemPrompt(it) },
            onThemeModeChange = { viewModel.updateThemeMode(it) },
            onDynamicColorChange = { viewModel.updateDynamicColorEnabled(it) },
            onHapticsChange = { viewModel.updateHapticsEnabled(it) },
            onAiGeneratedTitlesChange = { viewModel.updateAiGeneratedTitlesEnabled(it) },
            onTitleGenerationModelChange = { viewModel.updateTitleGenerationModel(it) },
            onAutoCheckUpdatesChange = { viewModel.updateAutoCheckUpdates(it) },
            onCheckForUpdates = { viewModel.checkForUpdates() },
            onDownloadUpdate = { viewModel.downloadUpdate(it) },
            onInstallUpdate = { viewModel.installUpdate() },
            onCancelDownload = { viewModel.cancelDownload() },
            onSkipVersion = { viewModel.skipVersion() },
            onRetry = { viewModel.retry() }
        )
    }

    // Add Provider Bottom Sheet
    val successState = uiState as? SettingsUiState.Success
    if (successState != null) {
        AddProviderSheet(
            isVisible = successState.showAddProviderSheet,
            isEditing = successState.editingProvider != null,
            editingProvider = successState.editingProvider,
            formState = formState,
            isSaving = successState.isSaving,
            onDismiss = { viewModel.hideProviderSheet() },
            onFieldChange = { name, type, baseUrl, defaultModel, apiKey ->
                viewModel.updateFormField(
                    name = name,
                    type = type,
                    baseUrl = baseUrl,
                    defaultModel = defaultModel,
                    apiKey = apiKey
                )
            },
            onFetchModels = { viewModel.fetchProviderModels() },
            onSave = { viewModel.saveProvider() }
        )

        // Delete Confirmation Dialog
        successState.showDeleteConfirmation?.let { provider ->
            DeleteProviderDialog(
                providerName = provider.name,
                onConfirm = { viewModel.deleteProvider(provider) },
                onDismiss = { viewModel.hideDeleteConfirmation() }
            )
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    paddingValues: PaddingValues,
    onAddProvider: () -> Unit,
    onEditProvider: (Provider) -> Unit,
    onDeleteProvider: (Provider) -> Unit,
    onSetActiveProvider: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onThemeModeChange: (AppPreferences.ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onAiGeneratedTitlesChange: (Boolean) -> Unit,
    onTitleGenerationModelChange: (String) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        when (uiState) {
            is SettingsUiState.Loading -> {
                LoadingContent()
            }
            is SettingsUiState.Success -> {
                SuccessContent(
                    uiState = uiState,
                    onAddProvider = onAddProvider,
                    onEditProvider = onEditProvider,
                    onDeleteProvider = onDeleteProvider,
                    onSetActiveProvider = onSetActiveProvider,
                    onTestConnection = onTestConnection,
                    onSystemPromptChange = onSystemPromptChange,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onHapticsChange = onHapticsChange,
                    onAiGeneratedTitlesChange = onAiGeneratedTitlesChange,
                    onTitleGenerationModelChange = onTitleGenerationModelChange,
                    onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallUpdate = onInstallUpdate,
                    onCancelDownload = onCancelDownload,
                    onSkipVersion = onSkipVersion
                )
            }
            is SettingsUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SuccessContent(
    uiState: SettingsUiState.Success,
    onAddProvider: () -> Unit,
    onEditProvider: (Provider) -> Unit,
    onDeleteProvider: (Provider) -> Unit,
    onSetActiveProvider: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onThemeModeChange: (AppPreferences.ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onAiGeneratedTitlesChange: (Boolean) -> Unit,
    onTitleGenerationModelChange: (String) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Providers Section
        item {
            SectionHeader(title = "AI Providers")
        }

        items(
            items = uiState.providers,
            key = { it.provider.id }
        ) { providerItem ->
            ProviderCard(
                providerItem = providerItem,
                onActivate = { onSetActiveProvider(providerItem.provider.id) },
                onEdit = { onEditProvider(providerItem.provider) },
                onDelete = { onDeleteProvider(providerItem.provider) },
                onTestConnection = { onTestConnection(providerItem.provider.id) }
            )
        }

        item {
            AddProviderButton(onClick = onAddProvider)
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Appearance Section
        item {
            SectionHeader(title = "Appearance")
        }

        item {
            ThemeModeSelector(
                selectedMode = uiState.themeMode,
                onModeSelected = onThemeModeChange
            )
        }

        // Dynamic Color Toggle (Android 12+ only)
        if (uiState.isDynamicColorSupported) {
            item {
                DynamicColorToggle(
                    enabled = uiState.dynamicColorEnabled,
                    onToggle = onDynamicColorChange
                )
            }
        }

        // Haptics Toggle
        item {
            HapticsToggle(
                enabled = uiState.hapticsEnabled,
                onToggle = onHapticsChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Chat Section
        item {
            SectionHeader(title = "Chat")
        }

        item {
            AiGeneratedTitlesToggle(
                enabled = uiState.aiGeneratedTitlesEnabled,
                onToggle = onAiGeneratedTitlesChange
            )
        }

        // Show title generation model field only when AI titles are enabled
        if (uiState.aiGeneratedTitlesEnabled) {
            item {
                TitleGenerationModelField(
                    currentModel = uiState.titleGenerationModel,
                    onModelChange = onTitleGenerationModelChange
                )
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // System Prompt Section
        item {
            SectionHeader(title = "System Prompt")
        }

        item {
            SystemPromptField(
                currentPrompt = uiState.systemPrompt,
                onPromptChange = onSystemPromptChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // About & Updates Section
        item {
            SectionHeader(title = "About & Updates")
        }

        item {
            AboutSection(
                appVersion = uiState.appVersion,
                autoCheckUpdates = uiState.autoCheckUpdates,
                updateState = uiState.updateState,
                onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                onCancelDownload = onCancelDownload,
                onSkipVersion = onSkipVersion
            )
        }

        // Bottom padding for navigation bar
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AddProviderButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add Provider")
    }
}

@Composable
private fun ThemeModeSelector(
    selectedMode: AppPreferences.ThemeMode,
    onModeSelected: (AppPreferences.ThemeMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPreferences.ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    AppPreferences.ThemeMode.SYSTEM -> Icons.Outlined.SettingsSystemDaydream
                                    AppPreferences.ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                    AppPreferences.ThemeMode.DARK -> Icons.Outlined.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Dynamic Color",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use colors from your wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun HapticsToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptics = rememberHapticFeedback()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Haptic Feedback",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vibrate on interactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = { newValue ->
                    // Provide haptic feedback when toggling (use the NEW value for enabled check)
                    haptics.perform(HapticPattern.TOGGLE, enabled = newValue)
                    onToggle(newValue)
                }
            )
        }
    }
}

@Composable
private fun AiGeneratedTitlesToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI-Generated Titles",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use AI to create meaningful conversation titles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun TitleGenerationModelField(
    currentModel: String,
    onModelChange: (String) -> Unit
) {
    var text by remember(currentModel) { mutableStateOf(currentModel) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Title Generation Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Leave empty to use the conversation's model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                },
                placeholder = {
                    Text(
                        text = "e.g., llama3.2:1b or gpt-4o-mini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onModelChange(text) },
                    enabled = text != currentModel
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun DeleteProviderDialog(
    providerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Provider",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$providerName\"? This action cannot be undone. Existing conversations will remain but will show \"Provider deleted\".",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
    autoCheckUpdates: Boolean,
    updateState: UpdateState,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Version Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MaterialChat",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Auto-check for updates toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Auto-check for updates",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Check for updates on startup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = autoCheckUpdates,
                    onCheckedChange = onAutoCheckUpdatesChange
                )
            }
        }

        // Check for updates button with status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Check for updates",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = getUpdateStatusText(updateState),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when (updateState) {
                        is UpdateState.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        is UpdateState.Downloading -> {
                            CircularProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            OutlinedButton(
                                onClick = onCheckForUpdates,
                                shape = RoundedCornerShape(12.dp),
                                enabled = updateState !is UpdateState.Installing
                            ) {
                                Text("Check")
                            }
                        }
                    }
                }

                // Show actions based on update state
                when (updateState) {
                    is UpdateState.Available -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onSkipVersion) {
                                Text("Skip")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onDownloadUpdate(updateState.update) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Download v${updateState.update.versionName}")
                            }
                        }
                    }
                    is UpdateState.Downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onCancelDownload) {
                                Text("Cancel")
                            }
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onInstallUpdate,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Install Now")
                            }
                        }
                    }
                    is UpdateState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> { /* No additional UI */ }
                }
            }
        }
    }
}

private fun getUpdateStatusText(state: UpdateState): String {
    return when (state) {
        is UpdateState.Idle -> "Tap to check for updates"
        is UpdateState.Checking -> "Checking..."
        is UpdateState.Available -> "Update available: v${state.update.versionName}"
        is UpdateState.UpToDate -> "You're up to date"
        is UpdateState.Downloading -> "Downloading... ${(state.progress * 100).toInt()}%"
        is UpdateState.ReadyToInstall -> "Ready to install v${state.update.versionName}"
        is UpdateState.Installing -> "Installing..."
        is UpdateState.Error -> "Error checking for updates"
    }
}
